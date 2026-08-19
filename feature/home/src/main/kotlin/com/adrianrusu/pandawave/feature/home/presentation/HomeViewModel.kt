package com.adrianrusu.pandawave.feature.home.presentation

import androidx.lifecycle.ViewModel
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.feature.home.domain.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val playbackRepository: BambooPlaybackRepository,
) : ViewModel() {
    val state = repository.state

    init { repository.start() }

    fun play(mediaId: String) = playbackRepository.dispatch(BambooPlaybackIntent.PlayMedia(mediaId))

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
