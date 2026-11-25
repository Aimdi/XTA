package com.aimdi.xtanative.ui.media

import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberVideoPlayers(): VideoPlayers? {
    val context = LocalContext.current
    return remember(context) { VideoPlayers { options -> ExoVideoPlayer(context, options) } }
}

/** Data Saver restricts this app on a metered network: read once where a tile is first drawn. */
@Composable
internal actual fun rememberDataSaver(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity != null && connectivity.isActiveNetworkMetered &&
            connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }
}

/** Media3's cache on disk ([VideoDiskCache]): its size, and every video it keeps dropped; null when it could not be opened. */
@Composable
internal actual fun rememberVideoCache(): VideoCacheControl? {
    val context = LocalContext.current
    return remember(context) {
        VideoDiskCache.of(context)?.let { cache ->
            object : VideoCacheControl {
                override fun size(): Long = cache.cacheSpace

                override fun clear() {
                    cache.keys.toList().forEach { key -> runCatching { cache.removeResource(key) } }
                }
            }
        }
    }
}
