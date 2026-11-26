package com.aimdi.xtanative.plugins.threads

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.threads.ThreadsMusic
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.media.rememberContinuityPlayers
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.onContextClick
import com.aimdi.xtanative.ui.plugins.pluginEdgeColor
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.isActive

/**
 * A post's song as threads.com draws it: its cover, rounded, with a black
 * record peeking out behind it (turning slowly while the song plays, unless
 * the reader asked for less motion), the title in bold over up to four
 * lines, the artists in grey, and a round play button. The button is there
 * only where Meta gave a file and the platform has a player
 * ([ThreadsSongRules.offersPlay]); a tap plays the preview through
 * [ThreadsApp.songs], which pauses any other song and the list's video. The
 * song stops when its card leaves the screen, and pauses when the window
 * does. A long press opens the song's page, where Meta names one. Nothing of
 * it loads before the reader taps play.
 */
@Composable
internal fun ThreadsSongCard(app: ThreadsApp, post: ThreadsPost, focal: Boolean, modifier: Modifier = Modifier) {
    val music = post.facts.music ?: return
    val key = post.id
    val players = rememberContinuityPlayers()
    val turn by app.songs.turn.collectAsState()
    val playing = turn.plays(key)
    val offers = ThreadsSongRules.offersPlay(music, players != null)
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (!focused) app.songs.pause(key) }
    DisposableEffect(key) { onDispose { app.songs.stop(key) } }
    val press = { players?.let { app.songs.press(key, music, it) } ?: Unit }
    val openPage = music.pageUrl?.let { url -> { app.host.openLink(url) } }
    SongCard(
        music,
        SongCardState(playing = playing, offers = offers, failed = turn.failed(key)),
        focal,
        modifier.onGloballyPositioned { if (it.boundsInWindow().height <= 0f) app.songs.pause(key) },
        press,
        openPage,
    )
}

/** What a song's card shows besides its song: whether it plays, whether it has a button, and whether its file failed. */
internal data class SongCardState(val playing: Boolean = false, val offers: Boolean = false, val failed: Boolean = false)

/** The song's card as drawn ([ThreadsSongCard] keeps its state): a tap is [onPress] where it [SongCardState.offers] play, a long press [onPage]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongCard(music: ThreadsMusic, state: SongCardState, focal: Boolean, modifier: Modifier, onPress: () -> Unit, onPage: (() -> Unit)?) {
    val label = ThreadsSongRules.label(music, state.playing, currentLanguage())
    val unavailable = pluginString(Strings.song_unavailable)
    val said = listOfNotNull(
        pluginString(Strings.music) + ": " + music.line,
        pluginString(Strings.song_explicit).takeIf { music.explicit },
        unavailable.takeIf { state.failed },
    ).joinToString(", ")
    val cover = if (focal) 96.dp else 72.dp
    val shape = RoundedCornerShape(14.dp)
    val actions = when {
        state.offers -> Modifier.then(onPage?.let { Modifier.onContextClick(it) } ?: Modifier).combinedClickable(
            onClickLabel = label,
            onClick = onPress,
            onLongClickLabel = onPage?.let { pluginString(Strings.song_open_page) },
            onLongClick = onPage,
        )
        onPage != null -> Modifier.combinedClickable(onClickLabel = pluginString(Strings.song_open_page), onClick = onPage)
        else -> Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, pluginEdgeColor(), shape)
            .then(actions)
            .semantics(mergeDescendants = true) { contentDescription = said }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArt(music, cover, spinning = state.playing && !LocalReducedMotion.current)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                music.title,
                style = if (focal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (music.explicit) ExplicitMark()
                music.artist?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.failed) {
                Text(unavailable, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.offers) {
            Spacer(Modifier.width(8.dp))
            PlayButton(state.playing, label, onPress)
        }
    }
}

/** The cover over its record: the record behind, to the end, peeking out by a third of the cover. */
@Composable
private fun SongArt(music: ThreadsMusic, cover: Dp, spinning: Boolean) {
    val record = cover * 0.92f
    val peek = cover * 0.34f
    Box(Modifier.size(width = cover + peek, height = cover), contentAlignment = Alignment.CenterStart) {
        Vinyl(music, record, spinning, Modifier.offset(x = cover + peek - record))
        val art = Modifier.size(cover).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        if (music.cover != null) {
            AsyncImage(music.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = art)
        } else {
            Box(art, contentAlignment = Alignment.Center) {
                Icon(ThreadsIcons.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(cover / 3))
            }
        }
    }
}

/**
 * A black record drawn with shapes: its grooves, a sheen that shows it
 * turning, and the cover as its label. It turns one round in six seconds
 * while [spinning], and stays where it stopped.
 */
@Composable
private fun Vinyl(music: ThreadsMusic, size: Dp, spinning: Boolean, modifier: Modifier) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        while (spinning && isActive) {
            angle.animateTo(angle.value + 360f, tween(ROUND_MS, easing = LinearEasing))
            angle.snapTo(angle.value % 360f)
        }
    }
    Box(modifier.size(size).graphicsLayer { rotationZ = angle.value }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2
            drawCircle(RecordBlack, radius)
            // A faint rim, so the record stands out on a black background too.
            drawCircle(Groove, radius - 0.5.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
            for (step in 1..GROOVES) drawCircle(Groove, radius * (0.42f + step * 0.5f / GROOVES), style = Stroke(width = 0.6.dp.toPx()))
            drawArc(Sheen, -60f, 40f, useCenter = false, topLeft = Offset(radius * 0.18f, radius * 0.18f), size = androidx.compose.ui.geometry.Size(radius * 1.64f, radius * 1.64f), style = Stroke(width = radius * 0.22f))
            drawArc(Sheen, 120f, 40f, useCenter = false, topLeft = Offset(radius * 0.18f, radius * 0.18f), size = androidx.compose.ui.geometry.Size(radius * 1.64f, radius * 1.64f), style = Stroke(width = radius * 0.22f))
        }
        val label = Modifier.size(size * 0.36f).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
        if (music.imageUrl != null) AsyncImage(music.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = label) else Box(label)
        Box(Modifier.size(size * 0.06f).clip(CircleShape).background(RecordBlack))
    }
}

/** The round dark button, 48dp, whose words a screen reader says ([ThreadsSongRules.label]). */
@Composable
private fun PlayButton(playing: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) ThreadsIcons.Pause else ThreadsIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(26.dp))
    }
}

/** The small boxed E that marks an explicit song, as music apps mark one; its word is in the card's description. */
@Composable
private fun ExplicitMark() {
    Box(
        Modifier.padding(end = 6.dp).size(16.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(pluginString(Strings.song_explicit_mark), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface, maxLines = 1)
    }
}

private const val ROUND_MS = 6_000
private const val GROOVES = 7
private val RecordBlack = Color(0xFF121212)
private val Groove = Color(0x33FFFFFF)
private val Sheen = Color(0x1FFFFFFF)
