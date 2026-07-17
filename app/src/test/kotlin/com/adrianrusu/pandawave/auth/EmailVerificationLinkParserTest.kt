package com.adrianrusu.pandawave.auth

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

class EmailVerificationLinkParserTest {
    private val production = EmailVerificationLinkConfig(
        appLinkHost = "accounts.pandawave.example",
        actionPath = "verify-email",
        tokenParameter = "token",
        debugScheme = null
    )

    @Test
    fun `accepts exact production app link and returns opaque token bytes`() {
        val result = EmailVerificationLinkParser(production).parse(
            "https://accounts.pandawave.example/verify-email?token=opaque%2Dtoken"
        )

        assertContentEquals(
            "opaque-token".encodeToByteArray(),
            assertIs<EmailVerificationLinkResult.Token>(result).value
        )
    }

    @Test
    fun `rejects wrong host path duplicate token fragment and non https production links`() {
        val parser = EmailVerificationLinkParser(production)
        val invalid = listOf(
            "https://evil.example/verify-email?token=opaque",
            "https://accounts.pandawave.example/reset-password?token=opaque",
            "https://accounts.pandawave.example/verify-email?token=one&token=two",
            "https://accounts.pandawave.example/verify-email?token=opaque#copied",
            "http://accounts.pandawave.example/verify-email?token=opaque"
        )

        invalid.forEach { link ->
            assertIs<EmailVerificationLinkResult.Invalid>(parser.parse(link))
        }
    }

    @Test
    fun `debug scheme is accepted only when explicitly configured`() {
        val link = "pandawave-dev://verify-email?token=opaque"

        assertIs<EmailVerificationLinkResult.Invalid>(
            EmailVerificationLinkParser(production).parse(link)
        )
        assertContentEquals(
            "opaque".encodeToByteArray(),
            assertIs<EmailVerificationLinkResult.Token>(
                EmailVerificationLinkParser(
                    production.copy(debugScheme = "pandawave-dev")
                ).parse(link)
            ).value
        )
    }
}
