package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.settings.EhReadingMode
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlin.math.roundToInt

private val BarColor = Color(0xD9000000)

internal fun modeIcon(mode: EhReadingMode): ImageVector = when (mode) {
    EhReadingMode.LEFT_TO_RIGHT -> EhIcons.LeftToRight
    EhReadingMode.RIGHT_TO_LEFT -> EhIcons.RightToLeft
    EhReadingMode.VERTICAL -> EhIcons.Vertical
}

/** The reader's bars over the pages (eh_reader_chrome.dart): the title at the top; the slider, the page counter and the mode below. */
@Composable
internal fun ReaderChrome(title: String, state: EhReaderState, model: EhReaderModel, onBack: () -> Unit, onCounter: () -> Unit, onMode: () -> Unit) {
    val reduced = LocalReducedMotion.current
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.chromeVisible,
            enter = if (reduced) EnterTransition.None else slideInVertically { -it },
            exit = if (reduced) ExitTransition.None else slideOutVertically { -it },
        ) {
            Row(Modifier.fillMaxWidth().background(BarColor).statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, pluginString(Strings.back), tint = Color.White) }
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 16.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(
            visible = state.chromeVisible,
            enter = if (reduced) EnterTransition.None else slideInVertically { it },
            exit = if (reduced) ExitTransition.None else slideOutVertically { it },
        ) {
            PageBar(state, model, onCounter, onMode)
        }
    }
}

@Composable
private fun PageBar(state: EhReaderState, model: EhReaderModel, onCounter: () -> Unit, onMode: () -> Unit) {
    val total = model.total
    BoxWithConstraints(Modifier.fillMaxWidth().background(BarColor).navigationBarsPadding().padding(4.dp)) {
        val counterWidth = maxWidth * 0.4f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { if (total > 1) PageSlider(state, model) }
            TextButton(onClick = onCounter, colors = ButtonDefaults.textButtonColors(contentColor = Color.White), modifier = Modifier.widthIn(max = counterWidth)) {
                Text(pluginString(Strings.page_of, state.shownPage, total), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onMode) { Icon(modeIcon(state.mode), pluginString(Strings.reader_mode), tint = Color.White) }
        }
    }
}

/** Runs the way the pages do, so page 1 sits on the right when reading right to left; shows the page while dragged and goes there when let go. */
@Composable
private fun PageSlider(state: EhReaderState, model: EhReaderModel) {
    val total = model.total
    val spoken = pluginString(Strings.page_of, state.shownPage, total)
    CompositionLocalProvider(LocalLayoutDirection provides if (state.mode.reversed) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Slider(
            value = state.shownPage.coerceIn(1, total).toFloat(),
            onValueChange = { model.scrub(it.roundToInt()) },
            onValueChangeFinished = { model.state.value.scrubPage?.let { model.goTo(it) } },
            valueRange = 1f..total.toFloat(),
            modifier = Modifier.semantics { stateDescription = spoken },
        )
    }
}

/** The reading modes, the one in use checked (eh_reader_sheets.dart). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModeSheet(current: EhReadingMode, onDismiss: () -> Unit, onPick: (EhReadingMode) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(pluginString(Strings.reader_mode))
        EhReadingMode.entries.forEach { mode ->
            ListItem(
                headlineContent = { Text(pluginString(EhText.readingMode(mode))) },
                leadingContent = { Icon(modeIcon(mode), contentDescription = null) },
                trailingContent = if (mode == current) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                modifier = Modifier.clickable { onPick(mode) },
            )
        }
    }
}

/**
 * A long press's sheet for one page: another image server, the original
 * for a signed-in reader, the picture saved, and the page's link, each only
 * when it can be done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageActions(app: EhApp, open: EhOpenReader, page: Int, onDismiss: () -> Unit) {
    val image = open.resolver.imageOf(page)
    val original = image?.originalImageUrl?.trim()?.takeIf { app.client.signedIn && it.isNotEmpty() }
    val preview = open.resolver.previewOf(page)
    val copyText = rememberCopyText()
    val openOutside = rememberOpenOutside()
    val copied = pluginString(Strings.link_copied)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(pluginString(Strings.page_of, page, open.model.total))
        ActionRow(Icons.Filled.Refresh, pluginString(Strings.reload_image)) {
            onDismiss()
            open.resolver.reload(page)
        }
        if (original != null) {
            ActionRow(PostIcons.OpenInNew, pluginString(Strings.reader_open_original)) {
                onDismiss()
                openOutside(original)
            }
        }
        if (image != null) {
            ActionRow(GroupIcons.FileDownloadOutlined, pluginString(Strings.download)) {
                onDismiss()
                app.download(open.gallery, page, open.model.total, image.imageUrl)
            }
        }
        if (preview != null) {
            ActionRow(XIcons.Link, pluginString(Strings.copy_link)) {
                onDismiss()
                copyText(open.gallery.pageUrl(app.client.host, preview.pageToken, page))
                app.notices.tell(copied)
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Asks for a page to go to, the current one written in: a page past the
 * gallery is answered at the screen's foot rather than gone to.
 */
@Composable
internal fun JumpDialog(app: EhApp, current: Int, total: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = "$current"
    var text by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val focus = remember { FocusRequester() }
    val language = currentLanguage()
    val submit = {
        onDismiss()
        val page = text.text.toIntOrNull()
        when {
            page == null -> Unit
            page < 1 || page > total -> app.notices.tell(Strings.jump_invalid.get(language, total))
            else -> onJump(page)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.jump_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { typed -> text = typed.copy(text = typed.text.filter(Char::isDigit)) },
                placeholder = { Text(pluginString(Strings.jump_hint, total)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = submit) { Text(pluginString(Strings.jump_go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
