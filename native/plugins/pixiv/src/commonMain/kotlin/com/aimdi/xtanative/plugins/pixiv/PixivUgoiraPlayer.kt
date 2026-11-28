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
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.plugins.PluginProgress
import com.aimdi.xtanative.ui.plugins.pluginString
import java.io.IOException
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Where an ugoira stands in its player. */
private enum class UgoiraPhase { IDLE, LOADING, PLAYING, PAUSED, FAILED }

/**
 * An ugoira's still picture with a play button over it: a tap fetches the
 * frame archive from the reader's image server (with Pixiv's Referer) and
 * plays the frames in place, each for its own delay. Frames are decoded once
 * and kept when the animation fits in [FRAME_CACHE_BYTES], else one ahead
 * each loop, so a long animation never sits in memory decoded whole. It
 * stops when the page leaves the screen.
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
        frames = catchingFailures { ugoiraFrames(runtime, illust) }.getOrNull()?.takeIf { it.isNotEmpty() }
        phase = if (frames == null) UgoiraPhase.FAILED else UgoiraPhase.PLAYING
    }
    val cache = remember(frames, context) { frames?.let { all -> UgoiraFrameCache(all.size) { decode(context, all[it]) } } }
    LaunchedEffect(illust.id, phase == UgoiraPhase.PLAYING) {
        val playing = frames?.takeIf { phase == UgoiraPhase.PLAYING } ?: return@LaunchedEffect
        play(playing.map { it.delay }, cache ?: return@LaunchedEffect) { frame = it }
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
        // The tip's box takes the place in the player; the button keeps its look inside it.
        UgoiraPhase.PLAYING -> Box(Modifier.align(Alignment.BottomEnd)) {
            IconTip(pluginString(Strings.ugoira_pause)) {
                IconButton(onClick = onTap, modifier = Modifier.padding(8.dp).then(scrim)) {
                    Icon(PixivIcons.Pause, pluginString(Strings.ugoira_pause), tint = Color.White)
                }
            }
        }
        else -> Box(Modifier.align(Alignment.Center)) {
            val failed = phase == UgoiraPhase.FAILED
            val label = pluginString(if (failed) Strings.retry else Strings.ugoira_play)
            IconTip(label) {
                IconButton(onClick = onTap, modifier = Modifier.size(64.dp).then(scrim)) {
                    Icon(if (failed) Icons.Filled.Refresh else Icons.Filled.PlayArrow, label, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/**
 * Shows each frame for its delay, round and round, decoding the next while one
 * shows; [cache] keeps what it decodes when the whole animation fits, so later
 * loops decode nothing. [wait] is how a frame's time passes.
 */
internal suspend fun <T : Any> play(
    delays: List<Duration>,
    cache: UgoiraFrameCache<T>,
    wait: suspend (Long) -> Unit = { delay(it) },
    show: (T) -> Unit,
) {
    var index = 0
    var next = cache.frame(0)
    while (true) {
        next?.let(show)
        val shownFor = delays[index].inWholeMilliseconds
        val started = System.currentTimeMillis()
        index = (index + 1) % delays.size
        next = cache.frame(index)
        wait((shownFor - (System.currentTimeMillis() - started)).coerceAtLeast(0))
    }
}

/** A decoded frame and the memory it takes. */
internal class DecodedFrame<T : Any>(val value: T, val bytes: Long)

/**
 * An ugoira's frames as [decode] makes them, all kept once decoded when the
 * first one shows they fit in [budget] together, else decoded again each
 * loop, one ahead, as a long animation needs. A frame that would not decode
 * is asked for again next time.
 */
internal class UgoiraFrameCache<T : Any>(private val frames: Int, private val budget: Long = FRAME_CACHE_BYTES, private val decode: suspend (Int) -> DecodedFrame<T>?) {
    private val kept = HashMap<Int, T>()
    private var keeps: Boolean? = null

