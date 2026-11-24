package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.FindBlock
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.LocalPageFind
import com.aimdi.xtanative.ui.common.PageFind
import com.aimdi.xtanative.ui.common.PageFindState
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.find_in_page
import com.aimdi.xtanative.ui.generated.resources.find_next_match
import com.aimdi.xtanative.ui.generated.resources.find_previous_match
import com.aimdi.xtanative.ui.generated.resources.reader_close
import com.aimdi.xtanative.ui.generated.resources.settings_no_results
import org.jetbrains.compose.resources.stringResource

/**
 * [content], a reading page (an opened post, an article), with a find bar
 * at its foot as a browser's Ctrl+F opens ([FindsInPageWithKeys]): what is
 * typed is found in the page's texts ([blocks], drawn through
 * [com.aimdi.xtanative.ui.common.foundIn]), "3 / 12" counts the matches,
 * Enter and F3 go to the next, Shift with them to the one before, and Esc
 * closes it, the keys going back to the page. In a lazy [list] the rows a
 * match is in are scrolled to first. Nothing off the desktop.
 */
@Composable
fun FindInPage(blocks: List<FindBlock>, list: LazyListState? = null, content: @Composable () -> Unit) {
    val actions = LocalDesktopActions.current
    if (actions == null) {
        content()
        return
    }
    val find = remember { PageFindState() }
    SideEffect { find.blocks = blocks }
    val field = remember { FocusRequester() }
    var typed by remember { mutableStateOf(TextFieldValue("")) }
    var asked by remember { mutableIntStateOf(0) }
    var inField by remember { mutableStateOf(false) }
    FindsInPageWithKeys(
        open = {
            find.open = true
            // Opened again: what was typed is selected, to type over or to find again.
            typed = typed.copy(selection = TextRange(0, typed.text.length))
            asked++
        },
        step = { by -> find.open.also { if (it) find.step(by) } },
    )
    LaunchedEffect(asked) { if (asked > 0) runCatching { field.requestFocus() } }
    // Esc in the bar's field closes it; the frame then takes the keys, as from any field.
    val owner = remember { Any() }
    DisposableEffect(actions, find.open) {
        if (find.open) actions.addFieldEscape(owner) { if (inField) find.open = false; false }
        onDispose { actions.removeFieldEscape(owner) }
    }
    val current = find.current
    LaunchedEffect(current, find.moves) {
        val item = current?.item ?: return@LaunchedEffect
        if (list != null && list.layoutInfo.visibleItemsInfo.none { it.index == item }) list.scrollToItem(item)
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { CompositionLocalProvider(LocalPageFind provides find, content = content) }
        if (find.open) {
            FindBar(
                find,
                typed,
                onType = {
                    typed = it
                    if (it.text != find.query) find.search(it.text, list?.firstVisibleItemIndex)
                },
                field = Modifier.focusRequester(field).onFocusChanged { inField = it.isFocused },
                close = { find.open = false },
            )
        }
    }
}

@Composable
private fun FindBar(find: PageFindState, typed: TextFieldValue, onType: (TextFieldValue) -> Unit, field: Modifier, close: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                val placeholder = stringResource(Res.string.find_in_page)
                BasicTextField(
                    typed,
                    onValueChange = onType,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).then(field).onPreviewKeyEvent { event ->
                        val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                        if (enter && event.type == KeyEventType.KeyDown) find.step(if (event.isShiftPressed) -1 else 1)
                        enter
                    },
                    decorationBox = { inner ->
                        Box {
                            if (typed.text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                            inner()
                        }
                    },
                )
                val count = PageFind.count(find.index, find.matches.size)
                when {
                    count != null -> Text(count, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    find.query.isNotBlank() -> Text(stringResource(Res.string.settings_no_results), style = MaterialTheme.typography.labelLarge, color = colors.error)
                }
                val hasMatches = find.matches.isNotEmpty()
                BarButton(Icons.Filled.KeyboardArrowUp, stringResource(Res.string.find_previous_match), hasMatches) { find.step(-1) }
                BarButton(Icons.Filled.KeyboardArrowDown, stringResource(Res.string.find_next_match), hasMatches) { find.step(1) }
                BarButton(Icons.Filled.Close, stringResource(Res.string.reader_close), true, close)
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconTip(label) { IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label) } }
}
