package com.adrianrusu.pandawave.core.media.adapter.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.adrianrusu.pandawave.core.common.log.PandaLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection

/**
 * Serves Canopy HTTP artwork to AAOS media hosts as seekable `content://` files.
 * Hosts typically cannot fetch `https://` album art from another app, and many
 * car image loaders fail on pipe FDs.
 */
class PandaWaveArtworkContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = if (PandaWaveArtworkContract.remoteSource(uri) == null) {
        null
    } else {
        DEFAULT_MIME_TYPE
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val source = PandaWaveArtworkContract.remoteSource(uri) ?: return null
        val columns = projection?.takeUnless { values -> values.isEmpty() } ?: DEFAULT_QUERY_COLUMNS
        return MatrixCursor(columns).apply {
            addRow(columns.map { column -> queryValue(column, source) }.toTypedArray())
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val source = PandaWaveArtworkContract.remoteSource(uri)
            ?: throw FileNotFoundException("Unsupported PandaWave artwork uri: $uri")
        if (mode != READ_MODE) {
            throw FileNotFoundException("PandaWave artwork uri is read-only: $uri")
        }
        val cacheDir = context?.cacheDir
            ?: throw FileNotFoundException("PandaWave artwork provider is not attached")
        val bytes = try {
            fetchBytes(source)
        } catch (error: Exception) {
            PandaLog.w(PandaLog.Tag.ARTWORK, error) {
                "artwork.provider.fetch_failed uri=${PandaLog.field(source.toString())}"
            }
            throw FileNotFoundException("PandaWave artwork fetch failed: ${error.message}")
        }
        val file = File.createTempFile("pw-art-", ".img", cacheDir)
        try {
            file.writeBytes(bytes)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            // Unlink after open so the inode lives only as long as the caller's FD.
            file.delete()
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun queryValue(column: String, source: Uri): Any? = when (column) {
        OpenableColumns.DISPLAY_NAME -> source.lastPathSegment ?: "artwork"
        OpenableColumns.SIZE -> null
        else -> null
    }

    private fun fetchBytes(source: Uri): ByteArray {
        val connection = source.toURLConnection()
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/*")
            val code = connection.responseCode
            val redirected = connection.url.toString().toJavaUriOrNull()
            if (redirected == null || !PandaWaveArtworkContract.isAllowedRemoteSource(redirected)) {
                throw FileNotFoundException("Refusing artwork redirect to ${connection.url}")
            }
            if (code !in 200..299) {
                throw FileNotFoundException("Artwork fetch HTTP $code for $source")
            }
            return connection.inputStream.use { input -> input.readBytesLimited(MAX_ARTWORK_BYTES) }
        } finally {
            connection.disconnect()
        }
    }
}

private fun InputStream.readBytesLimited(maxBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    copyToLimited(output, maxBytes)
    return output.toByteArray()
}

private fun InputStream.copyToLimited(out: OutputStream, maxBytes: Long) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return
        copied += read
        if (copied > maxBytes) {
            throw IOException("Artwork exceeded $maxBytes bytes")
        }
        out.write(buffer, 0, read)
    }
}

private fun Uri.toURLConnection(): HttpURLConnection =
    java.net.URI(toString()).toURL().openConnection() as HttpURLConnection

private const val READ_MODE = "r"
private const val DEFAULT_MIME_TYPE = "image/*"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000
private const val MAX_ARTWORK_BYTES = 2L * 1024L * 1024L
private val DEFAULT_QUERY_COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
