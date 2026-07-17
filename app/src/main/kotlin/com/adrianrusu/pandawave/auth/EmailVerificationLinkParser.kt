package com.adrianrusu.pandawave.auth

import java.net.URI

data class EmailVerificationLinkConfig(
    val appLinkHost: String,
    val actionPath: String,
    val tokenParameter: String,
    val debugScheme: String?
) {
    init {
        require(appLinkHost.isNotBlank())
        require(actionPath.matches(SAFE_COMPONENT))
        require(tokenParameter.matches(SAFE_COMPONENT))
        require(debugScheme == null || debugScheme.matches(SAFE_COMPONENT))
    }

    internal val normalizedActionPath: String = actionPath.trim('/')

    private companion object {
        val SAFE_COMPONENT = Regex("[a-zA-Z][a-zA-Z0-9.-]*")
    }
}

sealed interface EmailVerificationLinkResult {
    data class Token(val value: ByteArray) : EmailVerificationLinkResult

    data object Invalid : EmailVerificationLinkResult
}

class EmailVerificationLinkParser(private val config: EmailVerificationLinkConfig) {
    fun parse(link: String?): EmailVerificationLinkResult {
        if (link.isNullOrBlank() || link.length > MAX_LINK_CHARS) return invalid()
        val uri = runCatching { URI(link) }.getOrNull() ?: return invalid()
        if (uri.isOpaque || uri.fragment != null || uri.userInfo != null || uri.port != -1) {
            return invalid()
        }
        if (!isAcceptedRoute(uri)) return invalid()

        val token = tokenFrom(uri.rawQuery) ?: return invalid()
        return EmailVerificationLinkResult.Token(token)
    }

    private fun isAcceptedRoute(uri: URI): Boolean {
        val production = uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(config.appLinkHost, ignoreCase = true) &&
            uri.rawPath == "/${config.normalizedActionPath}"
        val debug = config.debugScheme?.let { scheme ->
            uri.scheme == scheme &&
                uri.host == config.normalizedActionPath &&
                (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
        } ?: false
        return production || debug
    }

    private fun tokenFrom(rawQuery: String?): ByteArray? {
        if (rawQuery.isNullOrEmpty()) return null
        var token: ByteArray? = null
        for (part in rawQuery.split('&')) {
            val separator = part.indexOf('=')
            if (separator <= 0) continue
            val name = decodeAscii(part.substring(0, separator)) ?: return null
            if (name != config.tokenParameter) continue
            if (token != null) return null
            token = percentDecode(part.substring(separator + 1))
                ?.takeIf { value ->
                    value.isNotEmpty() &&
                        value.size <= MAX_TOKEN_BYTES &&
                        value.all { byte -> byte.toInt() and 0xff in PRINTABLE_ASCII }
                }
                ?: return null
        }
        return token
    }

    private fun decodeAscii(value: String): String? {
        val bytes = percentDecode(value) ?: return null
        if (bytes.any { byte -> byte.toInt() and 0xff !in PRINTABLE_ASCII }) return null
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun percentDecode(value: String): ByteArray? {
        val result = ByteArray(value.length)
        var source = 0
        var target = 0
        while (source < value.length) {
            val character = value[source]
            when {
                character == '%' -> {
                    if (source + 2 >= value.length) return null
                    val high = value[source + 1].digitToIntOrNull(16) ?: return null
                    val low = value[source + 2].digitToIntOrNull(16) ?: return null
                    result[target++] = ((high shl 4) or low).toByte()
                    source += 3
                }

                character.code <= 0x7f -> {
                    result[target++] = character.code.toByte()
                    source += 1
                }

                else -> return null
            }
        }
        return result.copyOf(target)
    }

    private fun invalid(): EmailVerificationLinkResult = EmailVerificationLinkResult.Invalid

    private companion object {
        const val MAX_LINK_CHARS = 8_192
        const val MAX_TOKEN_BYTES = 4_096
        val PRINTABLE_ASCII = 0x21..0x7e
    }
}
