package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult

internal object PandaEngineNativeAuthOperationMapper {
    fun toDomain(values: Array<String>?): EngineAuthOperationResult {
        if (values == null || values.size != VALUE_COUNT) {
            return EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_MAPPING_DEFECT)
        }
        return runCatching {
            when (values[STATUS_INDEX]) {
                EngineAuthOperationResult.STATUS_ACCEPTED -> EngineAuthOperationResult.accepted()
                EngineAuthOperationResult.STATUS_REJECTED -> EngineAuthOperationResult.rejected()
                EngineAuthOperationResult.STATUS_AUTHENTICATED ->
                    EngineAuthOperationResult.authenticated()
                EngineAuthOperationResult.STATUS_ANONYMOUS -> EngineAuthOperationResult.anonymous()
                EngineAuthOperationResult.STATUS_ERROR -> EngineAuthOperationResult.error(
                    errorType = values[ERROR_INDEX].ifBlank {
                        EngineAuthOperationResult.ERROR_UNKNOWN
                    },
                    retryAfterMillis = values[RETRY_INDEX].takeIf(String::isNotBlank)?.toLong()
                )
                else -> EngineAuthOperationResult.error(
                    EngineAuthOperationResult.ERROR_MAPPING_DEFECT
                )
            }
        }.getOrElse {
            EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_MAPPING_DEFECT)
        }
    }

    private const val VALUE_COUNT = 3
    private const val STATUS_INDEX = 0
    private const val ERROR_INDEX = 1
    private const val RETRY_INDEX = 2
}
