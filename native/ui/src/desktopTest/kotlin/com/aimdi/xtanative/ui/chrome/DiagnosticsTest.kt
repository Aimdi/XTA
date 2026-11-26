package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.ui.plugins.OLDEST_PLUGIN_API
import com.aimdi.xtanative.ui.plugins.PLUGIN_API_VERSION
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticsTest {
    private val env = mapOf(
        "XDG_CURRENT_DESKTOP" to "Hyprland",
        "XDG_SESSION_TYPE" to "wayland",
        "XTA_NATIVE_MOCK_X" to "http://127.0.0.1:18183",
        "HTTPS_PROXY" to "http://user:secret@proxy:3128",
    )
    private val props = mapOf(
        "xtanative.version" to "1.0.17",
        "os.name" to "Linux",
        "os.version" to "6.9.1",
        "os.arch" to "amd64",
        "java.runtime.version" to "17.0.21+9",
        "java.vendor" to "Eclipse Adoptium",
    )

    @Test
    fun theTextNamesVersionsSystemDesktopAndRendererOnly() {
        val release = createTempFile().toFile().apply { writeText("NAME=\"Arch Linux\"\nPRETTY_NAME=\"Arch Linux\"\nID=arch\n") }
        Diagnostics.renderer = { "OPENGL" }
        val text = Diagnostics.text(Diagnostics.facts(env::get, props::get, release) { "libmpv.so.2, mpv client API 2.5" })
        release.delete()
        Diagnostics.renderer = { null }
        val lines = text.lines()
        assertEquals("XTA Native 1.0.17", lines[0])
        assertEquals("System: Arch Linux; Linux 6.9.1 amd64", lines[1])
        assertEquals("Desktop: Hyprland; session wayland", lines[2])
        assertEquals("Renderer: OPENGL", lines[3])
        assertEquals("Java: 17.0.21+9, Eclipse Adoptium", lines[4])
        assertEquals("Plugin API: $PLUGIN_API_VERSION (oldest $OLDEST_PLUGIN_API)", lines[5])
        assertTrue(lines.last().startsWith("Video: libmpv.so.2"))
        listOf("127.0.0.1", "secret", "proxy").forEach { assertFalse(it in text, it) }
    }

    @Test
    fun unknownFactsSaySoAndNoFileIsNoName() {
        val text = Diagnostics.text(Diagnostics.facts({ null }, { null }, File("/nonexistent/os-release")) { "no mpv" })
        assertTrue("XTA Native dev" in text)
        assertTrue("System: unknown" in text)
        assertTrue("Renderer: unknown" in text)
    }

    @Test
    fun osReleaseGivesItsPrettyNameElseItsName() {
        assertEquals("Ubuntu 24.04 LTS", Diagnostics.prettyName("NAME=Ubuntu\nPRETTY_NAME='Ubuntu 24.04 LTS'"))
        assertEquals("Fedora", Diagnostics.prettyName("NAME=Fedora\n"))
        assertNull(Diagnostics.prettyName(null))
    }

    @Test
    fun theHomeFolderAndUserNameAreTakenOut() {
        assertEquals("Video: no mpv at ~/lib for [user]", Diagnostics.scrubbed("Video: no mpv at /home/alice/lib for alice", "/home/alice", "alice"))
    }
}
