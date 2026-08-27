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
 * Prefers `content://…/id/{id}/{hash}` when PandaEngine supplies artwork
 * identity; otherwise wraps the remote URL as `content://…/remote?source=`.
 */
internal object PandaWaveArtworkContract {
    const val PATH_REMOTE = "remote"
    const val PATH_ID = "id"
    const val QUERY_SOURCE = "source"
    const val QUERY_WIDTH = "w"
    const val QUERY_HEIGHT = "h"

    fun authority(packageName: String): String = "$packageName.artwork"

    fun mediaHostUriString(
        packageName: String,
        artworkUri: String?,
        artworkId: String? = null,
        artworkVersion: String? = null
    ): String? {
        val id = artworkId?.trim()?.takeIf(String::isNotEmpty)
        val hash = artworkVersion?.trim()?.takeIf(String::isNotEmpty)
        val remote = artworkUri?.trim()?.takeIf(String::isNotEmpty)
        if (id != null && hash != null) {
            remote?.toJavaUriOrNull()?.takeIf(::isAllowedRemoteSource)?.let { source ->
                PandaArtworkRegistry.register(id, hash, source.toASCIIString())
            }
            return "$SCHEME_CONTENT://${authority(packageName)}/$PATH_ID/${encodePath(id)}/${encodePath(hash)}"
        }
        val raw = remote ?: return null
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

    fun mediaHostUri(
        packageName: String,
        artworkUri: String?,
        artworkId: String? = null,
        artworkVersion: String? = null
    ): Uri? = mediaHostUriString(packageName, artworkUri, artworkId, artworkVersion)?.toArtworkUriOrNull()

    fun remoteSourceString(uri: String): String? {
        val parsed = uri.toJavaUriOrNull() ?: return null
        if (parsed.host.isNullOrBlank()) return null
        val path = parsed.path?.trim('/') ?: return null
        val segments = path.split('/')
        if (segments.size >= 3 && segments[0] == PATH_ID) {
            val id = decodePath(segments[1])
            val hash = decodePath(segments[2])
            return PandaArtworkRegistry.remoteUri(id, hash)
        }
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

internal interface ArtworkUriProjector {
    fun project(artworkUri: String?, artworkId: String? = null, artworkVersion: String? = null): Uri?
}

internal object PassthroughArtworkUriProjector : ArtworkUriProjector {
    override fun project(artworkUri: String?, artworkId: String?, artworkVersion: String?): Uri? =
        artworkUri?.toArtworkUriOrNull()
}

internal class MediaHostArtworkUriProjector(
    private val packageName: String,
    private val uriParser: (String) -> Uri? = { value -> value.toArtworkUriOrNull() }
) : ArtworkUriProjector {
    override fun project(artworkUri: String?, artworkId: String?, artworkVersion: String?): Uri? =
        PandaWaveArtworkContract.mediaHostUriString(
            packageName = packageName,
            artworkUri = artworkUri,
            artworkId = artworkId,
            artworkVersion = artworkVersion
        )?.let(uriParser)
}

internal object PandaArtworkRegistry {
    private val lock = Any()
    private val remotes = object : LinkedHashMap<String, String>(MAX_ENTRIES, 0.75F, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_ENTRIES
    }

    fun register(id: String, hash: String, remoteUri: String) {
        synchronized(lock) {
            remotes[key(id, hash)] = remoteUri
        }
    }

    fun remoteUri(id: String, hash: String): String? = synchronized(lock) {
        remotes[key(id, hash)]
    }

    private fun key(id: String, hash: String): String = "$id/$hash"
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
    return rawQuery.split('&').firstOrNull { pair ->
        val separator = pair.indexOf('=')
        if (separator < 0) return@firstOrNull false
        decodeQueryValue(pair.substring(0, separator)) == name
    }?.let { pair ->
        decodeQueryValue(pair.substring(pair.indexOf('=') + 1))
    }
}

private fun encodeQueryValue(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

private fun decodeQueryValue(value: String): String =
    URLDecoder.decode(value.replace("+", "%20"), StandardCharsets.UTF_8)

private fun encodePath(value: String): String = encodeQueryValue(value)

private fun decodePath(value: String): String = decodeQueryValue(value)

private const val SCHEME_HTTP = "http"
private const val SCHEME_HTTPS = "https"
private const val SCHEME_CONTENT = "content"
private const val SCHEME_ANDROID_RESOURCE = "android.resource"
private const val SCHEME_FILE = "file"
private const val MAX_ENTRIES = 256
