package com.adrianrusu.pandawave.feature.home.data

import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.home.domain.HomeRepository
import com.adrianrusu.pandawave.feature.home.domain.HomeState
import com.adrianrusu.pandawave.feature.home.domain.HomeTrack
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PandaEngineHomeRepository(private val engineGateway: EngineGateway, private val hydrateExecutor: Executor) :
    HomeRepository {
    @Inject
    constructor(engineGateway: EngineGateway) : this(
        engineGateway,
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "pw-home-hydrate").apply { isDaemon = true }
        }
    )

    private val mutableState = MutableStateFlow(HomeState())
    override val state: StateFlow<HomeState> = mutableState.asStateFlow()
    private val started = AtomicBoolean(false)
    private var subscription: AutoCloseable? = null
    private var loadedIdentity: String? = null
    private val forYouCache = ProjectionCache<HomeTrack>(HOME_SECTION_FOR_YOU)
    private val recommendationsCache = ProjectionCache<HomeTrack>(HOME_SECTION_RECOMMENDATIONS)
    private val discoveryCache = ProjectionCache<HomeTrack>(HOME_SECTION_DISCOVERY)
    private var lastShownSignature: String? = null

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        subscription = engineGateway.observeSnapshots(::project)
        project(engineGateway.snapshot())
    }

    override fun refresh() {
        dispatch(
            EngineCommand(EngineCommand.TYPE_LOAD_FOR_YOU_FEED, EngineCommandPayloads.forYouFeed(pageSize = PAGE_SIZE))
        )
        dispatch(
            EngineCommand(
                EngineCommand.TYPE_LOAD_RECOMMENDATIONS,
                EngineCommandPayloads.recommendations(pageSize = PAGE_SIZE)
            )
        )
        dispatch(
            EngineCommand(
                EngineCommand.TYPE_LOAD_DISCOVERY_FEED,
                EngineCommandPayloads.discoveryFeed(pageSize = PAGE_SIZE)
            )
        )
    }

    override fun close() {
        subscription?.close()
        subscription = null
        loadedIdentity = null
        lastShownSignature = null
        started.set(false)
    }

    private fun dispatch(command: EngineCommand): EngineSnapshot {
        PandaLog.i(PandaLog.Tag.HOME) { "feed.request section=${sectionFor(command.type)}" }
        val snapshot = engineGateway.dispatch(command).snapshot
        project(snapshot, command)
        return snapshot
    }

    private fun project(snapshot: EngineSnapshot, command: EngineCommand? = null) {
        if (snapshot.authState.state != EngineAuthState.AUTHENTICATED) {
            if (loadedIdentity != null || mutableState.value.hasItems()) {
                PandaLog.i(PandaLog.Tag.HOME) {
                    "feed.skip_identity reason=${snapshot.authState.state}"
                }
            }
            loadedIdentity = null
            lastShownSignature = null
            clearCaches()
            mutableState.value = HomeState()
            return
        }
        val identity = snapshot.authState.account?.id?.takeIf {
            snapshot.authState.session?.current == true
        }
        if (identity == null) {
            PandaLog.w(PandaLog.Tag.HOME) {
                "feed.incomplete_auth hasAccount=${snapshot.authState.account != null} " +
                    "sessionCurrent=${snapshot.authState.session?.current}"
            }
            return
        }
        if (loadedIdentity != identity) {
            clearCaches()
            lastShownSignature = null
        }
        val next = HomeState(
            forYou = forYouCache.project(
                count = snapshot.forYouResultsCount.coerceAtLeast(0),
                force = command?.type == EngineCommand.TYPE_LOAD_FOR_YOU_FEED,
                pageAt = engineGateway::forYouResultsPage,
                mapper = { it.toHomeTrack() }
            ),
            recommendations = recommendationsCache.project(
                count = snapshot.recommendationsResultsCount.coerceAtLeast(0),
                force = command?.type == EngineCommand.TYPE_LOAD_RECOMMENDATIONS,
                pageAt = engineGateway::recommendationResultsPage,
                mapper = { it.toHomeTrack() }
            ),
            discovery = discoveryCache.project(
                count = snapshot.discoveryResultsCount.coerceAtLeast(0),
                force = command?.type == EngineCommand.TYPE_LOAD_DISCOVERY_FEED,
                pageAt = engineGateway::discoveryResultsPage,
                mapper = { it.toHomeTrack() }
            ),
            isLoading = snapshot.isBusy
        )
        mutableState.value = next
        logShown(next, snapshot, command)
        if (loadedIdentity != identity) {
            loadedIdentity = identity
            hydrateExecutor.execute {
                if (!started.get()) return@execute
                PandaLog.i(PandaLog.Tag.HOME) { "hydrate start identityChanged=true" }
                val startedAt = System.currentTimeMillis()
                refresh()
                PandaLog.i(PandaLog.Tag.HOME) {
                    "home.hydrate elapsedMs=${System.currentTimeMillis() - startedAt}"
                }
            }
        }
    }

    private fun logShown(state: HomeState, snapshot: EngineSnapshot, command: EngineCommand?) {
        val signature = listOf(
            state.forYou.size,
            state.recommendations.size,
            state.discovery.size,
            snapshot.forYouResultsCount,
            snapshot.recommendationsResultsCount,
            snapshot.discoveryResultsCount,
            snapshot.isBusy,
            command?.type.orEmpty()
        ).joinToString("|")
        if (signature == lastShownSignature) return
        lastShownSignature = signature
        PandaLog.i(PandaLog.Tag.HOME) {
            "feed.shown forYou=${state.forYou.size}/${snapshot.forYouResultsCount} " +
                "titles=${PandaLog.titles(state.forYou.map(HomeTrack::title))} " +
                "recommendations=${state.recommendations.size}/${snapshot.recommendationsResultsCount} " +
                "titles=${PandaLog.titles(state.recommendations.map(HomeTrack::title))} " +
                "discovery=${state.discovery.size}/${snapshot.discoveryResultsCount} " +
                "titles=${PandaLog.titles(state.discovery.map(HomeTrack::title))} " +
                "busy=${snapshot.isBusy} command=${command?.type.orEmpty()}"
        }
    }

    private fun EngineCatalogItem.toHomeTrack() = HomeTrack(mediaId, title, artist.orEmpty(), album)

    private fun clearCaches() {
        forYouCache.clear()
        recommendationsCache.clear()
        discoveryCache.clear()
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val HOME_SECTION_FOR_YOU = "for_you"
        const val HOME_SECTION_RECOMMENDATIONS = "recommendations"
        const val HOME_SECTION_DISCOVERY = "discovery"

        fun sectionFor(commandType: String): String = when (commandType) {
            EngineCommand.TYPE_LOAD_FOR_YOU_FEED -> HOME_SECTION_FOR_YOU
            EngineCommand.TYPE_LOAD_RECOMMENDATIONS -> HOME_SECTION_RECOMMENDATIONS
            EngineCommand.TYPE_LOAD_DISCOVERY_FEED -> HOME_SECTION_DISCOVERY
            else -> commandType
        }
    }
}

