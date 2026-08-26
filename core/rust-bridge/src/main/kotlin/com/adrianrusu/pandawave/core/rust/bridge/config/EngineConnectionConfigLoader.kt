package com.adrianrusu.pandawave.core.rust.bridge.config

import android.content.Context
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object EngineConnectionConfigLoader {
    private const val ASSET_NAME = "client-connection.json"
    private const val MAX_CONFIG_BYTES = 65_536

    fun load(context: Context): String {
        val bytes = context.assets.open(ASSET_NAME).use { it.readBytes() }
        require(bytes.size <= MAX_CONFIG_BYTES) { "Canopy client connection asset is too large" }

        val decoder =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val json = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        return validate(json)
    }

    internal fun validate(json: String): String {
        require(json.toByteArray(StandardCharsets.UTF_8).size <= MAX_CONFIG_BYTES) {
            "Canopy client connection asset is too large"
        }
        require(json.isNotBlank()) { "Canopy client connection asset is blank" }
        return json
    }
}
