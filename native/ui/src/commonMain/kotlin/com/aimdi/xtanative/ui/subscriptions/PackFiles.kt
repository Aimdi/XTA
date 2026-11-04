package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.runtime.Composable

/**
 * Asks for a group pack someone exported, the platform's way: Android's
 * document picker, the desktop's file dialog. The returned function opens the
 * chooser; [onChosen] gets a way to read the chosen file's text, called off
 * the main thread.
 */
@Composable
internal expect fun rememberPackChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit

/**
 * Saves a pack as a file the reader can hand on: Android's save dialog (the
 * Files app, Drive, …), the desktop's save dialog, opened on Downloads. The
 * returned function takes the file's name and its text.
 */
@Composable
internal expect fun rememberPackSaver(): (fileName: String, text: String) -> Unit
