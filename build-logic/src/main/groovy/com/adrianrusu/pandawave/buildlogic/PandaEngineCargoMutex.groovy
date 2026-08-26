package com.adrianrusu.pandawave.buildlogic

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** Serializes PandaEngine cargo ABI tasks so they do not contend on Cargo's shared locks. */
abstract class PandaEngineCargoMutex implements BuildService<BuildServiceParameters.None> {
}
