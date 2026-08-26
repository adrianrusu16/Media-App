package com.adrianrusu.pandawave.core.rust.bridge.engine.native

internal object PandaEngineNativePackedPage {
    fun <T> toItems(values: Array<String>?, valueCount: Int, itemAt: (Array<String>, Int) -> T?): List<T> {
        if (values.isNullOrEmpty() || valueCount <= 0 || values.size % valueCount != 0) {
            return emptyList()
        }
        return (0 until values.size / valueCount).mapNotNull { index ->
            itemAt(values, index * valueCount)
        }
    }
}
