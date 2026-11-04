package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.aimdi.xtanative.store.settings.MediaPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whether media waits for a tap before it loads: Settings › Media's "Load media manually". */
@Composable
fun rememberLoadsManually(): Boolean {
    val settings by LocalGraph.current.settings.state.collectAsState()
    return settings?.get(MediaPrefs.loadManually) == true
}

/**
 * Whether the picture at [url] shows now, as XTA's media and grid tiles
 * decide (_media.dart, media_grid.dart): at once, unless media loads
 * manually; then once [revealed] (a tap asked for it), or when the picture
 * is already on the device, which [onCached] is told.
 */
@Composable
fun mediaShown(url: String?, revealed: Boolean, onCached: () -> Unit): Boolean {
    val manual = rememberLoadsManually()
    if (!manual || revealed || url.isNullOrEmpty()) return true
    val context = LocalPlatformContext.current
    val cached by rememberUpdatedState(onCached)
    LaunchedEffect(context, url) { if (SingletonImageLoader.get(context).holds(url)) cached() }
    return false
}

/** Whether this loader already holds [url]'s picture, in memory or on disk. */
private suspend fun ImageLoader.holds(url: String): Boolean = withContext(Dispatchers.IO) {
    runCatching { memoryCache?.keys?.any { it.key == url } == true || diskCache?.openSnapshot(url)?.use { true } == true }.getOrDefault(false)
}
