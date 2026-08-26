package com.adrianrusu.pandawave.core.rust.bridge.gateway

import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidEngineProcessRecoveryTest {
    @Test
    fun `gateway reconnects after the remote engine process is killed`() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val engineProcessName = "${targetContext.packageName}:engine"
        val observedEvents = ConcurrentLinkedQueue<String>()
        val killIssued = AtomicBoolean(false)
        val disconnected = CountDownLatch(1)
        val reconnected = CountDownLatch(1)
        val gateway = AidlEngineGateway(AndroidEngineServiceConnection(targetContext))
        val eventSubscription = gateway.observeEngineEvents { event ->
            observedEvents += event.type
            if (!killIssued.get()) return@observeEngineEvents
            when (event.type) {
                EngineEvent.TYPE_SERVICE_DISCONNECTED -> disconnected.countDown()
                EngineEvent.TYPE_SERVICE_CONNECTED -> reconnected.countDown()
            }
        }

        try {
            awaitCondition("initial engine service connection") { gateway.isAuthAvailable }
            val initialPid = awaitProcessPid(engineProcessName)

            killIssued.set(true)
            Process.killProcess(initialPid)

            assertTrue(
                "Expected service_disconnected after killing $engineProcessName; events=$observedEvents",
                disconnected.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )
            assertTrue(
                "Expected service_connected after $engineProcessName restarted; events=$observedEvents",
                reconnected.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )
            val recoveredPid = awaitProcessPid(engineProcessName, excluding = initialPid)
            assertNotEquals("The engine process must have a new PID after recovery", initialPid, recoveredPid)

            val result = gateway.dispatchPlatformEvent(
                EnginePlatformEvent(
                    type = EnginePlatformEvent.TYPE_APP_FOREGROUNDED,
                    payload = null
                )
            )

            assertEquals(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, result.event.type)
        } finally {
            eventSubscription.close()
            gateway.close()
        }
    }

    private fun awaitProcessPid(processName: String, excluding: Int? = null): Int {
        val deadline = SystemClock.elapsedRealtime() + PROCESS_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            val pid = shell("pidof -s $processName").toIntOrNull()
            if (pid != null && pid != excluding) return pid
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Timed out waiting for process $processName excluding PID $excluding")
    }

    private fun awaitCondition(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + PROCESS_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { reader -> reader.readText().trim() }
    }

    private companion object {
        const val EVENT_TIMEOUT_SECONDS = 15L
        const val PROCESS_TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
