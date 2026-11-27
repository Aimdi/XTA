package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aimdi.xtanative.ui.common.SearchField
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.generated.resources.settings_no_results
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Where the desktop's drop-down list of a choice stands, how tall it is, and how the keys move along it. Pure, so the rules are tested. */
internal object ChoiceDropdownPlace {
    /** Its width and a row's height, in dp. */
    const val WIDTH = 300
    const val ROW = 40

    /** The most rows it shows before it scrolls. */
    const val MAX_ROWS = 9

    /** Its distance from the value it drops from and from the window's edges, in dp. */
    const val GAP = 4
    const val MARGIN = 8

    /**
     * The rows its list is tall for [count] options, all of them, not the
     * ones a filter leaves: the list keeps its size as the reader types.
     */
    fun rows(count: Int): Int = count.coerceIn(1, MAX_ROWS)

    /**
     * Its top left corner, in px: under [anchor], its end edge on the
     * anchor's (its start edge right to left); above the anchor where there is
     * no room under it and more over it; kept [margin] inside [window].
     */
    fun position(anchor: IntRect, window: IntSize, popup: IntSize, rtl: Boolean, gap: Int, margin: Int): IntOffset {
        val wantedX = if (rtl) anchor.left else anchor.right - popup.width
        val x = wantedX.coerceIn(margin, (window.width - popup.width - margin).coerceAtLeast(margin))
        val below = anchor.bottom + gap
        val above = anchor.top - gap - popup.height
        val fitsBelow = below + popup.height <= window.height - margin
        val y = when {
            fitsBelow -> below
            above >= margin -> above
            else -> below.coerceAtMost((window.height - popup.height - margin).coerceAtLeast(margin))
        }
        return IntOffset(x, y)
    }

    /** The highlighted row after [key] from [highlight] among [count]; null for a key that moves nothing. */
    fun moved(highlight: Int, count: Int, key: Key): Int? {
        if (count == 0) return null
        return when (key) {
            Key.DirectionDown -> (highlight + 1).coerceAtMost(count - 1)
            Key.DirectionUp -> (highlight - 1).coerceAtLeast(0)
            Key.PageDown -> (highlight + MAX_ROWS - 1).coerceAtMost(count - 1)
            Key.PageUp -> (highlight - MAX_ROWS + 1).coerceAtLeast(0)
            else -> null
        }
    }
}

/**
 * A choice's list as a desktop's drop-down: under the value it was opened
 * from, [ChoiceDropdownPlace.WIDTH] wide and as tall as all its options
 * would be (at most [ChoiceDropdownPlace.MAX_ROWS] rows), whatever the filter
 * leaves; a long list has a filter over it that the keys go to at once,
 * matching each option's [terms]. ↑ ↓ (and Page Up, Page Down) move the
 * highlight, Enter picks it, Esc or a click beside it closes it.
 */
@Composable
internal fun <T> ChoiceDropdown(
    options: List<T>,
    selected: T,
    labels: List<String>,
    terms: List<List<String>>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, terms) { ChoiceList.matchingAny(terms, query) }
    var highlight by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keys = remember { FocusRequester() }
    val filtered = ChoiceList.filtered(options.size)
    LaunchedEffect(shown) {
        // At first the chosen option, as a filter narrows the list its first match.
        highlight = if (query.isEmpty()) shown.indexOf(options.indexOf(selected)).coerceAtLeast(0) else 0
        list.scrollToItem((highlight - ChoiceDropdownPlace.MAX_ROWS / 2).coerceAtLeast(0))
    }
    LaunchedEffect(Unit) {
        // The popup's window may take the keys a few frames late, dropping a focus asked for before it does.
        repeat(FOCUS_TRIES) {
            withFrameNanos {}
            runCatching { keys.requestFocus() }
        }
    }
    val pick = { shown.getOrNull(highlight)?.let { onSelect(options[it]) } }
    val density = LocalDensity.current
    val place = remember(density) {
        with(density) { AnchoredBelow(ChoiceDropdownPlace.GAP.dp.roundToPx(), ChoiceDropdownPlace.MARGIN.dp.roundToPx()) }
    }
    Popup(popupPositionProvider = place, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.width(ChoiceDropdownPlace.WIDTH.dp).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> pick().let { true }
                    Key.Escape -> onDismiss().let { true }
                    else -> ChoiceDropdownPlace.moved(highlight, shown.size, event.key)?.let { next ->
                        highlight = next
                        scope.launch { list.keepInView(next) }
                        true
                    } ?: false
                }
            },
        ) {
            Column {
                if (filtered) {
                    SearchField(query, { query = it }, stringResource(Res.string.search), Modifier.fillMaxWidth().padding(8.dp).focusRequester(keys))
                } else {
                    Box(Modifier.size(0.dp).focusRequester(keys).focusable())
                }
                val rows = ChoiceDropdownPlace.rows(options.size)
                Box(Modifier.fillMaxWidth().height((ChoiceDropdownPlace.ROW * rows).dp + 8.dp)) {
                    LazyColumn(state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                        items(shown.size) { at ->
                            val index = shown[at]
                            DropdownOption(labels[index], options[index] == selected, at == highlight, onHover = { highlight = at }) { onSelect(options[index]) }
                        }
                    }
                    if (shown.isEmpty()) {
                        Text(
                            stringResource(Res.string.settings_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** How many frames the list asks for the keys before giving up. */
private const val FOCUS_TRIES = 10

/** Scrolls so [row] is in view, as little as it takes. */
private suspend fun LazyListState.keepInView(row: Int) {
    val visible = layoutInfo.visibleItemsInfo
    val first = visible.firstOrNull()?.index ?: return scrollToItem(row)
    val fullyLast = visible.lastOrNull { it.offset + it.size <= layoutInfo.viewportEndOffset }?.index ?: first
    when {
        row < first -> scrollToItem(row)
        row > fullyLast -> scrollToItem((row - (fullyLast - first)).coerceAtLeast(0))
    }
}

/** One option: the chosen one checked, the highlighted one lit, as a desktop list lights the row under the pointer or the keys. */
@Composable
private fun DropdownOption(label: String, chosen: Boolean, lit: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(ChoiceDropdownPlace.ROW.dp).padding(horizontal = 4.dp).clip(RoundedCornerShape(8.dp))
            .background(if (lit) colors.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent().type == PointerEventType.Move) onHover()
                    }
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (chosen) Icon(Icons.Outlined.Check, null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A popup under its anchor, as [ChoiceDropdownPlace.position] places it. */
private class AnchoredBelow(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        ChoiceDropdownPlace.position(anchorBounds, windowSize, popupContentSize, layoutDirection == LayoutDirection.Rtl, gap, margin)
}
