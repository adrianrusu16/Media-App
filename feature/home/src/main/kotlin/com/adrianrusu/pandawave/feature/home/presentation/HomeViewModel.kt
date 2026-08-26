package com.adrianrusu.pandawave.feature.home.presentation

import androidx.lifecycle.ViewModel
import com.adrianrusu.pandawave.core.common.log.PandaLog
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

    fun play(mediaId: String, section: String, title: String) {
        PandaLog.v(PandaLog.Tag.HOME) {
            "click action=play section=$section trackId=$mediaId title=${PandaLog.field(title)}"
        }
        PandaLog.i(PandaLog.Tag.HOME) {
            "play_requested section=$section trackId=$mediaId title=${PandaLog.field(title)}"
        }
        playbackRepository.dispatch(BambooPlaybackIntent.PlayMedia(mediaId))
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
