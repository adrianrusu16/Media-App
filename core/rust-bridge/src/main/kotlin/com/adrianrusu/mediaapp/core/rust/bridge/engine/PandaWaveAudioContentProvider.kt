package com.adrianrusu.mediaapp.core.rust.bridge.engine

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException

class PandaWaveAudioContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = if (trackIdFrom(uri) == null) {
        null
    } else {
        PandaWaveAudioSourceContract.DEFAULT_MIME_TYPE
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val trackId = trackIdFrom(uri) ?: return null
        val columns = projection?.takeUnless { value -> value.isEmpty() } ?: DEFAULT_QUERY_COLUMNS
        return MatrixCursor(columns).apply {
            addRow(columns.map { column -> queryValue(column, trackId) }.toTypedArray())
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val trackId = trackIdFrom(uri) ?: throw FileNotFoundException("Unsupported PandaWave audio uri: $uri")
        if (mode != READ_MODE) {
            throw FileNotFoundException("PandaWave audio uri is read-only: $uri")
        }

        return PandaWaveAudioContentStoreRegistry.open(trackId = trackId)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun trackIdFrom(uri: Uri): String? = PandaWaveAudioSourceContract.trackIdFromSourceUri(uri.toString())

    private fun queryValue(column: String, trackId: String): Any? = when (column) {
        OpenableColumns.DISPLAY_NAME -> "$trackId.mp3"
        OpenableColumns.SIZE -> null
        else -> null
    }

    private companion object {
        const val READ_MODE = "r"
        val DEFAULT_QUERY_COLUMNS = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
        )
    }
}

fun interface PandaWaveAudioContentStore {
    fun open(trackId: String): ParcelFileDescriptor
}

object PandaWaveAudioContentStoreRegistry {
    @Volatile
    private var store: PandaWaveAudioContentStore = UnavailablePandaWaveAudioContentStore

    fun install(store: PandaWaveAudioContentStore) {
        this.store = store
    }

    fun reset() {
        store = UnavailablePandaWaveAudioContentStore
    }

    fun open(trackId: String): ParcelFileDescriptor = store.open(trackId)
}

private object UnavailablePandaWaveAudioContentStore : PandaWaveAudioContentStore {
    override fun open(trackId: String): ParcelFileDescriptor =
        throw FileNotFoundException("No PandaWave audio content store configured for trackId=$trackId.")
}
