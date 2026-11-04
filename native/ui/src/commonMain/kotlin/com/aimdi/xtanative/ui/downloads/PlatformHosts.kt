package com.aimdi.xtanative.ui.downloads

import androidx.compose.runtime.Composable

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
