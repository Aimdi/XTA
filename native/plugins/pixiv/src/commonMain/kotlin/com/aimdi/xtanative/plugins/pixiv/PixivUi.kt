package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.pixiv.PixivFailure
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.Graphemes
import com.aimdi.xtanative.ui.common.rememberSwitchedHaptics
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberImageRequest

/** The running plugin, for every composable of its screens. */
internal val LocalPixiv = staticCompositionLocalOf<PixivRuntime> { error("No Pixiv plugin runtime here") }

/** The plugin's settings, recomposing as they change. */
@Composable
internal fun pixivSettings(): Settings {
    val settings by LocalPixiv.current.settings.state.collectAsState()
    return settings
}

/** The reader's mutes, as they change. */
@Composable
internal fun pixivMutes(): PixivMutes {
    val settings = pixivSettings()
    return remember(settings) { PixivMutes.of(settings) }
}

/**
 * A Pixiv picture from the image server the reader picked, asked for with
 * Pixiv's Referer, which Pixiv's server wants. Nothing to show draws the
 * surface the picture would cover.
 */
@Composable
internal fun PixivPicture(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    if (url.isNullOrEmpty()) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    val host = pixivSettings()[PixivPrefs.imageHost]
    val shown = remember(url, host) { PixivImages.url(url, host) }
    AsyncImage(
        model = rememberImageRequest(shown, PixivImages.HEADERS),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** A creator's round picture, or the first letter of [name] when Pixiv sends none. */
@Composable
internal fun PixivAvatar(url: String?, name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val shape = Modifier.size(size).clip(CircleShape)
    if (url.isNullOrEmpty()) {
        Box(modifier.then(shape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(
                name.trim().takeIf { it.isNotEmpty() }?.let(Graphemes::first).orEmpty().uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    } else {
        PixivPicture(url, modifier.then(shape))
    }
}

/** Why a Pixiv read failed, in XTA's words. */
@Composable
internal fun pixivErrorText(error: Throwable?): String = pluginString(failureText(PixivFailure.of(error ?: Exception())))

internal fun failureText(failure: PixivFailure): PluginText = when (failure) {
    PixivFailure.NOT_SIGNED_IN -> Strings.not_configured
    PixivFailure.NETWORK -> Strings.error_network
    PixivFailure.UNAUTHORIZED -> Strings.error_unauthorized
    PixivFailure.RATE_LIMITED -> Strings.error_rate_limited
    PixivFailure.NOT_FOUND -> Strings.error_not_found
    PixivFailure.BAD_RESPONSE -> Strings.error_response
}

/** A read that failed with nothing to show: why, and Retry. */
@Composable
internal fun PixivProblem(error: Throwable?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            pixivErrorText(error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.retry))
        }
    }
}

/** A message in place of a list, with Retry: re-selecting a tab does not read again, so an empty page still offers a way to. */
@Composable
internal fun PixivEmpty(message: String, onRetry: (() -> Unit)? = null, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp)) }
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (onRetry != null) {
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluginString(Strings.retry))
            }
        }
    }
}

/** What the plugin tells the reader, at the foot of the screen it is drawn over. */
@Composable
internal fun BoxScope.PixivNotices() {
    val runtime = LocalPixiv.current
    val host = remember { SnackbarHostState() }
    LaunchedEffect(runtime) { runtime.notices.collect { host.showSnackbar(it) } }
    SnackbarHost(host, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
}

/** Puts [text] on the clipboard and says [copied]. */
@Composable
internal fun rememberCopy(): (text: String, copied: PluginText) -> Unit {
    val clipboard = LocalClipboardManager.current
    val runtime = LocalPixiv.current
    val language = com.aimdi.xtanative.ui.plugins.currentLanguage()
    return remember(clipboard, runtime, language) {
        { text, copied ->
            clipboard.setText(AnnotatedString(text))
            runtime.tell(copied.get(language))
        }
    }
}

/** A light buzz when something lands, a firmer one for a long press, while the reader keeps haptic feedback on. */
@Stable
internal class PixivHaptics(private val play: (HapticFeedbackType) -> Unit, private val on: () -> Boolean) {
    fun light() {
        if (on()) play(HapticFeedbackType.TextHandleMove)
    }

    fun medium() {
        if (on()) play(HapticFeedbackType.LongPress)
    }
}

@Composable
internal fun rememberHaptics(): PixivHaptics {
    // The app's haptic feedback switch first, in a sheet too, then Pixiv's own (XTA's PixivHaptics).
    val feedback = rememberSwitchedHaptics()
    val runtime = LocalPixiv.current
    return remember(feedback, runtime) { PixivHaptics(feedback::performHapticFeedback) { runtime.settings[PixivPrefs.haptics] } }
}

/** One dark pill over a picture; white on black reads on any artwork. */
@Composable
internal fun PixivBadge(label: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A titled group of a settings page or a sheet. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** A row that turns a setting on or off, its explanation under it. */
@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A row that leads somewhere: an icon, a title and what it says now. */
@Composable
internal fun ActionRow(title: String, onClick: (() -> Unit)?, icon: ImageVector? = null, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke()
    }
}

/** One choice among a few, picked in a dialog of radio rows: [labels] by value, in order. */
@Composable
internal fun <T> ChoiceRow(title: String, current: T, labels: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ActionRow(title, onClick = { open = true }, subtitle = labels.firstOrNull { it.first == current }?.second)
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    labels.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                open = false
                                if (value != current) onPick(value)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == current, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(pluginString(Strings.cancel)) } },
        )
    }
}

/** A question asked before something that cannot be undone, answered with [action] or Cancel. */
@Stable
internal class PixivAsk {
    var question by mutableStateOf<Question?>(null)
        private set

    class Question(val text: String, val action: String, val onYes: () -> Unit)

    fun ask(text: String, action: String, onYes: () -> Unit) {
        question = Question(text, action, onYes)
    }

    fun dismiss() {
        question = null
    }
}

@Composable
internal fun rememberAsk(): PixivAsk = remember { PixivAsk() }

/** The dialog [ask] shows while it asks. */
@Composable
internal fun AskDialog(ask: PixivAsk) {
    val question = ask.question ?: return
    AlertDialog(
        onDismissRequest = ask::dismiss,
        text = { Text(question.text) },
        confirmButton = {
            TextButton(onClick = {
                ask.dismiss()
                question.onYes()
            }) { Text(question.action) }
        },
        dismissButton = { TextButton(onClick = ask::dismiss) { Text(pluginString(Strings.cancel)) } },
    )
}

/** A screen-wide frame for the plugin's own screens: its notices over [content]. */
@Composable
internal fun PixivFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        PixivNotices()
    }
}

/** A title of one or two lines over its quieter detail, as list rows draw names and @accounts. */
@Composable
internal fun NameLines(name: String, detail: String?, modifier: Modifier = Modifier, maxNameLines: Int = 1) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = maxNameLines, overflow = TextOverflow.Ellipsis)
        detail?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
