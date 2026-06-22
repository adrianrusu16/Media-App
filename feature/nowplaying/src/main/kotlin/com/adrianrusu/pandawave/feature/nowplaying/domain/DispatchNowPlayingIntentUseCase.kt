package com.adrianrusu.pandawave.feature.nowplaying.domain

class DispatchNowPlayingIntentUseCase(private val repository: NowPlayingRepository) {
    operator fun invoke(intent: NowPlayingIntent) {
        repository.dispatch(intent)
    }
}
