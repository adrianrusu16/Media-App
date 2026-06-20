package com.adrianrusu.mediaapp

import android.app.Application
import com.adrianrusu.mediaapp.core.preferences.ThemePreferenceCoordinator
import com.adrianrusu.mediaapp.core.rust.bridge.engine.PandaWaveAudioCacheStore
import com.adrianrusu.mediaapp.core.rust.bridge.engine.PandaWaveAudioContentStoreRegistry
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class PandaWaveApplication : Application() {
    @Inject
    lateinit var themePreferenceCoordinator: ThemePreferenceCoordinator

    override fun onCreate() {
        super.onCreate()
        themePreferenceCoordinator.start()
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
