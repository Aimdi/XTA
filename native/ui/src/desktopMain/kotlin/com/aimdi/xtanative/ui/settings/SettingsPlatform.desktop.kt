package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.store.replaceWith
import com.aimdi.xtanative.ui.chrome.AppLog
import com.aimdi.xtanative.ui.chrome.Diagnostics
import com.aimdi.xtanative.ui.chrome.LogsFolder
import com.aimdi.xtanative.ui.common.FileChoosers
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_download_path
import com.aimdi.xtanative.ui.generated.resources.settings_export
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal actual val isDesktop: Boolean = true

/** The desktop's save dialog, in the downloads folder first. */
@Composable
internal actual fun rememberBackupSaver(onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit {
    val title = stringResource(Res.string.settings_export)
    return { fileName, text ->
        FileChoosers.launch {
            val chosen = FileChoosers.save(title, fileName)
            val saved = chosen != null && withContext(Dispatchers.IO) { runCatching { BackupFile.write(chosen, text) }.isSuccess }
            onSaved(chosen?.name?.takeIf { saved })
        }
    }
}

/** A backup on disk. It can hold the X sessions, so only its owner may read it. */
internal object BackupFile {
    /** [text] as [file], whole; where a file cannot be staged beside it, written in place and made the owner's only. */
    fun write(file: File, text: String) {
        try {
            file.replaceWith(text)
        } catch (_: IOException) {
            file.writeText(text)
            runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
        }
    }
}

/** A folder chooser; the folder's path is what is stored. */
@Composable
internal actual fun rememberFolderPicker(onPicked: (stored: String, name: String) -> Unit): () -> Unit {
    val title = stringResource(Res.string.settings_download_path)
    return {
        FileChoosers.launch {
            FileChoosers.folder(title, File(System.getProperty("user.home")))?.let { onPicked(it.absolutePath, it.name) }
        }
    }
}


/** The log [AppLog] keeps, in `$XDG_STATE_HOME/xta-native/logs`. */
internal actual val logsFolder: String? get() = AppLog.folder().path

/** Through xdg-open, the folder handed as one argument. */
internal actual fun openLogsFolder(): Boolean = LogsFolder.open()

/** [Diagnostics]' text, of this machine now. */
internal actual fun diagnosticsText(): String? = Diagnostics.text(Diagnostics.facts())
