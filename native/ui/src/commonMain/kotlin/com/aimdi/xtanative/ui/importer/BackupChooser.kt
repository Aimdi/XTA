package com.aimdi.xtanative.ui.importer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Asks for the file XTA exported, the platform's way: Android's document
 * picker, the desktop's file dialog. The returned function opens the chooser;
 * [onChosen] gets a way to read the chosen file's text, called off the main thread.
 */
@Composable
internal expect fun rememberBackupChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit

/**
 * Takes a backup dropped from the file manager onto what this modifies, as
 * if it had been chosen: [onDropped] gets a way to read its text, called off
 * the main thread. The first file of a drop is the one read. The desktop
 * only; Android's screens take no drops. Off while not [enabled].
 */
@Composable
internal expect fun Modifier.takesDroppedBackup(enabled: Boolean = true, onDropped: (read: suspend () -> String?) -> Unit): Modifier
