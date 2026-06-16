package com.adrianrusu.mediaapp

import android.app.Application
import com.adrianrusu.mediaapp.core.rust.bridge.engine.PandaWaveAudioCacheStore
import com.adrianrusu.mediaapp.core.rust.bridge.engine.PandaWaveAudioContentStoreRegistry
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class PandaWaveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PandaWaveAudioContentStoreRegistry.install(
            PandaWaveAudioCacheStore(
                audioCacheDirectory = File(cacheDir, AUDIO_CACHE_DIRECTORY)
            )
        )
    }

    private companion object {
        const val AUDIO_CACHE_DIRECTORY = "pandawave/audio"
    }
}
