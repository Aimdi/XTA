package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.aimdi.xtanative.networks.pixiv.PixivFrameBytes
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.store.downloads.OkHttpMediaHttp
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.PluginProgress
import com.aimdi.xtanative.ui.plugins.pluginString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Where an ugoira stands in its player. */
private enum class UgoiraPhase { IDLE, LOADING, PLAYING, PAUSED, FAILED }

/**
 * An ugoira's still picture with a play button over it: a tap fetches the
 * frame archive from the reader's image server (with Pixiv's Referer) and
 * plays the frames in place, each for its own delay, decoding one ahead so a
 * long animation never sits in memory decoded whole. It stops when the page
 * leaves the screen.
 */
@Composable
internal fun UgoiraPlayer(illust: PixivIllust, modifier: Modifier = Modifier) {
    val runtime = LocalPixiv.current
    val context = LocalPlatformContext.current
    var phase by remember(illust.id) { mutableStateOf(UgoiraPhase.IDLE) }
    var frames by remember(illust.id) { mutableStateOf<List<PixivFrameBytes>?>(null) }
    var frame by remember(illust.id) { mutableStateOf<Painter?>(null) }
    LaunchedEffect(illust.id, phase == UgoiraPhase.LOADING) {
        if (phase != UgoiraPhase.LOADING) return@LaunchedEffect
        frames = runCatching { ugoiraFrames(runtime, illust) }.getOrNull()?.takeIf { it.isNotEmpty() }
        phase = if (frames == null) UgoiraPhase.FAILED else UgoiraPhase.PLAYING
    }
    LaunchedEffect(illust.id, phase == UgoiraPhase.PLAYING) {
        val playing = frames?.takeIf { phase == UgoiraPhase.PLAYING } ?: return@LaunchedEffect
        play(context, playing) { frame = it }
    }
    Box(modifier) {
        frame?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().background(Color.Black)) }
        UgoiraControl(phase) { phase = if (phase == UgoiraPhase.PLAYING) UgoiraPhase.PAUSED else if (frames == null) UgoiraPhase.LOADING else UgoiraPhase.PLAYING }
    }
}

@Composable
private fun BoxScope.UgoiraControl(phase: UgoiraPhase, onTap: () -> Unit) {
    val scrim = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
    when (phase) {
        UgoiraPhase.LOADING -> Box(Modifier.align(Alignment.Center).size(64.dp).then(scrim), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
        }
        UgoiraPhase.PLAYING -> IconButton(onClick = onTap, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).then(scrim)) {
            Icon(PixivIcons.Pause, pluginString(Strings.ugoira_pause), tint = Color.White)
        }
        else -> IconButton(onClick = onTap, modifier = Modifier.align(Alignment.Center).size(64.dp).then(scrim)) {
            val failed = phase == UgoiraPhase.FAILED
            Icon(
                if (failed) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                pluginString(if (failed) Strings.retry else Strings.ugoira_play),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** Shows each frame for its delay, round and round, decoding the next while one shows. */
private suspend fun play(context: PlatformContext, frames: List<PixivFrameBytes>, show: (Painter) -> Unit) {
    var index = 0
    var next = decode(context, frames[0])
    while (true) {
        next?.let(show)
        val shownFor = frames[index].delay.inWholeMilliseconds
        val started = System.currentTimeMillis()
        index = (index + 1) % frames.size
        next = decode(context, frames[index])
        delay((shownFor - (System.currentTimeMillis() - started)).coerceAtLeast(0))
    }
}

/** One frame decoded by the app's image loader, kept out of its caches; null for a frame that will not decode. */
private suspend fun decode(context: PlatformContext, frame: PixivFrameBytes): Painter? {
    val request = ImageRequest.Builder(context).data(frame.bytes).memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
    return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.asPainter(context)
}

/** The ugoira's frames: its metadata, then its archive from the reader's image server, read whole (at most [MAX_ARCHIVE]). */
internal suspend fun ugoiraFrames(runtime: PixivRuntime, illust: PixivIllust): List<PixivFrameBytes> {
    val meta = runtime.client.ugoiraMetadata(illust.id)
    val url = meta.archiveUrl(runtime.settings[PixivPrefs.imageHost])
    val archive = withContext(Dispatchers.IO) {
        OkHttpMediaHttp().get(url, PixivImages.HEADERS).use { response ->
            if (response.code !in 200..299) throw IOException("ugoira archive: HTTP ${response.code}")
            if ((response.contentLength ?: 0) > MAX_ARCHIVE) throw IOException("ugoira archive too large")
            readCapped(response.body) { ensureActive() }
        }
    }
    return withContext(Dispatchers.Default) { meta.framesIn(archive) }
}

private const val MAX_ARCHIVE = 64 * 1024 * 1024

/** [input] read whole, refusing more than [MAX_ARCHIVE]; [check] runs between reads, so a cancelled export stops on its way. */
private fun readCapped(input: InputStream, check: () -> Unit = {}): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        check()
        val read = input.read(buffer)
        if (read < 0) return out.toByteArray()
        out.write(buffer, 0, read)
        if (out.size() > MAX_ARCHIVE) throw IOException("ugoira archive too large")
    }
}

/** An ugoira saved as a looping GIF (pixiv_ugoira_export.dart): its frames fetched, then encoded a frame at a time, then saved where downloads go. */
internal object PixivUgoiraExport {
    const val GIF_TYPE = "image/gif"

    /** Says each step through [say]: fetching, the frame being encoded with XTA's note that it can take a minute, then saving, which no Cancel stops. Whether the file was saved. */
    suspend fun gif(runtime: PixivRuntime, illust: PixivIllust, language: String, say: (PluginProgress) -> Unit): Boolean {
        val frames = ugoiraFrames(runtime, illust)
        say(encoding(language, 0, frames.size))
        val gif = withContext(Dispatchers.Default) {
            PixivGifWriter.encode(frames, decode = { decodePixels(it, atLeastWidth = null) }) { done -> say(encoding(language, done, frames.size)) }
        }
        say(PluginProgress(Strings.ugoira_saving.get(language), cancellable = false))
        val naming = PixivSaveNaming.of(runtime.settings.now)
        return runtime.host.saveFileAndWait(gif, naming.workName(illust, ".gif"), GIF_TYPE, naming.subfolder(illust))
    }

    /** "Encoding frame 3 of 40…" for the frame after the [done] ones, its ring filled that far. */
    private fun encoding(language: String, done: Int, total: Int) = PluginProgress(
        Strings.ugoira_encoding.get(language, (done + 1).coerceIn(1, maxOf(total, 1)), total),
        fraction = if (total > 0) done.toFloat() / total else null,
        note = Strings.ugoira_slow.get(language),
    )
}
