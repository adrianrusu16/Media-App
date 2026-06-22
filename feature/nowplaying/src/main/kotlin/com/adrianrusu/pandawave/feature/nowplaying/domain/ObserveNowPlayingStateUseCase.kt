package com.adrianrusu.pandawave.feature.nowplaying.domain

class ObserveNowPlayingStateUseCase(private val repository: NowPlayingRepository) {
    operator fun invoke() = repository.state
}
