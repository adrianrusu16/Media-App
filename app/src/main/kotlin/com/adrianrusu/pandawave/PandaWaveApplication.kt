package com.adrianrusu.pandawave

import android.app.Application
import android.content.Context
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.preferences.ThemePreferenceCoordinator
import com.adrianrusu.pandawave.core.rust.bridge.engine.PandaWaveAudioCacheStore
import com.adrianrusu.pandawave.core.rust.bridge.engine.PandaWaveAudioContentStoreRegistry
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class PandaWaveApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var themePreferenceCoordinator: ThemePreferenceCoordinator

    override fun onCreate() {
        super.onCreate()
        if (!isDefaultProcess(packageName = packageName, processName = Application.getProcessName())) return

        PandaTrace.section("PW.Startup.Application.onCreate") {
            PandaTrace.section("PW.Startup.ThemeCoordinator.start") {
                themePreferenceCoordinator.start()
            }
            PandaTrace.section("PW.Startup.AudioCache.install") {
                PandaWaveAudioContentStoreRegistry.install(
                    PandaWaveAudioCacheStore(
                        audioCacheDirectory = File(cacheDir, AUDIO_CACHE_DIRECTORY)
                    )
                )
            }
        }
    }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .eventListener(ArtworkCoilEventListener)
        .build()

    private companion object {
        const val AUDIO_CACHE_DIRECTORY = "pandawave/audio"
    }
}

private object ArtworkCoilEventListener : EventListener() {
    override fun onError(request: ImageRequest, result: ErrorResult) {
        PandaLog.w(PandaLog.Tag.ARTWORK, result.throwable) {
            "artwork.coil.loader_error data=${PandaLog.field(request.data.toString())} " +
                "memoryCacheKey=${PandaLog.field(request.memoryCacheKey)} " +
                "diskCacheKey=${PandaLog.field(request.diskCacheKey)} " +
                "message=${PandaLog.field(result.throwable.message)}"
        }
    }

    override fun onCancel(request: ImageRequest) {
        PandaLog.d(PandaLog.Tag.ARTWORK) {
            "artwork.coil.loader_cancel data=${PandaLog.field(request.data.toString())} " +
                "memoryCacheKey=${PandaLog.field(request.memoryCacheKey)}"
        }
    }
}

internal fun isDefaultProcess(packageName: String, processName: String): Boolean = packageName == processName
