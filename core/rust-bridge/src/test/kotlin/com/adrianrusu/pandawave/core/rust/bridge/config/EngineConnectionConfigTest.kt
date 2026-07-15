package com.adrianrusu.pandawave.core.rust.bridge.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EngineConnectionConfigTest {
    @Test
    fun `validation preserves valid JSON verbatim`() {
        val json = "{\"schema_version\":1}"

        assertEquals(json, EngineConnectionConfigLoader.validate(json))
    }

    @Test
    fun `validation rejects a blank asset`() {
        assertFailsWith<IllegalArgumentException> {
            EngineConnectionConfigLoader.validate("  \n")
        }
    }

    @Test
    fun `validation rejects an asset larger than the public config limit`() {
        val error = assertFailsWith<IllegalArgumentException> {
            EngineConnectionConfigLoader.validate("x".repeat(65_537))
        }

        assertTrue(error.message.orEmpty().contains("too large"))
    }
}
