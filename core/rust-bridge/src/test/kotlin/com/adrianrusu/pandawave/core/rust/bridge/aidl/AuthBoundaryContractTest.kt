package com.adrianrusu.pandawave.core.rust.bridge.aidl

import kotlin.test.Test
import kotlin.test.assertFalse

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
}
