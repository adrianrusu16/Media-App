package com.adrianrusu.pandawave.core.rust.bridge.engine

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

    fun isCached(trackId: String): Boolean = cacheFileForTrack(trackId).isFile

    fun put(trackId: String, source: InputStream): File {
        ensureCacheDirectory()
        val destination = cacheFileForTrack(trackId)
        val temporaryFile = File.createTempFile(
            destination.nameWithoutExtension,
            TEMP_FILE_EXTENSION,
            audioCacheDirectory
        )

        try {
            temporaryFile.outputStream().use { output -> source.copyTo(output) }
            moveReplacingCompletedFile(temporaryFile, destination)
            return destination
        } catch (error: Throwable) {
            temporaryFile.delete()
            throw error
        }
    }

    private fun ensureCacheDirectory() {
        if (audioCacheDirectory.isDirectory) return
        if (!audioCacheDirectory.mkdirs() && !audioCacheDirectory.isDirectory) {
            throw IOException("Unable to create PandaWave audio cache directory: $audioCacheDirectory")
        }
    }

    private fun moveReplacingCompletedFile(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private companion object {
        const val TEMP_FILE_EXTENSION = ".tmp"
    }
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
