package com.aimdi.xtanative.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `xta-native --help` and `--version`, answered before the app starts. */
class CommandLineTest {
    @Test
    fun helpAndVersionAreAnsweredAndAnythingElseStartsTheApp() {
        assertEquals(CommandLine.Answer.HELP, CommandLine.answer(arrayOf("--help")))
        assertEquals(CommandLine.Answer.HELP, CommandLine.answer(arrayOf("-h")))
        assertEquals(CommandLine.Answer.HELP, CommandLine.answer(arrayOf("--version", "-h")), "help wins over the version")
        assertEquals(CommandLine.Answer.VERSION, CommandLine.answer(arrayOf("--version")))
        assertEquals(CommandLine.Answer.RUN, CommandLine.answer(emptyArray()))
        assertEquals(CommandLine.Answer.RUN, CommandLine.answer(arrayOf("--minimized", "https://x.com/a/status/1")))
        assertEquals(CommandLine.Answer.RUN, CommandLine.answer(arrayOf("https://x.com/-h")), "a link is no option")
    }

    @Test
    fun theVersionLineAndUsageNameWhatTheAppTakes() {
        assertEquals("xta-native 1.2.3", CommandLine.versionLine("1.2.3"))
        assertEquals("xta-native", CommandLine.versionLine(""))
        listOf("--minimized", "--version", "--help", "URL").forEach { assertTrue(it in CommandLine.usage, it) }
    }
}
