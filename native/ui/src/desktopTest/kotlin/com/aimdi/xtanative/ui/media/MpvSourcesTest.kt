package com.aimdi.xtanative.ui.media

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvSourcesTest {
    private val app = Files.createTempDirectory("xta-mpv").toFile().resolve("xta-native").apply { mkdirs() }
    private val folders = listOf(app)

    private fun allowed(source: String) = MpvSources.allowed(source, folders)

    @Test
    fun webAddressesAreAllowed() {
        assertTrue(allowed("https://video.twimg.com/a.mp4"))
        assertTrue(allowed("HTTP://127.0.0.1:8805/video.mp4"))
        assertFalse(allowed("https:video.twimg.com"))
    }

    @Test
    fun otherSchemesAreRefused() {
        listOf(
            "rtmp://h/live", "rtsp://h/s", "udp://1.2.3.4:5", "ftp://h/a.mp4", "smb://h/s/a.mp4", "sftp://h/a",
            "lavfi://sine", "edl://a.mp4", "av://v4l2:/dev/video0", "ytdl://x", "memory://x", "data:video/mp4;base64,AA", "a.mp4", "",
        ).forEach { assertFalse(allowed(it), it) }
    }

    @Test
    fun onlyTheAppsOwnFilesAreAllowed() {
        val kept = File(app, "offline/v.mp4").apply { parentFile.mkdirs(); writeText("") }
        assertTrue(allowed(kept.path))
        assertTrue(allowed(kept.toURI().toString()))
        assertFalse(allowed("/etc/passwd"))
        assertFalse(allowed("${app.path}/../secret.mp4"), "no climbing out")
        assertFalse(allowed("${app.path}-other/a.mp4"))
        assertFalse(allowed("file:///etc/passwd"))
    }

    @Test
    fun aLinkOutOfTheAppsFolderIsRefused() {
        val outside = Files.createTempFile("xta-outside", ".mp4").toFile()
        val link = File(app, "offline/link.mp4").apply { parentFile.mkdirs() }
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertFalse(allowed(link.path))
    }
}
