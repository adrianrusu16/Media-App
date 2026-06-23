package com.adrianrusu.pandawave.core.audio.visualizer

import android.media.audiofx.Visualizer
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientVisualizerAvailability.Reason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidFftAudioVisualizer : AmbientAudioVisualizer {
    private val processor = AndroidFftProcessor(targetBands = TARGET_BAND_COUNT)
    private val mutableAmplitudes = MutableStateFlow(FloatArray(0))
    override val amplitudes: StateFlow<FloatArray> = mutableAmplitudes.asStateFlow()

    private val mutableAvailability = MutableStateFlow<AmbientVisualizerAvailability>(
        AmbientVisualizerAvailability.Unavailable(Reason.InvalidSession)
    )
    override val availability: StateFlow<AmbientVisualizerAvailability> = mutableAvailability.asStateFlow()

    private var visualizer: Visualizer? = null
    private var attachedAudioSessionId: Int? = null
    private var startRequested = false
    private var closed = false

    override fun attachToAudioSession(audioSessionId: Int) {
        if (closed) return
        if (audioSessionId <= 0) {
            releaseVisualizer()
            attachedAudioSessionId = null
            mutableAmplitudes.value = FloatArray(0)
            mutableAvailability.value = AmbientVisualizerAvailability.Unavailable(Reason.InvalidSession)
            return
        }
        if (attachedAudioSessionId == audioSessionId && visualizer != null) return

        releaseVisualizer()
        attachedAudioSessionId = audioSessionId

        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = chooseCaptureSize()
                val listenerStatus = setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft != null) {
                                mutableAmplitudes.value = processor.process(fft)
                            }
                        }
                    },
                    (Visualizer.getMaxCaptureRate() / CAPTURE_RATE_DIVISOR).coerceAtLeast(1),
                    false,
                    true
                )
                check(listenerStatus == Visualizer.SUCCESS) {
                    "Visualizer rejected FFT capture listener: $listenerStatus"
                }
                if (startRequested) enabled = true
            }
            mutableAvailability.value = AmbientVisualizerAvailability.Ready
        } catch (error: SecurityException) {
            failAttachment(Reason.PermissionDenied)
        } catch (error: UnsupportedOperationException) {
            failAttachment(Reason.Unsupported)
        } catch (error: IllegalArgumentException) {
            failAttachment(Reason.InvalidSession)
        } catch (error: IllegalStateException) {
            failAttachment(Reason.InitializationFailed)
        } catch (error: RuntimeException) {
            failAttachment(Reason.RuntimeFailed)
        }
    }

    override fun start() {
        if (closed || startRequested) return
        startRequested = true
        setEnabled(enabled = true)
    }

    override fun stop() {
        if (!startRequested) return
        startRequested = false
        setEnabled(enabled = false)
    }

    override fun close() {
        if (closed) return
        closed = true
        startRequested = false
        releaseVisualizer()
        attachedAudioSessionId = null
        mutableAmplitudes.value = FloatArray(0)
        mutableAvailability.value = AmbientVisualizerAvailability.Unavailable(Reason.InvalidSession)
    }

    private fun setEnabled(enabled: Boolean) {
        val activeVisualizer = visualizer ?: return
        try {
            activeVisualizer.enabled = enabled
        } catch (error: RuntimeException) {
            mutableAvailability.value = AmbientVisualizerAvailability.Unavailable(Reason.RuntimeFailed)
            releaseVisualizer()
        }
    }

    private fun failAttachment(reason: Reason) {
        releaseVisualizer()
        attachedAudioSessionId = null
        mutableAvailability.value = AmbientVisualizerAvailability.Unavailable(reason)
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
        } catch (error: RuntimeException) {
            mutableAvailability.value = AmbientVisualizerAvailability.Unavailable(Reason.RuntimeFailed)
        }
        try {
            visualizer?.release()
        } catch (error: RuntimeException) {
            mutableAvailability.value = AmbientVisualizerAvailability.Unavailable(Reason.RuntimeFailed)
        }
        visualizer = null
    }

    private fun chooseCaptureSize(): Int {
        val range = Visualizer.getCaptureSizeRange()
        return PREFERRED_CAPTURE_SIZE.coerceIn(range[0], range[1])
    }

    private companion object {
        const val TARGET_BAND_COUNT = 64
        const val CAPTURE_RATE_DIVISOR = 2
        const val PREFERRED_CAPTURE_SIZE = 1024
    }
}
