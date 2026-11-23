package com.aimdi.xtanative.ui.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutostartEntryTest {
    @Test
    fun theEntryIsWrittenAndTakenAway() {
        val dir = Files.createTempDirectory("autostart").toFile()
        try {
            val folder = File(dir, "autostart")
            val entry = AutostartEntry(folder, "xta-native")
            assertFalse(entry.isOn())
            assertTrue(entry.set(true))
            assertTrue(entry.isOn())
            val text = File(folder, AutostartEntry.FILE_NAME).readText()
            assertTrue("Exec=xta-native --minimized\n" in text)
            assertTrue(text.startsWith("[Desktop Entry]\nType=Application\n"))
            assertFalse(File(folder, AutostartEntry.FILE_NAME + ".part").exists())
            assertTrue(entry.set(false))
            assertFalse(entry.isOn())
            // An entry the desktop's own settings switched off counts as off.
            File(folder, AutostartEntry.FILE_NAME).writeText(AutostartEntry.text("xta-native") + "Hidden=true\n")
            assertFalse(entry.isOn())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theEntryRunsThePackagesCommandOrTheLaunchersPath() {
        val dir = Files.createTempDirectory("bin").toFile()
        try {
            val bin = File(dir, "xta-native").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
            assertEquals("xta-native", AutostartEntry.execFor("/opt/xta-native/bin/xta-native", dir.path, "/home/me"))
            // Not on PATH, or a copy in the home folder: its own path, quoted.
            assertEquals("\"/opt/xta-native/bin/xta-native\"", AutostartEntry.execFor("/opt/xta-native/bin/xta-native", "/nowhere", "/home/me"))
            assertEquals("\"/home/me/My Apps/xta-native/bin/xta-native\"", AutostartEntry.execFor("/home/me/My Apps/xta-native/bin/xta-native", dir.path, "/home/me"))
            assertEquals("\"/home/me/a\\\$b\\\"c/xta\"", AutostartEntry.execFor("/home/me/a\$b\"c/xta", "/nowhere", "/home/me"))
            assertNull(AutostartEntry.execFor(null, dir.path, "/home/me"))
            bin.delete()
        } finally {
            dir.deleteRecursively()
        }
    }
}
