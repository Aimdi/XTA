package com.aimdi.xtanative.ui.settings

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals

/** An exported backup can carry the X sessions: it is the reader's own to read, even over an older readable one. */
class BackupFileTest {
    @Test
    fun anExportIsReadableByItsOwnerOnly() {
        val folder = Files.createTempDirectory("downloads").toFile()
        val file = File(folder, "xta-backup.json").apply {
            writeText("old")
            Files.setPosixFilePermissions(toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        }
        BackupFile.write(file, """{"accounts":[]}""")
        assertEquals("""{"accounts":[]}""", file.readText())
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
        assertEquals(listOf("xta-backup.json"), folder.list()!!.toList())
        folder.deleteRecursively()
    }
}
