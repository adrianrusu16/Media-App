package com.adrianrusu.pandawave.feature.home.data

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.home.domain.HomeRepository
import com.adrianrusu.pandawave.feature.home.domain.HomeState
import com.adrianrusu.pandawave.feature.home.domain.HomeTrack
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PandaEngineHomeRepository @Inject constructor(
    private val engineGateway: EngineGateway,
) : HomeRepository {
    private val mutableState = MutableStateFlow(HomeState())
    override val state: StateFlow<HomeState> = mutableState.asStateFlow()
    private val started = AtomicBoolean(false)
    private var subscription: AutoCloseable? = null
    private var loadedIdentity: String? = null
    private val forYouCache = ProjectionCache<HomeTrack>()
    private val recommendationsCache = ProjectionCache<HomeTrack>()
    private val discoveryCache = ProjectionCache<HomeTrack>()

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        subscription = engineGateway.observeSnapshots(::project)
        project(engineGateway.snapshot())
    }

    override fun refresh() {
        dispatch(EngineCommand(EngineCommand.TYPE_LOAD_FOR_YOU_FEED, EngineCommandPayloads.forYouFeed(pageSize = PAGE_SIZE)))
        dispatch(EngineCommand(EngineCommand.TYPE_LOAD_RECOMMENDATIONS, EngineCommandPayloads.recommendations(pageSize = PAGE_SIZE)))
        dispatch(EngineCommand(EngineCommand.TYPE_LOAD_DISCOVERY_FEED, EngineCommandPayloads.discoveryFeed(pageSize = PAGE_SIZE)))
    }

    override fun close() {
        subscription?.close()
        subscription = null
        loadedIdentity = null
        started.set(false)
    }

    private fun dispatch(command: EngineCommand) = project(engineGateway.dispatch(command).snapshot, command)

    private fun project(snapshot: EngineSnapshot, command: EngineCommand? = null) {
        val identity = snapshot.authState.account?.id?.takeIf {
            snapshot.authState.state == EngineAuthState.AUTHENTICATED &&
                snapshot.authState.session?.current == true
        }
        if (identity == null) {
            loadedIdentity = null
            clearCaches()
            mutableState.value = HomeState()
            return
        }
        if (loadedIdentity != identity) {
            clearCaches()
        }
        mutableState.value = HomeState(
            forYou = forYouCache.project(
                count = snapshot.forYouResultsCount.coerceAtLeast(0),
                force = command?.type == EngineCommand.TYPE_LOAD_FOR_YOU_FEED,
                itemAt = engineGateway::forYouResult,
                mapper = { it.toHomeTrack() },
            ),
            recommendations = recommendationsCache.project(
                count = snapshot.recommendationsResultsCount.coerceAtLeast(0),
                force = command?.type == EngineCommand.TYPE_LOAD_RECOMMENDATIONS,
                itemAt = engineGateway::recommendationResult,
                mapper = { it.toHomeTrack() },
            ),
            discovery = discoveryCache.project(
                count = snapshot.discoveryResultsCount.coerceAtLeast(0),
                force = command?.type == EngineCommand.TYPE_LOAD_DISCOVERY_FEED,
                itemAt = engineGateway::discoveryResult,
                mapper = { it.toHomeTrack() },
            ),
            isLoading = snapshot.isBusy,
        )
        if (loadedIdentity != identity) {
            loadedIdentity = identity
            refresh()
        }
    }

    private fun EngineCatalogItem.toHomeTrack() = HomeTrack(mediaId, title, artist.orEmpty(), album)

    private fun clearCaches() {
        forYouCache.clear()
        recommendationsCache.clear()
        discoveryCache.clear()
    }

    private companion object { const val PAGE_SIZE = 20 }
}

private class ProjectionCache<T> {
    private var count: Int = -1
    private var items: List<T> = emptyList()

    fun project(
        count: Int,
        force: Boolean,
        itemAt: (Int) -> EngineCatalogItem?,
        mapper: (EngineCatalogItem) -> T,
    ): List<T> {
        if (!force && this.count == count) return items
        this.count = count
        items = List(count, itemAt).filterNotNull().map(mapper)
        return items
    }

    fun clear() {
        count = -1
        items = emptyList()
    }
}
