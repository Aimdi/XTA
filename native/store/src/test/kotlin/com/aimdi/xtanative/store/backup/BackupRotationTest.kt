package com.aimdi.xtanative.store.backup

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class BackupRotationTest {
    private val start = Instant.parse("2026-11-27T10:00:00Z")

    private fun folder(): File = Files.createTempDirectory("rotation").toFile().resolve("backups")

    @Test
    fun copiesAreTheOwnersAloneAndReadBack() = runBlocking {
        val folder = folder()
        val rotation = BackupRotation(folder)
        val copy = assertNotNull(rotation.write(LocalBackupKind.DAILY, "{\"a\":1}", start))
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(copy.file.toPath())))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(folder.toPath())))
        assertEquals("{\"a\":1}", rotation.read(copy, 1024))
        assertNull(rotation.read(copy, 2), "past the limit")
        assertEquals(listOf(copy.copy(bytes = 7)), rotation.list())
        assertTrue(folder.listFiles()!!.none { it.name.endsWith(".part") }, "nothing staged is left")
    }

    @Test
    fun aDailyCopyIsDueOnceADay() = runBlocking {
        val rotation = BackupRotation(folder())
        assertTrue(rotation.dueDaily(start))
        rotation.write(LocalBackupKind.BEFORE_RESTORE, "{}", start)
        assertTrue(rotation.dueDaily(start), "a copy before a restore is no daily one")
        rotation.write(LocalBackupKind.DAILY, "{}", start)
        assertFalse(rotation.dueDaily(start.plus(Duration.ofHours(23))))
        assertTrue(rotation.dueDaily(start.plus(Duration.ofHours(24))))
    }

    @Test
    fun theNewestOfEachKindStay() = runBlocking {
        val rotation = BackupRotation(folder(), keepDaily = 3, keepBeforeRestore = 2)
        repeat(5) { day -> rotation.write(LocalBackupKind.DAILY, "{}", start.plus(Duration.ofDays(day.toLong()))) }
        repeat(4) { n -> rotation.write(LocalBackupKind.BEFORE_RESTORE, "{}", start.plusSeconds(n.toLong())) }
        val kept = rotation.list()
        assertEquals(listOf(4L, 3L, 2L), kept.filter { it.kind == LocalBackupKind.DAILY }.map { Duration.between(start, it.madeAt).toDays() })
        assertEquals(listOf(3L, 2L), kept.filter { it.kind == LocalBackupKind.BEFORE_RESTORE }.map { Duration.between(start, it.madeAt).seconds })
    }

    @Test
    fun aCopyReadBackToRestoreIsNotPrunedByTheCopyMadeBeforeThatRestore() = runBlocking {
        val rotation = BackupRotation(folder(), keepDaily = 1, keepBeforeRestore = 1)
        val chosen = assertNotNull(rotation.write(LocalBackupKind.BEFORE_RESTORE, "{\"old\":1}", start))
        assertNotNull(rotation.read(chosen, 1024))
        rotation.write(LocalBackupKind.BEFORE_RESTORE, "{}", start.plusSeconds(60))
        assertTrue(chosen.file.isFile, "the copy being restored stays")
        rotation.write(LocalBackupKind.DAILY, "{}", start.plusSeconds(120))
        assertTrue(chosen.file.isFile)
    }

    @Test
    fun copiesPastTheBudgetGoButTheNewestOfEachKindStays() = runBlocking {
        val rotation = BackupRotation(folder(), budgetBytes = 25)
        val big = "x".repeat(10)
        repeat(3) { day -> rotation.write(LocalBackupKind.DAILY, big, start.plus(Duration.ofDays(day.toLong()))) }
        rotation.write(LocalBackupKind.BEFORE_RESTORE, "x".repeat(40), start.plus(Duration.ofDays(5)))
        val kept = rotation.list()
        assertEquals(listOf(LocalBackupKind.BEFORE_RESTORE, LocalBackupKind.DAILY), kept.map { it.kind }, "both newest stay, even past the budget")
    }

    @Test
    fun twoCopiesInOneSecondKeepBoth() = runBlocking {
        val rotation = BackupRotation(folder())
        rotation.write(LocalBackupKind.BEFORE_RESTORE, "1", start)
        rotation.write(LocalBackupKind.BEFORE_RESTORE, "2", start)
        val kept = rotation.list()
        assertEquals(2, kept.size)
        assertEquals("2", rotation.read(kept.first(), 10), "the later one is newest")
    }

    @Test
    fun otherFilesAreNoCopies() = runBlocking {
        val folder = folder().apply { mkdirs() }
        File(folder, "notes.json").writeText("{}")
        File(folder, "xta-auto-20261127T100000Z-weekly.json").writeText("{}")
        val rotation = BackupRotation(folder)
        assertEquals(emptyList(), rotation.list())
        val outside = LocalBackup(File(folder.parentFile, "xta-auto-20261127T100000Z-daily.json").apply { writeText("{}") }, LocalBackupKind.DAILY, start, 2)
        assertNull(rotation.read(outside, 10), "only this folder's copies are read")
        rotation.write(LocalBackupKind.DAILY, "{}", start)
        assertTrue(File(folder, "notes.json").isFile, "pruning leaves other files alone")
    }
}
