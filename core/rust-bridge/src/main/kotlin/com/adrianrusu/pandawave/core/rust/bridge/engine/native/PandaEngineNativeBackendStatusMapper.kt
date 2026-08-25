package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineBackendDependencyStatus
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineBackendStatus

internal object PandaEngineNativeBackendStatusMapper {
    fun toDomain(values: Array<String>): EngineBackendStatus {
        require(values.size >= HEADER_VALUE_COUNT) { "Native backend status header is incomplete." }
        val dependencyCount = values[DEPENDENCY_COUNT_INDEX].toInt()
        require(dependencyCount >= 0 && values.size == HEADER_VALUE_COUNT + dependencyCount * 3) {
            "Native backend dependency values are incomplete."
        }

        return EngineBackendStatus(
            healthy = when (values[HEALTHY_INDEX]) {
                "1" -> true
                "0" -> false
                else -> error("Native backend health value is invalid.")
            },
            version = values[VERSION_INDEX],
            status = values[STATUS_INDEX],
            checkedAtEpochMillis = values[CHECKED_AT_INDEX].takeIf(String::isNotEmpty)?.toLong(),
            dependencies = List(dependencyCount) { index ->
                val offset = HEADER_VALUE_COUNT + index * 3
                EngineBackendDependencyStatus(
                    name = values[offset],
                    status = values[offset + 1],
                    message = values[offset + 2]
                )
            }
        )
    }

    private const val HEALTHY_INDEX = 0
    private const val VERSION_INDEX = 1
    private const val STATUS_INDEX = 2
    private const val CHECKED_AT_INDEX = 3
    private const val DEPENDENCY_COUNT_INDEX = 4
    private const val HEADER_VALUE_COUNT = 5
}
