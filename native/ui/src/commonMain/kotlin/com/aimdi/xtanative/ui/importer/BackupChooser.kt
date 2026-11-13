package com.aimdi.xtanative.ui.importer

import androidx.compose.runtime.Composable

/**
 * Asks for the file XTA exported, the platform's way: Android's document
 * picker, the desktop's file dialog. The returned function opens the chooser;
 * [onChosen] gets a way to read the chosen file's text, called off the main thread.
 */
@Composable
internal expect fun rememberBackupChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit
