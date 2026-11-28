package com.aimdi.xtanative.store.backup

import com.aimdi.xtanative.store.replaceWith
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Why a local copy was made. */
enum class LocalBackupKind(val tag: String) {
    /** The copy made once a day while the app runs. */
    DAILY("daily"),

    /** The copy made right before an import or a restore from the server wrote over the device's data. */
    BEFORE_RESTORE("before-restore"),
}

/** One copy in the folder: when it was made, why, and how large it is. */
data class LocalBackup(val file: File, val kind: LocalBackupKind, val madeAt: Instant, val bytes: Long)

/**
 * The app's own copies of its data, made without the reader asking, in a
 * folder of the data folder only they can open (0700, each copy 0600): one a
 * day ([dueDaily]) and one before each import or restore, so a wrong one can
 * be undone. Each is an XTA backup written whole beside its name and then
 * given it, so a crash never leaves half a copy. The newest [keepDaily] daily
 * copies and [keepBeforeRestore] of the others stay, older ones go, and so do
 * any past [budgetBytes] together, the newest of each kind always kept, as a
 * saved library with many pictures in its notes makes copies of many
 * megabytes. Nothing here reaches the network.
 */
class BackupRotation(
    private val folder: File,
    private val keepDaily: Int = 7,
    private val keepBeforeRestore: Int = 3,
    private val budgetBytes: Long = 512L * 1024 * 1024,
) {
    private val mutex = Mutex()

    /** The copies read back to restore while the app runs: the copy made before that restore never prunes them. */
    private val restoring: MutableSet<File> = Collections.synchronizedSet(HashSet())

    /** The copies there, newest first. */
    suspend fun list(): List<LocalBackup> = mutex.withLock { withContext(Dispatchers.IO) { found() } }

    /** Whether a daily copy is due at [now]: none was made in the last day. */
    suspend fun dueDaily(now: Instant): Boolean = list().none { it.kind == LocalBackupKind.DAILY && Duration.between(it.madeAt, now) < DAY }

    /** [text] kept as a copy of [kind] made at [now], the oldest past the limits gone; the copy, or null when it could not be written. */
    suspend fun write(kind: LocalBackupKind, text: String, now: Instant): LocalBackup? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val written = runCatching {
                ownFolder()
                val file = File(folder, nameOf(kind, now, found()))
                file.replaceWith(text)
                runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
                LocalBackup(file, kind, now, file.length())
            }.getOrNull()
            prune(found()).filterNot { it.file.canonicalFile in restoring }.forEach { it.file.delete() }
            written
        }
    }

    /** [backup]'s text, refused once it is gone, no copy of this folder, or past [maxBytes]. */
    suspend fun read(backup: LocalBackup, maxBytes: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = backup.file.canonicalFile
            if (file.parentFile != folder.canonicalFile || parse(file) == null || file.length() > maxBytes) throw IOException("Not a copy")
            file.readText().also { restoring += file }
        }.getOrNull()
    }

    private fun found(): List<LocalBackup> =
        folder.listFiles().orEmpty().mapNotNull(::parse).sortedByDescending { it.madeAt }

    /** The copies of [all] (newest first) the limits leave out. */
    internal fun prune(all: List<LocalBackup>): List<LocalBackup> {
        val kept = LocalBackupKind.entries.flatMap { kind ->
            all.filter { it.kind == kind }.take(if (kind == LocalBackupKind.DAILY) keepDaily else keepBeforeRestore)
        }.sortedByDescending { it.madeAt }
        val newest = LocalBackupKind.entries.mapNotNull { kind -> kept.firstOrNull { it.kind == kind } }.toSet()
        var total = newest.sumOf { it.bytes }
        val withinBudget = kept.filter { backup ->
            if (backup in newest) return@filter true
            (total + backup.bytes <= budgetBytes).also { if (it) total += backup.bytes }
        }.toSet()
        return all.filterNot { it in withinBudget }
    }

    private fun ownFolder() {
        val owner = PosixFilePermissions.fromString("rwx------")
        if (!folder.isDirectory) runCatching { Files.createDirectories(folder.toPath(), PosixFilePermissions.asFileAttribute(owner)) }
        folder.mkdirs()
        runCatching { Files.setPosixFilePermissions(folder.toPath(), owner) }
    }

    companion object {
        private val DAY: Duration = Duration.ofHours(24)
        private const val PREFIX = "xta-auto-"
        private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        private val NAME = Regex("""xta-auto-(\d{8}T\d{6}Z)(?:-(\d+))?-([a-z-]+)\.json""")

        /** A name for a copy of [kind] at [now], one no copy of [existing] has: a second copy in the same second is numbered. */
        internal fun nameOf(kind: LocalBackupKind, now: Instant, existing: List<LocalBackup>): String {
            val taken = existing.mapTo(HashSet()) { it.file.name }
            val base = PREFIX + stamp.format(now)
            return generateSequence(0) { it + 1 }.map { n -> if (n == 0) "$base-${kind.tag}.json" else "$base-$n-${kind.tag}.json" }.first { it !in taken }
        }

        /** The copy [file] is, by its name; null for anything else in the folder (a staged write, a file someone put there). */
        internal fun parse(file: File): LocalBackup? {
            if (!file.isFile) return null
            val match = NAME.matchEntire(file.name) ?: return null
            val kind = LocalBackupKind.entries.firstOrNull { it.tag == match.groupValues[3] } ?: return null
            val at = runCatching { Instant.from(stamp.parse(match.groupValues[1])) }.getOrNull() ?: return null
            val order = match.groupValues[2].toLongOrNull() ?: 0
            return LocalBackup(file, kind, at.plusMillis(order), file.length())
        }
    }
}
