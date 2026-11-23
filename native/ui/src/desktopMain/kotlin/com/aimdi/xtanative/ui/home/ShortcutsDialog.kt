package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.keys_alt
import com.aimdi.xtanative.ui.generated.resources.keys_back
import com.aimdi.xtanative.ui.generated.resources.keys_ctrl
import com.aimdi.xtanative.ui.generated.resources.keys_end
import com.aimdi.xtanative.ui.generated.resources.keys_esc
import com.aimdi.xtanative.ui.generated.resources.keys_full_screen
import com.aimdi.xtanative.ui.generated.resources.keys_help
import com.aimdi.xtanative.ui.generated.resources.keys_home
import com.aimdi.xtanative.ui.generated.resources.keys_move
import com.aimdi.xtanative.ui.generated.resources.keys_next_post
import com.aimdi.xtanative.ui.generated.resources.keys_page_down
import com.aimdi.xtanative.ui.generated.resources.keys_page_up
import com.aimdi.xtanative.ui.generated.resources.keys_pages
import com.aimdi.xtanative.ui.generated.resources.keys_quit
import com.aimdi.xtanative.ui.generated.resources.keys_refresh
import com.aimdi.xtanative.ui.generated.resources.keys_scroll_ends
import com.aimdi.xtanative.ui.generated.resources.keys_scroll_line
import com.aimdi.xtanative.ui.generated.resources.keys_scroll_screen
import com.aimdi.xtanative.ui.generated.resources.keys_search
import com.aimdi.xtanative.ui.generated.resources.keys_shift
import com.aimdi.xtanative.ui.generated.resources.keys_space
import com.aimdi.xtanative.ui.generated.resources.keys_tab
import com.aimdi.xtanative.ui.generated.resources.keys_tabs
import com.aimdi.xtanative.ui.generated.resources.keys_text_size
import com.aimdi.xtanative.ui.generated.resources.keys_title
import com.aimdi.xtanative.ui.generated.resources.keys_video
import com.aimdi.xtanative.ui.generated.resources.keys_wheel
import com.aimdi.xtanative.ui.generated.resources.settings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One key of a shortcut: a key named in the reader's language (Ctrl is Strg in German), or one shown as its sign (F5, ←). */
sealed interface KeyCap {
    data class Named(val name: StringResource) : KeyCap

    data class Sign(val sign: String) : KeyCap
}

/** What a row of the list does, and the keys that do it: each a combination pressed together. */
data class Shortcut(val does: StringResource, val keys: List<List<KeyCap>>)

/** The desktop's keys, as [DesktopKeys] and the window answer them, for the list F1 and ? open. */
object DesktopShortcuts {
    private val ctrl = KeyCap.Named(Res.string.keys_ctrl)
    private val shift = KeyCap.Named(Res.string.keys_shift)
    private val alt = KeyCap.Named(Res.string.keys_alt)
    private val tab = KeyCap.Named(Res.string.keys_tab)
    private val space = KeyCap.Named(Res.string.keys_space)

    private fun sign(sign: String) = KeyCap.Sign(sign)

    val all: List<Shortcut> = listOf(
        Shortcut(Res.string.keys_help, listOf(listOf(sign("?")), listOf(sign("F1")))),
        Shortcut(Res.string.keys_back, listOf(listOf(KeyCap.Named(Res.string.keys_esc)), listOf(alt, sign("←")), listOf(ctrl, sign("W")))),
        Shortcut(Res.string.keys_refresh, listOf(listOf(sign("F5")), listOf(ctrl, sign("R")))),
        Shortcut(Res.string.keys_search, listOf(listOf(ctrl, sign("F")), listOf(sign("/")))),
        Shortcut(Res.string.keys_tabs, listOf(listOf(ctrl, tab), listOf(ctrl, shift, tab))),
        Shortcut(Res.string.keys_pages, listOf(listOf(sign("←")), listOf(sign("→")))),
        Shortcut(Res.string.keys_scroll_line, listOf(listOf(sign("↑")), listOf(sign("↓")))),
        Shortcut(Res.string.keys_next_post, listOf(listOf(sign("J")), listOf(sign("K")))),
        Shortcut(
            Res.string.keys_scroll_screen,
            listOf(listOf(KeyCap.Named(Res.string.keys_page_up)), listOf(KeyCap.Named(Res.string.keys_page_down)), listOf(space), listOf(shift, space)),
        ),
        Shortcut(Res.string.keys_scroll_ends, listOf(listOf(KeyCap.Named(Res.string.keys_home)), listOf(KeyCap.Named(Res.string.keys_end)))),
        Shortcut(Res.string.keys_move, listOf(listOf(tab), listOf(shift, tab))),
        Shortcut(Res.string.keys_text_size, listOf(listOf(ctrl, sign("+")), listOf(ctrl, sign("−")), listOf(ctrl, sign("0")), listOf(ctrl, KeyCap.Named(Res.string.keys_wheel)))),
        Shortcut(Res.string.keys_full_screen, listOf(listOf(sign("F11")))),
        Shortcut(Res.string.settings_title, listOf(listOf(ctrl, sign(",")))),
        Shortcut(Res.string.keys_video, listOf(listOf(space), listOf(sign("←")), listOf(sign("→")))),
        Shortcut(Res.string.keys_quit, listOf(listOf(ctrl, sign("Q")))),
    )
}

/** The list of the desktop's keys ([DesktopShortcuts]), over the page until closed (Esc, or its button). */
@Composable
internal fun ShortcutsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.keys_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(DesktopShortcuts.all) { shortcut -> ShortcutRow(shortcut) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutRow(shortcut: Shortcut) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(shortcut.does), Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            Modifier.width(KEYS_WIDTH),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            shortcut.keys.forEach { combination ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    combination.forEachIndexed { index, key ->
                        if (index > 0) Text("+", style = MaterialTheme.typography.labelSmall)
                        Cap(key)
                    }
                }
            }
        }
    }
}

/** One key, drawn as a keycap. */
@Composable
private fun Cap(key: KeyCap) {
    val label = when (key) {
        is KeyCap.Named -> stringResource(key.name)
        is KeyCap.Sign -> key.sign
    }
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(label, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

/** How wide the keys' side of a row is: enough for four short combinations on two lines. */
private val KEYS_WIDTH = 220.dp
