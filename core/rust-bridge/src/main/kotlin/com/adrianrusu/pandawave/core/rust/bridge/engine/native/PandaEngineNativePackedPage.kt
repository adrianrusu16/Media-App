package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.common.log.PandaLog

internal object PandaEngineNativePackedPage {
    fun <T> toItems(values: Array<String>?, valueCount: Int, itemAt: (Array<String>, Int) -> T?): List<T> {
        if (values.isNullOrEmpty() || valueCount <= 0) {
            return emptyList()
        }
        if (values.size % valueCount != 0) {
            PandaLog.e(PandaLog.Tag.MEDIA) {
                "packed_page_stride_mismatch valueCount=$valueCount packedSize=${values.size} " +
                    "remainder=${values.size % valueCount} " +
                    "(Kotlin/native FFI field counts diverge — rebuild libpanda_engine_ffi.so)"
            }
            return emptyList()
        }
        val parsed = (0 until values.size / valueCount).mapNotNull { index ->
            itemAt(values, index * valueCount)
        }
        val expected = values.size / valueCount
        if (parsed.size != expected) {
            PandaLog.w(PandaLog.Tag.MEDIA) {
                "packed_page_drop expected=$expected kept=${parsed.size} valueCount=$valueCount " +
                    "packedSize=${values.size}"
            }
        }
        return parsed
    }
}
