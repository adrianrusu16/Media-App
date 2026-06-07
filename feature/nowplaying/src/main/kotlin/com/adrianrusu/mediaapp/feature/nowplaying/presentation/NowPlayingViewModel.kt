package com.adrianrusu.mediaapp.feature.nowplaying.presentation

import androidx.lifecycle.ViewModel
import com.adrianrusu.mediaapp.feature.nowplaying.domain.DispatchNowPlayingIntentUseCase
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingRepository
import com.adrianrusu.mediaapp.feature.nowplaying.domain.ObserveNowPlayingStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val repository: NowPlayingRepository,
    observeState: ObserveNowPlayingStateUseCase,
    private val dispatchIntent: DispatchNowPlayingIntentUseCase
) : ViewModel() {
    val state = observeState()

    init {
        repository.start()
    }

    fun onIntent(intent: NowPlayingIntent) {
        dispatchIntent(intent)
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
