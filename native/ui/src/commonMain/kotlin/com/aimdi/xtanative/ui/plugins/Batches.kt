package com.aimdi.xtanative.ui.plugins

/** One file of a batch a plugin saves through the app's downloads ([PluginHost.downloadAll], [PluginHost.queueDownloads]): where it is fetched from, and the name it is saved under. */
data class PluginDownload(val url: String, val fileName: String)

/** How a batch was queued ([PluginHost.queueDownloads]): [queued] files on their way, [skipped] left out as the downloads had them already. */
data class PluginQueued(val queued: Int, val skipped: Int)
