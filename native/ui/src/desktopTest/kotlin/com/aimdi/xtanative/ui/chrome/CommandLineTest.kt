package com.aimdi.xtanative.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun anUnknownOptionIsRefusedInsteadOfStartingTheApp() {
        assertEquals(CommandLine.Answer.UNKNOWN, CommandLine.answer(arrayOf("--bogus")))
        assertEquals(CommandLine.Answer.UNKNOWN, CommandLine.answer(arrayOf("-x")))
        assertEquals(CommandLine.Answer.UNKNOWN, CommandLine.answer(arrayOf("--bogus", "--help")), "nothing else is done with a wrong start")
        assertEquals(CommandLine.Answer.UNKNOWN, CommandLine.answer(arrayOf("https://x.com/a", "--minimised")))
        assertEquals("--bogus", CommandLine.unknownOption(arrayOf("https://x.com/a", "--bogus", "-y")))
        assertNull(CommandLine.unknownOption(arrayOf("--minimized", "https://x.com/a/status/1")), "the options the .desktop file and autostart pass")
        assertNull(CommandLine.unknownOption(arrayOf("https://x.com/-h")))
        assertEquals("xta-native: unknown option '--bogus'", CommandLine.unknownLine("--bogus"))
        assertEquals(2, CommandLine.USAGE_ERROR)
        CommandLine.options.forEach { assertTrue(it in CommandLine.usage, it) }
    }

    @Test
    fun theVersionLineAndUsageNameWhatTheAppTakes() {
        assertEquals("xta-native 1.2.3", CommandLine.versionLine("1.2.3"))
        assertEquals("xta-native", CommandLine.versionLine(""))
        listOf("--minimized", "--verbose", "--version", "--help", "URL", "XDG_STATE_HOME/xta-native/logs").forEach { assertTrue(it in CommandLine.usage, it) }
    }
}
