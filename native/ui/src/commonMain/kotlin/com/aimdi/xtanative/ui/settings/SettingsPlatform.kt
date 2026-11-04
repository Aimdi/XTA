package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.services.links.InstalledBrowser

/**
 * The Linux desktop, which has no browser to pick (links open in the default
 * one), no screenshot blocking and no vibration, as XTA's isDesktop.
 */
internal expect val isDesktop: Boolean

/** The apps that say they open https links; empty where the platform cannot say. */
@Composable
internal expect fun rememberInstalledBrowsers(): List<InstalledBrowser>

/** What the platform's text-to-speech offers, with the voices of [engine] (the system's for null). */
@Composable
internal expect fun rememberSpeechOptions(engine: String?): SpeechOptions?

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