private fun HomeState.hasItems(): Boolean =
    forYou.isNotEmpty() || recommendations.isNotEmpty() || discovery.isNotEmpty()

private class ProjectionCache<T>(private val section: String) {
    private var count: Int = -1
    private var items: List<T> = emptyList()

    fun project(
        count: Int,
        force: Boolean,
        pageAt: (Int, Int) -> List<EngineCatalogItem>,
        mapper: (EngineCatalogItem) -> T
    ): List<T> {
        if (!force && this.count == count) return items
        if (!force && count == 0 && items.isNotEmpty()) {
            PandaLog.d(PandaLog.Tag.HOME) {
                "feed.keep_previous section=$section previous=${items.size} snapshotCount=0"
            }
            return items
        }
        this.count = count
        val page = buildList {
            var offset = 0
            while (offset < count) {
                val limit = minOf(MAX_PROJECTION_PAGE_SIZE, count - offset)
                addAll(pageAt(offset, limit))
                offset += limit
            }
        }
        PandaLog.i(PandaLog.Tag.HOME) {
            "feed.page_read section=$section offset=0 limit=$count count=${page.size} " +
                "titles=${PandaLog.titles(page.map(EngineCatalogItem::title))} force=$force"
        }
        items = page.map(mapper)
        return items
    }

    fun clear() {
        count = -1
        items = emptyList()
    }
}

private const val MAX_PROJECTION_PAGE_SIZE = 50
