package com.aimdi.xtanative.ui.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.io.File

/**
 * What a video player reads a video through: a file over the web through
 * the app's video cache on disk ([VideoCaching], [VideoDiskCache]), so a tile
 * scrolled back to, a GIF's next loop and the same video full screen read
 * from the phone; anything else (a live playlist, a file on the phone)
 * straight, as before. A cache that cannot be opened, or a kept piece that
 * cannot be read, falls back to the network.
 */
internal class VideoSources(context: Context) {
    private val plain = DefaultMediaSourceFactory(context)
    private val cached = VideoDiskCache.of(context)?.let { cache ->
        DefaultMediaSourceFactory(
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
        )
    }

    fun of(url: String): MediaSource {
        val factory = cached?.takeIf { VideoCaching.caches(url) } ?: plain
        return factory.createMediaSource(MediaItem.fromUri(url))
    }
}

/**
 * The process's one video cache, in the app's cache folder (which Android
 * may empty, and which goes with the app's cache): Media3 allows one cache
 * a folder, so every player shares it. Null when it cannot be opened.
 */
internal object VideoDiskCache {
    private var cache: SimpleCache? = null
    private var failed = false

    @Synchronized
    fun of(context: Context): SimpleCache? {
        if (cache == null && !failed) {
            val app = context.applicationContext
            cache = runCatching {
                SimpleCache(File(app.cacheDir, FOLDER), LeastRecentlyUsedCacheEvictor(VideoCaching.MAX_BYTES), StandaloneDatabaseProvider(app))
            }.onFailure { println("XTA Native: video: no video cache: $it") }.getOrNull()
            failed = cache == null
        }
        return cache
    }

    private const val FOLDER = "xta-video"
}
