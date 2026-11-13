package com.aimdi.xtanative.pluginhost

/**
 * Why an install, update, load or uninstall did not happen, for the store to
 * say in the reader's language. A `detail` is for the log, not the screen.
 */
sealed interface PluginFailure {
    /** The catalogue does not offer it on this platform now: held back, private while hidden, or without a file for it. */
    data object NotOffered : PluginFailure

    /** It is not installed, so there is nothing to load. */
    data object NotInstalled : PluginFailure

    /** Built for plugin API [apiVersion], newer than this app's: "needs a newer XTA Native". */
    data class NeedsNewerApp(val apiVersion: Int) : PluginFailure

    /** Built for plugin API [apiVersion], older than any this app still loads: the plugin needs an update. */
    data class NeedsNewerPlugin(val apiVersion: Int) : PluginFailure

    /** The file could not be fetched: [code] is the server's answer, null when none came or the transfer broke off. */
    data class DownloadFailed(val code: Int?, val detail: String?) : PluginFailure

    /** The file is [actual] bytes where the catalogue says [expected]. */
    data class WrongSize(val expected: Long, val actual: Long) : PluginFailure

    /** The file's SHA-256 is not the one the catalogue gives. */
    data object WrongChecksum : PluginFailure

    /** The file is not signed with this app's key. */
    data class BadSignature(val detail: String) : PluginFailure

    /** The file's xta-plugin.json is missing or unreadable, or names another plugin or version than the catalogue. */
    data class BadManifest(val detail: String) : PluginFailure

    /** The installed file is gone, or no longer the one that was installed. */
    data object Damaged : PluginFailure

    /** Its entry class could not be loaded or made. */
    data class LoadFailed(val detail: String) : PluginFailure

    /** The device would not keep or remove its files, or the app could not erase what it stored. */
    data class StorageFailed(val detail: String) : PluginFailure
}
