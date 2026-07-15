package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthStateParcelTest {
    @Test
    fun malformedAuthenticatedParcelReadsAsLoginRequired() = withParcel { parcel ->
        parcel.writeAuthenticated(accountId = "", accountCreatedAt = 1)
        parcel.setDataPosition(0)

        assertEquals(EngineAuthState.loginRequired(), parcel.readEngineAuthState())
    }

    @Test
    fun negativeAuthenticatedTimestampReadsAsLoginRequired() = withParcel { parcel ->
        parcel.writeAuthenticated(accountId = "account-1", accountCreatedAt = -1)
        parcel.setDataPosition(0)

        assertEquals(EngineAuthState.loginRequired(), parcel.readEngineAuthState())
    }

    @Test
    fun invalidAuthenticatedObjectWritesOnlyLoginRequired() = withParcel { parcel ->
        parcel.writeEngineAuthState(validAuthenticated().copy(session = null))
        parcel.setDataPosition(0)

        assertEquals(EngineAuthState.LOGIN_REQUIRED, parcel.readString())
        assertEquals(0, parcel.dataAvail())
    }

    @Test
    fun invalidSnapshotAuthStateRoundTripsAsLoginRequired() = withParcel { parcel ->
        EngineSnapshot.idle(1).copy(
            authState = validAuthenticated().copy(
                account = validAuthenticated().account?.copy(primaryEmail = " ")
            )
        ).writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        assertEquals(
            EngineAuthState.loginRequired(),
            EngineSnapshot.CREATOR.createFromParcel(parcel).authState
        )
    }

    private fun Parcel.writeAuthenticated(accountId: String, accountCreatedAt: Long) {
        writeString(EngineAuthState.AUTHENTICATED)
        writeString(accountId)
        writeString("driver@example.com")
        writeString("active")
        writeLong(accountCreatedAt)
        writeString("session-1")
        writeString("")
        writeLong(2)
        writeLong(3)
        writeLong(4)
        writeInt(1)
    }

    private fun validAuthenticated(): EngineAuthState = EngineAuthState(
        state = EngineAuthState.AUTHENTICATED,
        account = EngineAccount("account-1", "driver@example.com", "active", 1),
        session = EngineAuthSession("session-1", "", 2, 3, 4, true)
    )

    private inline fun withParcel(block: (Parcel) -> Unit) {
        val parcel = Parcel.obtain()
        try {
            block(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
