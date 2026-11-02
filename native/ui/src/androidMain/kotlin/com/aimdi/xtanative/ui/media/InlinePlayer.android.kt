package com.aimdi.xtanative.ui.media

import android.content.Context
import android.graphics.Matrix
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

@Composable
actual fun rememberInlinePlayer(): InlinePlayer? {
    val context = LocalContext.current
    val player = remember { Media3InlinePlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * Media3's player for a whole feed, drawn into a TextureView, so the tile's
 * rounded corners clip it and it scrolls with the list.
 */
private class Media3InlinePlayer(context: Context) : InlinePlayer {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        volume = 0f
        repeatMode = Player.REPEAT_MODE_ONE
    }
    private var url: String? = null
    private var view: TextureView? = null
    private var resumed = true

    private val cropOnLayout = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> crop(v as TextureView, player.videoSize) }

    init {
        player.addListener(
            object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    view?.let { crop(it, videoSize) }
                }
            },
        )
    }

    @Composable
    override fun Video(url: String, modifier: Modifier) {
        DisposableEffect(url) {
            play(url)
            onDispose { stop(url) }
        }
        AndroidView(
            factory = { context -> TextureView(context).also(this::attach) },
            onRelease = this::detach,
            modifier = modifier,
        )
    }

    private fun play(url: String) {
        if (this.url != url) {
            this.url = url
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        }
        player.playWhenReady = resumed
    }

    /** Stops [url], unless another tile has started meanwhile. */
    private fun stop(url: String) {
        if (this.url != url) return
        this.url = null
        player.stop()
        player.clearMediaItems()
    }

    private fun attach(view: TextureView) {
        this.view?.removeOnLayoutChangeListener(cropOnLayout)
        this.view = view
        view.addOnLayoutChangeListener(cropOnLayout)
        player.setVideoTextureView(view)
    }

    private fun detach(view: TextureView) {
        view.removeOnLayoutChangeListener(cropOnLayout)
        if (this.view !== view) return
        player.clearVideoTextureView(view)
        this.view = null
    }

    override fun pause() {
        resumed = false
        player.playWhenReady = false
    }

    override fun resume() {
        resumed = true
        if (url != null) player.playWhenReady = true
    }

    fun release() = player.release()

    /** Fills the tile as its poster does: scaled to cover, centred, the rest cut off. */
    private fun crop(view: TextureView, size: VideoSize) {
        if (size.width == 0 || size.height == 0 || view.width == 0 || view.height == 0) return
        val viewRatio = view.width.toFloat() / view.height
        val videoRatio = size.width * size.pixelWidthHeightRatio / size.height
        val (sx, sy) = if (videoRatio > viewRatio) videoRatio / viewRatio to 1f else 1f to viewRatio / videoRatio
        view.setTransform(Matrix().apply { setScale(sx, sy, view.width / 2f, view.height / 2f) })
    }
}
