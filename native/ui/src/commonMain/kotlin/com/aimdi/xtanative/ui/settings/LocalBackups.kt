package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.store.backup.ExportChoice
import com.aimdi.xtanative.store.backup.LocalBackup
import com.aimdi.xtanative.store.backup.LocalBackupKind
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay

/** The app's automatic copies of its data, made without the reader asking. */
object LocalBackups {
    /** What a copy holds: all a full export would, but no X sign-ins, which never sit in a file the reader did not ask for. */
    val CHOICE: ExportChoice = ExportChoice.everything(includeAccounts = false)

    /** How long after the start the first daily copy waits: not while the window and feeds are being made. */
    val FIRST_AFTER: Duration = 1.minutes

    /** How often a running app asks whether a daily copy is due. */
    val EVERY: Duration = 6.hours

    /** How many copies Settings › Data lists, newest first. */
    const val SHOWN = 5

    /** The largest copy read back for a restore, as a backup file chosen by the reader is. */
    const val MAX_BYTES: Long = 256L * 1024 * 1024

    /** A daily copy made whenever none was in the last day, for as long as the app runs. */
    suspend fun keepDaily(backup: DeviceBackup, now: () -> Instant = Instant::now) {
        delay(FIRST_AFTER)
        while (true) {
            if (runCatching { backup.dueDaily(now()) }.getOrDefault(false)) backup.snapshot(LocalBackupKind.DAILY)
            delay(EVERY)
        }
    }

    /** The copies Settings › Data offers, of [all] (newest first). */
    fun shown(all: List<LocalBackup>): List<LocalBackup> = all.take(SHOWN)
}