    suspend fun frame(index: Int): T? {
        kept[index]?.let { return it }
        val decoded = decode(index) ?: return null
        val keep = keeps ?: (decoded.bytes * frames <= budget).also { keeps = it }
        if (keep) kept[index] = decoded.value
        return decoded.value
    }
}

/** The most an ugoira's decoded frames may take to be kept between loops (a 600×600 one: about 33 frames). */
internal const val FRAME_CACHE_BYTES = 48L * 1024 * 1024

/** One frame decoded by the app's image loader, kept out of its caches; null for a frame that will not decode. */
private suspend fun decode(context: PlatformContext, frame: PixivFrameBytes): DecodedFrame<Painter>? {
    val request = ImageRequest.Builder(context).data(frame.bytes).memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
    val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image ?: return null
    val bytes = image.size.takeIf { it > 0 } ?: (image.width.toLong() * image.height * 4)
    return DecodedFrame(image.asPainter(context), bytes)
}

/** The ugoira's frames: its metadata, then its archive from the reader's image server, read entry by entry (at most [MAX_ARCHIVE]). */
internal suspend fun ugoiraFrames(runtime: PixivRuntime, illust: PixivIllust): List<PixivFrameBytes> {
    val meta = runtime.client.ugoiraMetadata(illust.id)
    val url = meta.archiveUrl(runtime.settings[PixivPrefs.imageHost])
    return withContext(Dispatchers.IO) {
        OkHttpMediaHttp().get(url, PixivImages.HEADERS).use { response ->
            if (response.code !in 200..299) throw IOException("ugoira archive: HTTP ${response.code}")
            if ((response.contentLength ?: 0) > MAX_ARCHIVE) throw IOException("ugoira archive too large")
            meta.framesFrom(response.body, MAX_ARCHIVE) { ensureActive() }
        }
    }
}

private const val MAX_ARCHIVE = 64L * 1024 * 1024

/** An animation format an ugoira is saved in: its file's type and ending, and how its frames are encoded. */
internal enum class PixivUgoiraFormat(val type: String, val ending: String) {
    GIF("image/gif", ".gif"),
    // An APNG is a PNG to anything that reads only its first frame, and is typed as one where files are kept.
    APNG("image/png", ".png"),
    ;

    suspend fun encode(frames: List<PixivFrameBytes>, onFrame: (done: Int) -> Unit): ByteArray = when (this) {
        GIF -> PixivGifWriter.encode(frames, decode = { decodePixels(it, atLeastWidth = null) }, onFrame)
        APNG -> PixivApngWriter.encode(frames, decode = { decodePixels(it, atLeastWidth = null) }, onFrame)
    }
}

/** An ugoira saved as a looping GIF (pixiv_ugoira_export.dart) or APNG: its frames fetched, then encoded a frame at a time, then saved where downloads go. */
internal object PixivUgoiraExport {
    /** Says each step through [say]: fetching, the frame being encoded with XTA's note that it can take a minute, then saving, which no Cancel stops. Whether the file was saved. */
    suspend fun save(runtime: PixivRuntime, illust: PixivIllust, format: PixivUgoiraFormat, language: String, say: (PluginProgress) -> Unit): Boolean {
        val frames = ugoiraFrames(runtime, illust)
        say(encoding(language, 0, frames.size))
        val file = withContext(Dispatchers.Default) { format.encode(frames) { done -> say(encoding(language, done, frames.size)) } }
        say(PluginProgress(Strings.ugoira_saving.get(language), cancellable = false))
        val naming = PixivSaveNaming.of(runtime.settings.now)
        return runtime.host.saveFileAndWait(file, naming.workName(illust, format.ending), format.type, naming.subfolder(illust))
    }

    /** "Encoding frame 3 of 40…" for the frame after the [done] ones, its ring filled that far. */
    private fun encoding(language: String, done: Int, total: Int) = PluginProgress(
        Strings.ugoira_encoding.get(language, (done + 1).coerceIn(1, maxOf(total, 1)), total),
        fraction = if (total > 0) done.toFloat() / total else null,
        note = Strings.ugoira_slow.get(language),
    )
}
