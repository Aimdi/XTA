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


/** The settings search's key for Start at login, which is no stored setting: the desktop's autostart entry is. */
internal const val START_AT_LOGIN_KEY = "desktop.start_at_login"

/** Whether the app starts when the reader logs in, the platform's way (the desktop's autostart entry). */
internal interface LoginStart {
    /** Whether it does now, as the platform has it (the reader may have changed it elsewhere). */
    fun isOn(): Boolean

    /** Makes it [on]; false when that failed. */
    fun set(on: Boolean): Boolean
}

/** The desktop's [LoginStart]; null where there is none (Android, or a desktop build not started from its launcher). */
@Composable
internal expect fun rememberLoginStart(): LoginStart?
