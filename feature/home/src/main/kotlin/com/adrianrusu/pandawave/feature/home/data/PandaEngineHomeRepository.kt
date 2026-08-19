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

    private fun dispatch(command: EngineCommand) = project(engineGateway.dispatch(command).snapshot)

    private fun project(snapshot: EngineSnapshot) {
        val identity = snapshot.authState.account?.id?.takeIf {
            snapshot.authState.state == EngineAuthState.AUTHENTICATED &&
                snapshot.authState.session?.current == true
        }
        if (identity == null) {
            loadedIdentity = null
            mutableState.value = HomeState()
            return
        }
        mutableState.value = HomeState(
            forYou = List(snapshot.forYouResultsCount.coerceAtLeast(0), engineGateway::forYouResult)
                .filterNotNull()
                .map { it.toHomeTrack() },
            recommendations = List(
                snapshot.recommendationsResultsCount.coerceAtLeast(0),
                engineGateway::recommendationResult,
            )
                .filterNotNull()
                .map { it.toHomeTrack() },
            discovery = List(snapshot.discoveryResultsCount.coerceAtLeast(0), engineGateway::discoveryResult)
                .filterNotNull()
                .map { it.toHomeTrack() },
            isLoading = snapshot.isBusy,
        )
        if (loadedIdentity != identity) {
            loadedIdentity = identity
            refresh()
        }
    }

    private fun EngineCatalogItem.toHomeTrack() = HomeTrack(mediaId, title, artist.orEmpty(), album)

    private companion object { const val PAGE_SIZE = 20 }
}
