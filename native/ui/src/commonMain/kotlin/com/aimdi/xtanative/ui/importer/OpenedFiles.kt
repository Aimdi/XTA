package com.aimdi.xtanative.ui.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.data.plugins.Destination
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.ui.links.OpenRequests
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.plugins.OpenedPluginFile
import com.aimdi.xtanative.ui.plugins.PluginDroppedFile
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.settings.BackupRead
import com.aimdi.xtanative.ui.settings.LocalBackups
import com.aimdi.xtanative.ui.settings.RestoreDialog
import com.aimdi.xtanative.ui.settings.backupProblem
import com.aimdi.xtanative.ui.subscriptions.GroupAddSources
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_cancel
import com.aimdi.xtanative.ui.generated.resources.settings_ok
import com.aimdi.xtanative.ui.generated.resources.settings_open_opml

/** What a file opened with the app (`xta-native <file>`) turned out to be. */
sealed interface OpenedFile {
    /** An XTA backup, to restore after its preview. */
    data class Backup(val document: BackupDocument) : OpenedFile

    /** An OPML list of feeds, for a plugin that follows feeds to import. */
    data class Opml(val name: String, val bytes: ByteArray) : OpenedFile

    /** Neither, or not readable: [read] says why, as a chosen backup's would. */
    data class Refused(val read: BackupRead) : OpenedFile

    companion object {
        /** The plugins that import an OPML list on their settings screen, in the order offered. */
        val OPML_PLUGINS: List<String> = listOf(GroupAddSources.RSS, GroupAddSources.SUBSTACK)

        /** [bytes] of the file named [name]: an OPML list by its name or its first element, else a backup or why it is none. */
        fun of(name: String, bytes: ByteArray?): OpenedFile {
            if (bytes == null) return Refused(BackupRead.NotABackup)
            val text = bytes.decodeToString()
            if (isOpml(name, text)) {
                return if (bytes.size <= PluginDroppedFile.MAX_BYTES) Opml(name, bytes) else Refused(BackupRead.NotABackup)
            }
            return when (val read = BackupRead.of(text)) {
                is BackupRead.Ready -> Backup(read.document)
                else -> Refused(read)
            }
        }

        /** Whether [text] (of a file named [name]) is an OPML list: named `.opml`, or an XML document whose root is `<opml>`. */
        fun isOpml(name: String, text: String): Boolean {
            if (name.substringAfterLast('.', "").equals("opml", ignoreCase = true)) return true
            val head = text.take(HEAD).trimStart('﻿', ' ', '\n', '\r', '\t')
            if (!head.startsWith("<")) return false
            val root = Regex("""<(?![?!])([A-Za-z_][\w.:-]*)""").find(head)?.groupValues?.get(1)
            return root.equals("opml", ignoreCase = true)
        }

        private const val HEAD = 4_096

        /** The file at [path], read off the window's thread up to a backup's size; null once it is gone or larger. */
        suspend fun read(path: String): OpenedFile = withContext(Dispatchers.IO) {
            val file = File(path)
            val bytes = runCatching { file.takeIf { it.isFile && it.length() <= LocalBackups.MAX_BYTES }?.readBytes() }.getOrNull()
            of(file.name, bytes)
        }
    }
}

/**
 * The main window's questions about files opened with the app: a backup's
 * preview, then its restore; which plugin follows an OPML list's feeds,
 * whose settings then take it as a dropped file; or why a file cannot be
 * opened.
 */
@Composable
internal fun OpenedFileDialogs() {
    var shown by remember { mutableStateOf<OpenedFile?>(null) }
    LaunchedEffect(Unit) { OpenRequests.openedFiles.collect { path -> shown = OpenedFile.read(path) } }
    RestoreDialog((shown as? OpenedFile.Backup)?.document, LocalGraph.current, null, onClose = { shown = null })
    when (val opened = shown) {
        is OpenedFile.Backup -> Unit
        is OpenedFile.Opml -> OpmlDialog(opened) { shown = null }
        is OpenedFile.Refused -> RefusedDialog(opened.read) { shown = null }
        null -> Unit
    }
}

/** Which of the running plugins that read OPML lists follows [opml]'s feeds; with none running, why it cannot be opened. */
@Composable
private fun OpmlDialog(opml: OpenedFile.Opml, onClose: () -> Unit) {
    val plugins = LocalPlugins.current
    val running by plugins.running.collectAsStateWithLifecycle()
    val language = currentLanguage()
    val choices = OpenedFile.OPML_PLUGINS.mapNotNull { id -> running.firstOrNull { it.plugin.id == id && runCatching { it.plugin.settings != null }.getOrDefault(false) } }
    if (choices.isEmpty()) return RefusedDialog(BackupRead.NotABackup, onClose)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.settings_open_opml, opml.name)) },
        text = {
            Column {
                choices.forEach { choice ->
                    TextButton(onClick = {
                        onClose()
                        OpenedPluginFile.offer(OpenedPluginFile.of(opml.name, "text/x-opml", opml.bytes))
                        plugins.navigator.go(Destination.PluginSettings(choice.plugin.id))
                    }) { Text(runCatching { choice.plugin.title(language) }.getOrDefault(choice.plugin.id)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.settings_cancel)) } },
    )
}

@Composable
private fun RefusedDialog(read: BackupRead, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        text = { Text(backupProblem(read).orEmpty()) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.settings_ok)) } },
    )
}
