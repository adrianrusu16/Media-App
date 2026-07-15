package com.adrianrusu.pandawave.core.rust.bridge.aidl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class AuthBoundaryContractTest {
    @Test
    fun `public auth projection contains no credential or wire fields`() {
        val publicTypes = listOf(
            EngineAuthState::class.java,
            EngineAccount::class.java,
            EngineAuthSession::class.java
        )
        val publicSurface = publicTypes.flatMap { type ->
            listOf(type.name) + type.declaredFields.map { field -> field.name }
        }.joinToString(" ").lowercase()

        for (forbidden in listOf("access_token", "refresh_token", "sessionenvelope", "canopy.v1", "tonic")) {
            assertFalse(publicSurface.contains(forbidden), "public Kotlin boundary leaked $forbidden")
        }
    }

    @Test
    fun `invalid authenticated aggregates normalize to login required`() {
        val valid = EngineAuthState(
            state = EngineAuthState.AUTHENTICATED,
            account = EngineAccount("account-1", "driver@example.com", "active", 1),
            session = EngineAuthSession("session-1", "", 2, 3, 4, true)
        )
        val invalidStates = listOf(
            valid.copy(account = valid.account?.copy(id = "")),
            valid.copy(account = valid.account?.copy(primaryEmail = " ")),
            valid.copy(account = valid.account?.copy(status = "")),
            valid.copy(account = valid.account?.copy(createdAtEpochMillis = -1)),
            valid.copy(session = valid.session?.copy(id = "")),
            valid.copy(session = valid.session?.copy(expiresAtEpochMillis = -1)),
            valid.copy(account = null),
            valid.copy(session = null)
        )

        assertEquals(EngineAuthState.AUTHENTICATED, valid.normalized().state)
        invalidStates.forEach { state ->
            assertEquals(EngineAuthState.loginRequired(), state.normalized())
        }
    }
}
