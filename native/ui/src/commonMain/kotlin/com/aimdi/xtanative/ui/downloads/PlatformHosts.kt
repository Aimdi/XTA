package com.aimdi.xtanative.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * What the platform's file dialogs need in the window, once: Android's save
 * dialog can only be opened by the activity, so the window opens it for the
 * downloads that ask. Nothing on the desktop, where the dialog opens anywhere.
 */
@Composable
internal expect fun PlatformFilesHost()

/**
 * A picker of one file, the platform's way (Android's document picker, the
 * desktop's open dialog), titled [title]: pictures alone when [images] (a PNG
 * or a JPEG), else pictures and videos. The returned function opens it and
 * answers the picked file, or null when the reader backs out.
 */
@Composable
internal expect fun rememberFilePicker(images: Boolean, title: String): suspend () -> PickedFile?

/**
 * Files dropped here from the desktop's file manager, as the picker
 * would take them ([rememberFilePicker], not only pictures): each a read
 * of its own for [onDropped], in the order dropped; other files and text
 * are left to what is under it. Nothing where the platform drops no files
 * (Android).
 */
@Composable
internal expect fun Modifier.takesDroppedFiles(enabled: Boolean, onDropped: (List<suspend () -> PickedFile?>) -> Unit): Modifier
