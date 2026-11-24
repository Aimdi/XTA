package com.aimdi.xtanative.ui.chrome

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLogTest {
    @Test
    fun theLogLivesInTheStateFolderAndARelativeOneIsIgnored() {
        assertEquals(File("/s/xta-native/logs"), AppLog.folder({ if (it == "XDG_STATE_HOME") "/s" else null }, "/home/r"))
        assertEquals(File("/home/r/.local/state/xta-native/logs"), AppLog.folder({ if (it == "XDG_STATE_HOME") "state" else null }, "/home/r"))
        assertEquals(File("/home/r/.local/state/xta-native/logs"), AppLog.folder({ null }, "/home/r"))
    }

    @Test
    fun credentialsNeverReachTheLog() {
        val lines = listOf(
            "Authorization: Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs",
            "cookie: ct0=0123456789abcdef; auth_token=feedface1234",
            """{"Cookie":"guest_id=v1%3A1; kdt=xyz","ok":1}""",
            "GET https://x.com/i/api/graphql/abc?auth_token=deadbeef&count=20",
            "header x-csrf-token=0123456789abcdef0123",
            """{"access_token":"ya29.secret","refresh_token":"1//secret"}""",
            "failed with bearer AbCdEfGh12345678",
        )
        val secrets = listOf("AAAAAAAAAAAAAAAAAAAAANRIL", "0123456789abcdef", "feedface1234", "guest_id=v1", "xyz", "deadbeef", "ya29.secret", "1//secret", "AbCdEfGh12345678")
        lines.map(LogRedaction::redact).forEach { line ->
            secrets.forEach { secret -> assertFalse(secret in line, "$secret in $line") }
            assertTrue("[redacted]" in line, line)
        }
        assertEquals("XTA Native: video: mpv did not open the source: loading failed", LogRedaction.redact("XTA Native: video: mpv did not open the source: loading failed"))
        assertEquals("count=20&cursor=abc, the basic settings", LogRedaction.redact("count=20&cursor=abc, the basic settings"))
    }

    @Test
    fun theLogTurnsOverAtItsSizeAndKeepsThreeFiles() {
        val folder = Files.createTempDirectory("xta-log").toFile().resolve("xta-native/logs")
        val log = RotatingLog(folder, maxBytes = 200, files = 3)
        repeat(20) { log.write("err", "line $it ${"x".repeat(40)}") }
        val names = folder.list()!!.sorted()
        assertEquals(listOf("xta-native.log", "xta-native.log.1", "xta-native.log.2"), names)
        names.forEach { assertTrue(File(folder, it).length() <= 200, "$it is ${File(folder, it).length()} bytes") }
        assertTrue("line 19" in File(folder, "xta-native.log").readText())
        assertFalse(folder.listFiles()!!.any { "line 0 " in it.readText() }, "the oldest went")
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(folder.toPath())))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(folder.parentFile.toPath())))
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(File(folder, "xta-native.log").toPath())))
        // A new start goes on in the same file.
        RotatingLog(folder, maxBytes = 200, files = 3).write("out", "again")
        assertTrue("again" in File(folder, "xta-native.log").readText())
    }

    @Test
    fun theTeeWritesThroughAndHandsOnWholeLines() {
        val terminal = ByteArrayOutputStream()
        val lines = mutableListOf<String>()
        val tee = LogTee(terminal, "err") { stream, line -> lines += "$stream:$line" }
        tee.write("one\ntw".toByteArray())
        tee.write("o ä\r\n".toByteArray())
        tee.write('x'.code)
        assertEquals("one\ntwo ä\r\nx", terminal.toString(Charsets.UTF_8))
        assertEquals(listOf("err:one", "err:two ä"), lines)
    }

    @Test
    fun aFakeOpenerIsHandedTheLogFolder() {
        val dir = Files.createTempDirectory("xta-open").toFile()
        val seen = File(dir, "seen")
        val opener = File(dir, "xdg-open").apply {
            writeText("#!/bin/sh\nprintf '%s' \"$1\" > '${seen.path}'\n")
            setExecutable(true)
        }
        val folder = File(dir, "state/xta-native/logs")
        assertTrue(LogsFolder.open(folder, opener.path))
        repeat(50) { if (!seen.exists()) Thread.sleep(100) }
        assertEquals(folder.path, seen.readText())
        assertTrue(folder.isDirectory, "made before it is opened")
    }
}
