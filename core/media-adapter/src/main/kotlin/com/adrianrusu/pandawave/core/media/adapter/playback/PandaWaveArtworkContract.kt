package com.adrianrusu.pandawave.core.media.adapter.playback

import android.net.Uri
import androidx.core.net.toUri
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Rewrites Canopy HTTP artwork into exported `content://` URIs so AAOS media
 * hosts can open album art. In-app Coil keeps using the original HTTP URL.
 *
 * String helpers are unit-testable on the JVM; Android [Uri] is only created
 * at the Media3 boundary.
 */
internal object PandaWaveArtworkContract {
    const val PATH_REMOTE = "remote"
    const val QUERY_SOURCE = "source"

    fun authority(packageName: String): String = "$packageName.artwork"

    fun mediaHostUriString(packageName: String, artworkUri: String?): String? {
        val raw = artworkUri?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val parsed = raw.toJavaUriOrNull() ?: return raw
        return when (parsed.scheme?.lowercase(Locale.US)) {
            SCHEME_HTTP, SCHEME_HTTPS -> {
                if (!isAllowedRemoteSource(parsed)) {
                    null
                } else {
                    val encodedSource = encodeQueryValue(parsed.toASCIIString())
                    "$SCHEME_CONTENT://${authority(packageName)}/$PATH_REMOTE?$QUERY_SOURCE=$encodedSource"
                }
            }

            SCHEME_CONTENT,
            SCHEME_ANDROID_RESOURCE,
            SCHEME_FILE -> raw

            else -> raw
        }
    }

    fun mediaHostUri(packageName: String, artworkUri: String?): Uri? =
        mediaHostUriString(packageName, artworkUri)?.toArtworkUriOrNull()

    fun remoteSourceString(uri: String): String? {
        val parsed = uri.toJavaUriOrNull() ?: return null
        if (parsed.host.isNullOrBlank()) return null
        val path = parsed.path?.trim('/') ?: return null
        if (path != PATH_REMOTE) return null
        val source = queryParameter(parsed.rawQuery, QUERY_SOURCE)?.toJavaUriOrNull() ?: return null
        return source.takeIf(::isAllowedRemoteSource)?.toASCIIString()
    }

    fun remoteSource(uri: Uri): Uri? = remoteSourceString(uri.toString())?.toArtworkUriOrNull()

    fun isAllowedRemoteSource(uri: URI): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != SCHEME_HTTP && scheme != SCHEME_HTTPS) return false
        if (!uri.userInfo.isNullOrEmpty()) return false
        return !uri.host.isNullOrBlank()
    }
}

internal fun interface ArtworkUriProjector {
    fun project(artworkUri: String?): Uri?
}

internal object PassthroughArtworkUriProjector : ArtworkUriProjector {
    override fun project(artworkUri: String?): Uri? = artworkUri?.toArtworkUriOrNull()
}

internal class MediaHostArtworkUriProjector(
    private val packageName: String,
    private val uriParser: (String) -> Uri? = { value -> value.toArtworkUriOrNull() }
) : ArtworkUriProjector {
    override fun project(artworkUri: String?): Uri? =
        PandaWaveArtworkContract.mediaHostUriString(packageName, artworkUri)?.let(uriParser)
}

internal fun String.toArtworkUriOrNull(): Uri? = try {
    this.toUri()
} catch (_: RuntimeException) {
    null
}

internal fun String.toJavaUriOrNull(): URI? = try {
    URI(this)
} catch (_: URISyntaxException) {
    null
}

private fun queryParameter(rawQuery: String?, name: String): String? {
    if (rawQuery.isNullOrEmpty()) return null
    return rawQuery.split('&').firstNotNullOfOrNull { pair ->
        val separator = pair.indexOf('=')
        if (separator < 0) return@firstNotNullOfOrNull null
        val key = decodeQueryValue(pair.substring(0, separator))
        if (key != name) return@firstNotNullOfOrNull null
        decodeQueryValue(pair.substring(separator + 1))
    }
}

private fun encodeQueryValue(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

private fun decodeQueryValue(value: String): String =
    URLDecoder.decode(value.replace("+", "%20"), StandardCharsets.UTF_8)

private const val SCHEME_HTTP = "http"
private const val SCHEME_HTTPS = "https"
private const val SCHEME_CONTENT = "content"
private const val SCHEME_ANDROID_RESOURCE = "android.resource"
private const val SCHEME_FILE = "file"
