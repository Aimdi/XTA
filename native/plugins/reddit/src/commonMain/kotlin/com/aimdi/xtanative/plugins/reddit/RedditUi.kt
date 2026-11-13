package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.reddit.redditTextParts
import com.aimdi.xtanative.ui.plugins.LinkedText
import com.aimdi.xtanative.ui.plugins.TextRun
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberShareText

/** The sentence a failed read leads with, in XTA's words. */
@Composable
internal fun troubleText(trouble: RedditTrouble): String = pluginString(
    when (trouble) {
        RedditTrouble.CLIENT_ID -> Strings.error_client_id
        RedditTrouble.NOT_CONFIGURED -> Strings.not_configured
        RedditTrouble.BLOCKED -> Strings.error_blocked
        RedditTrouble.NOT_FOUND -> Strings.error_not_found
        RedditTrouble.RATE_LIMITED -> Strings.error_rate_limited
        RedditTrouble.RESPONSE -> Strings.error_response
        RedditTrouble.NETWORK -> Strings.error_network
    },
)

/** A failed read as the shared paged lists keep it. */
internal fun troubleOf(problem: Problem): RedditTrouble = when (problem) {
    Problem.RateLimited -> RedditTrouble.RATE_LIMITED
    Problem.Offline -> RedditTrouble.NETWORK
    is Problem.Refused -> RedditTrouble.ofStatus(problem.code)
    else -> RedditTrouble.RESPONSE
}

/** What happened underneath, so a refusal, a timeout and a reshaped page do not all read the same. */
internal fun detailOf(problem: Problem): String? = (problem as? Problem.Refused)?.let { "HTTP ${it.code}" }

/** A read that brought nothing: why, what happened underneath, and a way to try again; [compact] as one row under what is shown. */
@Composable
internal fun RedditProblemView(trouble: RedditTrouble, detail: String?, onRetry: () -> Unit, compact: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 12.dp else 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text(troubleText(trouble), style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge, color = muted, textAlign = TextAlign.Center)
        if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = muted, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text(pluginString(Strings.retry)) }
    }
}

@Composable
internal fun RedditProblemView(error: Throwable, onRetry: () -> Unit, compact: Boolean = false) =
    RedditProblemView(RedditTrouble.of(error), error.message, onRetry, compact)

/** Nothing to read, which is not something gone wrong: an icon, why, and what to do about it. */
@Composable
internal fun RedditEmpty(icon: ImageVector, message: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** The end of a list, said out loud: a list that only stopped left the reader waiting for a page that was never coming. */
@Composable
internal fun EndOfList() {
    Text(
        pluginString(Strings.end_of_list),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
    )
}

/** The next page on a press, for a reader the list's own reading ahead has not reached. */
@Composable
internal fun LoadMoreButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        OutlinedButton(onClick = onClick) { Text(pluginString(Strings.load_more)) }
    }
}

/** A section's name above its rows, in the accent. */
@Composable
internal fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/**
 * A title, self text or comment with its links tappable and shortened
 * (XTA's `RedditRichText`): a link opens as the app opens links, a Reddit
 * one on this plugin's own screens.
 */
@Composable
internal fun RedditText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium, maxLines: Int = Int.MAX_VALUE) {
    val runs = remember(text) {
        redditTextParts(text).map { part -> part.url?.let { TextRun.Link(part.text, it) } ?: TextRun.Plain(part.text) }
    }
    LinkedText(runs, onMention = {}, onTag = {}, modifier = modifier, style = style, maxLines = maxLines)
}

/**
 * The pixel face of an account or a community without a picture of its own
 * ([RedditFaces]); a deleted account, with no name, gets a plain tile.
 * [round] is how a thread marks the person who wrote the post.
 */
@Composable
internal fun FaceAvatar(name: String?, size: Dp, modifier: Modifier = Modifier, round: Boolean = false) {
    val shape = if (round) CircleShape else RoundedCornerShape(size / 4)
    val face = remember(name) { name?.takeIf { it.isNotEmpty() }?.let(RedditFaces::of) }
    if (face == null) {
        Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        return
    }
    Canvas(modifier.size(size).clip(shape)) {
        drawRect(Color(face.background))
        val cell = minOf(this.size.width, this.size.height) / 8f
        val left = (this.size.width - cell * 8) / 2
        val top = (this.size.height - cell * 8) / 2
        face.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, pixel ->
                val colour = when (pixel) {
                    '#' -> face.ink
                    'o' -> face.accent
                    else -> null
                }
                // A hair of overlap: exact edges leave seams where a cell lands on a fraction of a pixel.
                if (colour != null) drawRect(Color(colour), Offset(left + x * cell, top + y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/**
 * A community's picture as Reddit's own apps show it, contained on a filled
 * rounded square; its face while it loads, and for good when it has none.
 * [known] is a picture already in hand: null asks for it.
 */
@Composable
internal fun SubredditAvatar(app: RedditApp, subreddit: String, size: Dp, modifier: Modifier = Modifier, known: String? = null, onClick: (() -> Unit)? = null) {
    var icon by remember(subreddit, known) { mutableStateOf(known) }
    var loaded by remember(icon) { mutableStateOf(false) }
    LaunchedEffect(subreddit, known) { if (known == null) icon = app.icons.icon(subreddit) }
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        if (!loaded) FaceAvatar("r/$subreddit", size)
        icon?.takeIf { it.isNotEmpty() }?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The content padding of a body in one of the plugin's own screens, below its bar: clear of the system's bars at the bottom. */
internal fun bottomOnly(padding: PaddingValues): PaddingValues = PaddingValues(bottom = padding.calculateBottomPadding())

/** A colour readable as text, or as a line at [minRatio], on the page it is drawn on. */
@Composable
internal fun readable(colour: Color, minRatio: Double = 4.5): Color =
    Color(RedditContrast.ensure(colour.toArgb(), MaterialTheme.colorScheme.background.toArgb(), minRatio))

/** Shares a link where the system can, and copies it where it cannot (the desktop). */
@Composable
internal fun rememberShareLink(): (String) -> Unit {
    val share = rememberShareText()
    val clipboard = LocalClipboardManager.current
    return remember(share, clipboard) { { url -> if (!share(url)) clipboard.setText(AnnotatedString(url)) } }
}
