package com.aimdi.xtanative.ui.importer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.ui.common.FileChoosers
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.import_choose
import com.aimdi.xtanative.ui.plugins.DroppedFiles
import com.aimdi.xtanative.ui.plugins.takesFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** The desktop's open dialog (its portal's, else GTK's), over any file. */
@Composable
internal actual fun rememberBackupChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit {
    val title = stringResource(Res.string.import_choose)
    return { FileChoosers.launch { FileChoosers.open(title)?.let { file -> onChosen { file.readText() } } } }
}

/** A backup dropped from the file manager, read as one chosen in the dialog is. */
@Composable
internal actual fun Modifier.takesDroppedBackup(enabled: Boolean, onDropped: (read: suspend () -> String?) -> Unit): Modifier =
    takesFiles(enabled, pick = BackupDrops::backupIn) { file ->
        onDropped { withContext(Dispatchers.IO) { DroppedFiles.readUpTo(file, BackupDrops.MAX_BYTES).decodeToString() } }
    }

/** Which file of a drop is read as a backup. */
internal object BackupDrops {
    /** The largest file read as a backup (256 MiB): saved posts and notes make big ones, a disk image is none. */
    const val MAX_BYTES: Long = 256L * 1024 * 1024

    /** The first of [files] small enough to be one; whether it is a backup, reading it tells. Asks the disk, so off the window's thread. */
    fun backupIn(files: List<File>): File? = files.firstOrNull()?.takeIf { it.isFile && it.length() <= MAX_BYTES }
}
