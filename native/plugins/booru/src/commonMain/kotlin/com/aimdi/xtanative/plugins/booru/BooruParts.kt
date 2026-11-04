package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.plugins.pluginString

/** What the reader chose, once the app's settings are read. */
@Composable
internal fun Bound(app: BooruApp, content: @Composable (Settings) -> Unit) {
    val settings by app.settings.collectAsState()
    val loaded = settings
    if (loaded == null) LoadingRow() else content(loaded)
}

/** A booru's picture at [url], or a broken picture when it has none or it does not load. */
@Composable
internal fun BooruImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, description: String? = null) {
    var failed by remember(url) { mutableStateOf(url.isEmpty()) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = description,
                contentScale = contentScale,
                onState = { failed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (failed) Icon(BooruIcons.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** In place of a feed: [message] under [icon], and what can be done about it. */
@Composable
internal fun EmptyPane(icon: ImageVector, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** A dense line above a feed: [text] beside [icon], and a button at its end when there is [actionLabel]. */
@Composable
internal fun NoticeLine(icon: ImageVector, text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** A section's heading, in the theme's accent, as settings and the search page title their parts. */
@Composable
internal fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).then(modifier).padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * A compact tag chip tinted with its kind's colour (plugin_tag_chip.dart):
 * the name, then a quieter [detail] such as a post count, a [marker] before
 * it (followed, hidden). The chip stays small; its touch target keeps 48 dp.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TagChip(
    label: String,
    category: BooruTagCategory?,
    onClick: () -> Unit,
    detail: String? = null,
    marker: ImageVector? = null,
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    val color = tagColor(category)
    val dark = darkTheme()
    val text = color ?: MaterialTheme.colorScheme.onSurface
    val fill = color?.copy(alpha = if (dark) 0.16f else 0.09f) ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val border = color?.copy(alpha = if (dark) 0.5f else 0.4f) ?: MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(10.dp)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.defaultMinSize(minHeight = 48.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(shape)
                .background(fill)
                .border(1.dp, border, shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = longClickLabel)
                .let { if (description != null) it.semantics { contentDescription = description } else it }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (marker != null) {
                Icon(marker, contentDescription = null, tint = text, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(chipText(label, detail, text, muted), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun chipText(label: String, detail: String?, color: Color, muted: Color): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(label) }
        if (detail != null) withStyle(SpanStyle(color = muted)) { append("  $detail") }
    }

/** The foot of a Booru screen, where what the plugin tells the reader shows. */
@Composable
internal fun BooruMessages(app: BooruApp, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(app) { app.messages.collect { host.showSnackbar(it) } }
    SnackbarHost(host, modifier)
}

/** The ways out of the app XTA's Booru takes: the system's browser, the share sheet, the clipboard. */
internal class BooruOutside(private val open: (String) -> Unit, private val share: (String) -> Boolean, private val copy: (String) -> Unit) {
    /** Opens [url] outside the app, as XTA's openBooruLink launches it in another application. */
    fun open(url: String) {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) open.invoke(trimmed)
    }

    /** The share sheet for [text], or the clipboard where the platform has none. */
    fun share(text: String, copied: () -> Unit) {
        if (!share.invoke(text)) copy(text, copied)
    }

    fun copy(text: String, copied: () -> Unit) {
        copy.invoke(text)
        copied()
    }
}

@Composable
internal fun rememberOutside(): BooruOutside {
    val links = LocalLinks.current
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    return remember(links, uri, clipboard) {
        BooruOutside(
            open = { url -> links?.openOutside(url) ?: runCatching { uri.openUri(url) } },
            share = { text -> links?.share(text) == true },
            copy = { text -> clipboard.setText(AnnotatedString(text)) },
        )
    }
}

/** "Copied to clipboard", said at the screen's foot. */
@Composable
internal fun copiedNotice(app: BooruApp): () -> Unit {
    val copied = pluginString(Strings.copied)
    return remember(app, copied) { { app.tell(copied) } }
}
