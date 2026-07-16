package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineAuthOperationResultParcelTest {
    @Test
    fun `authenticated result round trips without credentials`() {
        val result = EngineAuthOperationResult.authenticated()

        val restored = roundTrip(result)

        assertEquals(EngineAuthOperationResult.STATUS_AUTHENTICATED, restored.status)
        assertTrue(restored.isSuccessful)
        assertNull(restored.errorType)
        assertFalse(restored.toString().contains("token", ignoreCase = true))
        assertFalse(restored.toString().contains("password", ignoreCase = true))
    }

    @Test
    fun `typed error preserves only safe classification and retry hint`() {
        val result = EngineAuthOperationResult.error(
            errorType = EngineAuthOperationResult.ERROR_RATE_LIMITED,
            retryAfterMillis = 2_000
        )

        val restored = roundTrip(result)

        assertEquals(EngineAuthOperationResult.STATUS_ERROR, restored.status)
        assertEquals(EngineAuthOperationResult.ERROR_RATE_LIMITED, restored.errorType)
        assertEquals(2_000L, restored.retryAfterMillis)
        assertFalse(restored.isSuccessful)
    }

    private fun roundTrip(value: EngineAuthOperationResult): EngineAuthOperationResult {
        val parcel = Parcel.obtain()
        return try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            EngineAuthOperationResult.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
