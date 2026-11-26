package com.aimdi.xtanative.ui.importer

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupDropsTest {
    private val dir: File = Files.createTempDirectory("xta-backup-drops").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun theFirstFileOfADropIsTheBackup() {
        val first = File(dir, "xta-2026-11-26.json").apply { writeText("{}") }
        val second = File(dir, "other.json").apply { writeText("{}") }
        assertEquals(first, BackupDrops.backupIn(listOf(first, second)))
    }

    @Test
    fun aDropWithoutFilesHasNoBackup() {
        assertNull(BackupDrops.backupIn(emptyList()))
        assertNull(BackupDrops.backupIn(listOf(File(dir, "missing.json"))))
    }
}
