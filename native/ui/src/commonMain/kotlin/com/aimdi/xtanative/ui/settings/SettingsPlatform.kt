package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable

/**
 * The Linux desktop, which has no screenshot blocking and no vibration, as
 * XTA's isDesktop.
 */
internal expect val isDesktop: Boolean

/**
 * Asks where to keep a backup, the platform's way (Android's document
 * picker, the desktop's save dialog), then writes it there. The returned
 * function takes the file's suggested name and its text; [onSaved] gets the
 * name it went under, or null when the reader backed out or it failed.
 */
@Composable
internal expect fun rememberBackupSaver(onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit

/** Asks for the folder downloads go to; [onPicked] gets what is stored (a document tree, or a path) and its name. */
@Composable
internal expect fun rememberFolderPicker(onPicked: (stored: String, name: String) -> Unit): () -> Unit

/** The screens below in [language] (`language` and `region`, as [SettingsChoices.localeOf] gives them), or the system's for null. */
@Composable
expect fun ProvideAppLanguage(language: Pair<String, String>?, content: @Composable () -> Unit)
