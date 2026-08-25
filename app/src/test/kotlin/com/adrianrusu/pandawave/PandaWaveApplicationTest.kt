package com.adrianrusu.pandawave

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PandaWaveApplicationTest {
    @Test
    fun defaultProcessMatchesPackageNameOnly() {
        assertTrue(isDefaultProcess(packageName = "com.adrianrusu.pandawave", processName = "com.adrianrusu.pandawave"))
        assertFalse(isDefaultProcess(packageName = "com.adrianrusu.pandawave", processName = "com.adrianrusu.pandawave:engine"))
    }
}
