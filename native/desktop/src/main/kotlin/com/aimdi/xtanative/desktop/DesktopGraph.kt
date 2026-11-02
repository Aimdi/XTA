package com.aimdi.xtanative.desktop

import com.aimdi.xtanative.data.AppGraph
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.flow.flowOf

/**
 * The app graph on the Linux desktop: its files in ~/.local/share/xta-native,
 * always online, and XTA's stand-in for X instead of x.com when
 * XTA_NATIVE_MOCK_X names its address (as CI's desktop check does).
 */
internal fun desktopGraph(): AppGraph = AppGraph(
    files = dataFolder(),
    connection = flowOf(true),
    version = System.getProperty("xtanative.version").orEmpty(),
    mockX = System.getenv("XTA_NATIVE_MOCK_X")?.takeIf { it.isNotBlank() },
)

/**
 * xta-native under $XDG_DATA_HOME, ~/.local/share where that is not set. Only
 * its user may open it: the session in it holds X cookies.
 */
private fun dataFolder(): File {
    val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
        ?: File(System.getProperty("user.home"), ".local/share")
    return File(base, "xta-native").apply {
        mkdirs()
        runCatching { Files.setPosixFilePermissions(toPath(), PosixFilePermissions.fromString("rwx------")) }
    }
}
