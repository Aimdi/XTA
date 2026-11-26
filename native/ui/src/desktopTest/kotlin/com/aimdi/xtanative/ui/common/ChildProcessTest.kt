package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class ChildProcessTest {
    private val archive = "-XX:SharedArchiveFile=/c/1.jsa -Xshare:auto"

    @Test
    fun `the reader's own options come back`() {
        val env = mutableMapOf(
            "JAVA_TOOL_OPTIONS" to "$archive -Dfoo=1",
            ChildProcess.ORIGINAL to "-Dfoo=1",
            ChildProcess.WAS_SET to "1",
            "PATH" to "/usr/bin",
        )
        ChildProcess.restore(env)
        assertEquals(mapOf("JAVA_TOOL_OPTIONS" to "-Dfoo=1", "PATH" to "/usr/bin"), env)
    }

    @Test
    fun `an empty value the reader had stays set`() {
        val env = mutableMapOf("JAVA_TOOL_OPTIONS" to archive, ChildProcess.ORIGINAL to "", ChildProcess.WAS_SET to "1")
        ChildProcess.restore(env)
        assertEquals(mapOf("JAVA_TOOL_OPTIONS" to ""), env)
    }

    @Test
    fun `options the reader did not have are taken out`() {
        val env = mutableMapOf("JAVA_TOOL_OPTIONS" to archive, ChildProcess.ORIGINAL to "", ChildProcess.WAS_SET to "0", "HOME" to "/h")
        ChildProcess.restore(env)
        assertEquals(mapOf("HOME" to "/h"), env)
    }

    @Test
    fun `without the launcher's marks nothing changes`() {
        val env = mutableMapOf("JAVA_TOOL_OPTIONS" to "-Dbar=2", "HOME" to "/h")
        ChildProcess.restore(env)
        assertEquals(mapOf("JAVA_TOOL_OPTIONS" to "-Dbar=2", "HOME" to "/h"), env)
    }

    @Test
    fun `a builder has the reader's environment`() {
        val env = ChildProcess.builder("true").environment()
        assertEquals(false, ChildProcess.WAS_SET in env)
        assertEquals(false, ChildProcess.ORIGINAL in env)
    }
}
