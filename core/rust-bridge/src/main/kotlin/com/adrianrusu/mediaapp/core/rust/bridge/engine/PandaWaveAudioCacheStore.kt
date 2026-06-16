package com.adrianrusu.mediaapp.core.rust.bridge.engine

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

class PandaWaveAudioCacheStore(private val audioCacheDirectory: File) : PandaWaveAudioContentStore {
    override fun open(trackId: String): ParcelFileDescriptor {
        val file = cacheFileForTrack(trackId)
        if (!file.isFile) {
            throw FileNotFoundException("PandaWave audio cache miss for trackId=$trackId.")
        }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    fun cacheFileForTrack(trackId: String): File =
        audioCacheDirectory.resolve(PandaWaveAudioCacheKey.fileNameForTrack(trackId))
}

object PandaWaveAudioCacheKey {
    private const val FILE_EXTENSION = ".audio"

    fun fileNameForTrack(trackId: String): String {
        val normalized = trackId.trim()
        require(normalized.isNotBlank()) { "PandaWave audio cache track id must not be blank." }
        return normalized.sha256Hex() + FILE_EXTENSION
    }

    private fun String.sha256Hex(): String = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
