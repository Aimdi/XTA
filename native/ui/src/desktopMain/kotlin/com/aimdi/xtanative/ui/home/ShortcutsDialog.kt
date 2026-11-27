package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import com.aimdi.xtanative.ui.common.SearchField
import com.aimdi.xtanative.ui.generated.resources.settings_no_results
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.aimdi.xtanative.ui.common.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aimdi.xtanative.ui.common.ColumnScrollbar
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.home_mark_all_read
import com.aimdi.xtanative.ui.generated.resources.keys_alt
import com.aimdi.xtanative.ui.generated.resources.keys_back
import com.aimdi.xtanative.ui.generated.resources.keys_columns
import com.aimdi.xtanative.ui.generated.resources.keys_ctrl
import com.aimdi.xtanative.ui.generated.resources.keys_delete
import com.aimdi.xtanative.ui.generated.resources.keys_end
import com.aimdi.xtanative.ui.generated.resources.keys_esc
import com.aimdi.xtanative.ui.generated.resources.keys_find_in_page
import com.aimdi.xtanative.ui.generated.resources.keys_find_match
import com.aimdi.xtanative.ui.generated.resources.keys_forward
import com.aimdi.xtanative.ui.generated.resources.keys_full_screen
import com.aimdi.xtanative.ui.generated.resources.keys_go_home
import com.aimdi.xtanative.ui.generated.resources.keys_help
import com.aimdi.xtanative.ui.generated.resources.keys_home
import com.aimdi.xtanative.ui.generated.resources.keys_interface_size
import com.aimdi.xtanative.ui.generated.resources.keys_leave_selection
import com.aimdi.xtanative.ui.generated.resources.keys_menu
import com.aimdi.xtanative.ui.generated.resources.keys_move
import com.aimdi.xtanative.ui.generated.resources.keys_move_row
import com.aimdi.xtanative.ui.generated.resources.keys_new
import com.aimdi.xtanative.ui.generated.resources.keys_next_post
import com.aimdi.xtanative.ui.generated.resources.keys_open_copied_link
import com.aimdi.xtanative.ui.generated.resources.keys_page_down
import com.aimdi.xtanative.ui.generated.resources.keys_page_up
import com.aimdi.xtanative.ui.generated.resources.keys_pages
import com.aimdi.xtanative.ui.generated.resources.keys_photo_move
import com.aimdi.xtanative.ui.generated.resources.keys_photo_zoom
import com.aimdi.xtanative.ui.generated.resources.keys_post_like
import com.aimdi.xtanative.ui.generated.resources.keys_post_author
import com.aimdi.xtanative.ui.generated.resources.keys_post_link
import com.aimdi.xtanative.ui.generated.resources.keys_post_media
import com.aimdi.xtanative.ui.generated.resources.keys_post_menu
import com.aimdi.xtanative.ui.generated.resources.keys_post_open
import com.aimdi.xtanative.ui.generated.resources.keys_post_save
import com.aimdi.xtanative.ui.generated.resources.keys_post_translate
import com.aimdi.xtanative.ui.generated.resources.keys_post_window
import com.aimdi.xtanative.ui.generated.resources.keys_quick_switch
import com.aimdi.xtanative.ui.generated.resources.keys_quit
import com.aimdi.xtanative.ui.generated.resources.keys_recent_searches
import com.aimdi.xtanative.ui.generated.resources.keys_refresh
import com.aimdi.xtanative.ui.generated.resources.keys_regions
import com.aimdi.xtanative.ui.generated.resources.keys_remove_selected
import com.aimdi.xtanative.ui.generated.resources.keys_reopen_window
import com.aimdi.xtanative.ui.generated.resources.keys_scroll_ends
import com.aimdi.xtanative.ui.generated.resources.keys_scroll_line
import com.aimdi.xtanative.ui.generated.resources.keys_scroll_screen
import com.aimdi.xtanative.ui.generated.resources.keys_search
import com.aimdi.xtanative.ui.generated.resources.keys_select_all
import com.aimdi.xtanative.ui.generated.resources.keys_shift
import com.aimdi.xtanative.ui.generated.resources.keys_space
import com.aimdi.xtanative.ui.generated.resources.keys_tab
import com.aimdi.xtanative.ui.generated.resources.keys_tab_number
import com.aimdi.xtanative.ui.generated.resources.keys_tabs
import com.aimdi.xtanative.ui.generated.resources.keys_text_size
import com.aimdi.xtanative.ui.generated.resources.keys_title
import com.aimdi.xtanative.ui.generated.resources.keys_undo
import com.aimdi.xtanative.ui.generated.resources.keys_video
import com.aimdi.xtanative.ui.generated.resources.keys_video_jump
import com.aimdi.xtanative.ui.generated.resources.keys_video_more
import com.aimdi.xtanative.ui.generated.resources.keys_video_speed
import com.aimdi.xtanative.ui.generated.resources.keys_wheel
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.generated.resources.settings_accessibility
import com.aimdi.xtanative.ui.generated.resources.settings_general
import com.aimdi.xtanative.ui.generated.resources.settings_media
import com.aimdi.xtanative.ui.generated.resources.settings_posts
import com.aimdi.xtanative.ui.generated.resources.settings_saved
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

/** A group of the list, under its [title]: the keys of one part of the app. */
data class ShortcutSection(val title: StringResource, val shortcuts: List<Shortcut>)

/** The desktop's keys, as [DesktopKeys] and the window answer them, for the list F1 and ? open. */
object DesktopShortcuts {
    private val ctrl = KeyCap.Named(Res.string.keys_ctrl)
    private val shift = KeyCap.Named(Res.string.keys_shift)
    private val alt = KeyCap.Named(Res.string.keys_alt)
    private val tab = KeyCap.Named(Res.string.keys_tab)
    private val space = KeyCap.Named(Res.string.keys_space)

    private fun sign(sign: String) = KeyCap.Sign(sign)

    /** The keys in titled groups, in the order the list shows them. */
    val sections: List<ShortcutSection> = listOf(
        ShortcutSection(
            Res.string.settings_general,
            listOf(
                Shortcut(Res.string.keys_help, listOf(listOf(sign("?")), listOf(sign("F1")))),
                Shortcut(Res.string.keys_back, listOf(listOf(KeyCap.Named(Res.string.keys_esc)), listOf(alt, sign("←")), listOf(ctrl, sign("W")))),
                Shortcut(Res.string.keys_forward, listOf(listOf(alt, sign("→")))),
                Shortcut(Res.string.keys_go_home, listOf(listOf(alt, KeyCap.Named(Res.string.keys_home)))),
                Shortcut(Res.string.keys_refresh, listOf(listOf(sign("F5")), listOf(ctrl, sign("R")))),
                Shortcut(Res.string.keys_open_copied_link, listOf(listOf(ctrl, sign("V")))),
                Shortcut(Res.string.keys_new, listOf(listOf(ctrl, sign("N")))),
                Shortcut(Res.string.keys_tabs, listOf(listOf(ctrl, tab), listOf(ctrl, shift, tab))),
                Shortcut(Res.string.keys_tab_number, listOf(listOf(ctrl, sign("1…9")))),
                Shortcut(Res.string.keys_pages, listOf(listOf(sign("←")), listOf(sign("→")))),
                Shortcut(Res.string.keys_columns, listOf(listOf(ctrl, sign("←")), listOf(ctrl, sign("→")))),
                Shortcut(Res.string.keys_move, listOf(listOf(tab), listOf(shift, tab))),
                Shortcut(Res.string.keys_regions, listOf(listOf(sign("F6")), listOf(shift, sign("F6")))),
                Shortcut(Res.string.keys_move_row, listOf(listOf(alt, sign("↑")), listOf(alt, sign("↓")))),
                Shortcut(Res.string.keys_undo, listOf(listOf(ctrl, sign("Z")))),
                Shortcut(Res.string.keys_full_screen, listOf(listOf(sign("F11")))),
                Shortcut(Res.string.keys_reopen_window, listOf(listOf(ctrl, shift, sign("T")))),
                Shortcut(Res.string.settings_title, listOf(listOf(ctrl, sign(",")))),
                Shortcut(Res.string.keys_quit, listOf(listOf(ctrl, sign("Q")))),
            ),
        ),
        ShortcutSection(
            Res.string.search,
            listOf(
                Shortcut(Res.string.keys_search, listOf(listOf(ctrl, sign("F")), listOf(sign("/")), listOf(ctrl, sign("L")))),
                Shortcut(Res.string.keys_quick_switch, listOf(listOf(ctrl, sign("P")), listOf(ctrl, sign("K")))),
                Shortcut(Res.string.keys_find_in_page, listOf(listOf(ctrl, sign("F")))),
                Shortcut(Res.string.keys_find_match, listOf(listOf(sign("F3")), listOf(shift, sign("F3")), listOf(sign("↵")), listOf(shift, sign("↵")))),
                Shortcut(Res.string.keys_recent_searches, listOf(listOf(sign("↓")), listOf(sign("↑")))),
            ),
        ),
        ShortcutSection(
            Res.string.settings_posts,
            listOf(
                Shortcut(Res.string.keys_next_post, listOf(listOf(sign("J")), listOf(sign("K")))),
                Shortcut(Res.string.home_mark_all_read, listOf(listOf(shift, KeyCap.Named(Res.string.keys_esc)))),
                Shortcut(Res.string.keys_post_open, listOf(listOf(sign("↵")), listOf(sign("O")))),
                Shortcut(Res.string.keys_post_window, listOf(listOf(shift, sign("↵")), listOf(ctrl, sign("↵")))),
                Shortcut(Res.string.keys_post_author, listOf(listOf(sign("U")))),
                Shortcut(Res.string.keys_post_media, listOf(listOf(sign("M")), listOf(sign("V")))),
                Shortcut(Res.string.keys_post_translate, listOf(listOf(sign("T")))),
                Shortcut(Res.string.keys_post_like, listOf(listOf(sign("L")))),
                Shortcut(Res.string.keys_post_save, listOf(listOf(sign("B")))),
                Shortcut(Res.string.keys_post_link, listOf(listOf(ctrl, shift, sign("C")))),
                Shortcut(Res.string.keys_post_menu, listOf(listOf(KeyCap.Named(Res.string.keys_menu)), listOf(shift, sign("F10")))),
                Shortcut(Res.string.keys_scroll_line, listOf(listOf(sign("↑")), listOf(sign("↓")))),
                Shortcut(
                    Res.string.keys_scroll_screen,
                    listOf(listOf(KeyCap.Named(Res.string.keys_page_up)), listOf(KeyCap.Named(Res.string.keys_page_down)), listOf(space), listOf(shift, space)),
                ),
                Shortcut(Res.string.keys_scroll_ends, listOf(listOf(KeyCap.Named(Res.string.keys_home)), listOf(KeyCap.Named(Res.string.keys_end)))),
            ),
        ),
        ShortcutSection(
            Res.string.settings_accessibility,
            listOf(
                Shortcut(Res.string.keys_text_size, listOf(listOf(ctrl, sign("+")), listOf(ctrl, sign("−")), listOf(ctrl, sign("0")), listOf(ctrl, KeyCap.Named(Res.string.keys_wheel)))),
                Shortcut(Res.string.keys_interface_size, listOf(listOf(ctrl, alt, sign("+")), listOf(ctrl, alt, sign("−")), listOf(ctrl, alt, sign("0")))),
            ),
        ),
        ShortcutSection(
            Res.string.settings_media,
            listOf(
                Shortcut(Res.string.keys_video, listOf(listOf(space), listOf(sign("K")), listOf(sign("←")), listOf(sign("→")), listOf(sign("J")), listOf(sign("L")))),
                Shortcut(Res.string.keys_video_more, listOf(listOf(sign("M")), listOf(KeyCap.Named(Res.string.keys_home)), listOf(sign("F")))),
                Shortcut(Res.string.keys_video_speed, listOf(listOf(sign("<")), listOf(sign(">")))),
                Shortcut(Res.string.keys_video_jump, listOf(listOf(sign("0…9")), listOf(KeyCap.Named(Res.string.keys_end)))),
                Shortcut(Res.string.keys_photo_zoom, listOf(listOf(sign("+")), listOf(sign("−")), listOf(sign("0")))),
                Shortcut(Res.string.keys_photo_move, listOf(listOf(sign("←")), listOf(sign("↑")), listOf(sign("→")), listOf(sign("↓")))),
            ),
        ),
        ShortcutSection(
            Res.string.settings_saved,
            listOf(
                Shortcut(Res.string.keys_select_all, listOf(listOf(ctrl, sign("A")))),
                Shortcut(Res.string.keys_remove_selected, listOf(listOf(KeyCap.Named(Res.string.keys_delete)))),
                Shortcut(Res.string.keys_leave_selection, listOf(listOf(KeyCap.Named(Res.string.keys_esc)))),
            ),
        ),
    )

    /** Every key, as one list. */
    val all: List<Shortcut> = sections.flatMap { it.shortcuts }

    /**
     * [sections] in order, split where two columns come out closest in
     * height (a title counts as two rows): the first column, then the rest.
     */
    fun twoColumns(sections: List<ShortcutSection>): Pair<List<ShortcutSection>, List<ShortcutSection>> {
        val heights = sections.map { it.shortcuts.size + TITLE_ROWS }
        val total = heights.sum()
        val split = (0..sections.size).minBy { at -> kotlin.math.abs(total - 2 * heights.take(at).sum()) }
        return sections.take(split) to sections.drop(split)
    }

    private const val TITLE_ROWS = 2

    /** Every text the list shows (its titles, what each row does, the keys named in words), for the filter to read in the reader's language. */
    val texts: List<StringResource> = (
        sections.map { it.title } +
            all.map { it.does } +
            all.flatMap { shortcut -> shortcut.keys.flatten().filterIsInstance<KeyCap.Named>().map { it.name } }
        ).distinct()

    /**
     * The rows of [sections] the filter's [query] finds, in their groups (a
     * group left with none is gone): every word of it, ignoring case, in the
     * group's title, what the row does or its keys, each read by [text]. A
     * blank query keeps them all.
     */
    fun matching(sections: List<ShortcutSection>, query: String, text: (StringResource) -> String): List<ShortcutSection> {
        val words = query.lowercase().split(' ', '\t').filter { it.isNotEmpty() }
        if (words.isEmpty()) return sections
        return sections.mapNotNull { section ->
            val title = text(section.title)
            val kept = section.shortcuts.filter { shortcut ->
                val said = listOf(title, text(shortcut.does)) + shortcut.keys.flatten().map { key ->
                    when (key) {
                        is KeyCap.Named -> text(key.name)
                        is KeyCap.Sign -> key.sign
                    }
                }
                val haystack = said.joinToString(" ").lowercase()
                words.all { it in haystack }
            }
            if (kept.isEmpty()) null else section.copy(shortcuts = kept)
        }
    }
}

/**
 * The list of the desktop's keys ([DesktopShortcuts]) in its titled groups,
 * in two columns where the window is wide enough, no taller than
 * [DIALOG_MAX_HEIGHT], with the desktop's scrollbar beside it and a filter
 * over it ([DesktopShortcuts.matching]), over the page until closed (Esc, or
 * its button).
 */
@Composable
internal fun ShortcutsDialog(onDismiss: () -> Unit) {
    // Material's alert dialogs stop at 560dp; this one is laid out as one but grows to two columns.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(24.dp).widthIn(max = DIALOG_MAX_WIDTH), shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation) {
            Column(Modifier.padding(24.dp)) {
                var query by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.keys_title), Modifier.weight(1f).padding(end = 16.dp), style = MaterialTheme.typography.headlineSmall, color = AlertDialogDefaults.titleContentColor)
                    SearchField(query, { query = it }, stringResource(Res.string.search), Modifier.width(FILTER_WIDTH))
                }
                Spacer(Modifier.height(16.dp))
                val names = DesktopShortcuts.texts.associateWith { stringResource(it) }
                val shown = remember(query, names) { DesktopShortcuts.matching(DesktopShortcuts.sections, query) { names.getValue(it) } }
                val scroll = rememberScrollState()
                BoxWithConstraints(Modifier.weight(1f, fill = false).heightIn(max = DIALOG_MAX_HEIGHT)) {
                    val wide = maxWidth >= TWO_COLUMNS_FROM
                    // The bar's own lane at the end, so it never covers the keys.
                    Box(Modifier.padding(end = 12.dp).verticalScrollByKeys(scroll).padding(bottom = 8.dp)) {
                        when {
                            shown.isEmpty() -> Text(
                                stringResource(Res.string.settings_no_results),
                                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            wide && shown.size > 1 -> {
                                val (first, second) = DesktopShortcuts.twoColumns(shown)
                                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                                    Sections(first, Modifier.weight(1f))
                                    Sections(second, Modifier.weight(1f))
                                }
                            }
                            else -> Sections(shown, Modifier.fillMaxWidth())
                        }
                    }
                    // More below: the last rows fade into the dialog instead of being cut off at its edge.
                    if (scroll.canScrollForward) {
                        val surface = AlertDialogDefaults.containerColor
                        Box(
                            Modifier.align(Alignment.BottomStart).padding(end = 12.dp).fillMaxWidth().height(FADE_HEIGHT)
                                .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface))),
                        )
                    }
                    ColumnScrollbar(scroll, Modifier.align(Alignment.TopEnd))
                }
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
                }
            }
        }
    }
}

/** [sections] one under another, each its title over its rows. */
@Composable
private fun Sections(sections: List<ShortcutSection>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sections.forEachIndexed { index, section ->
            Text(
                stringResource(section.title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = if (index == 0) 0.dp else 14.dp).semantics { heading() },
            )
            section.shortcuts.forEach { shortcut -> ShortcutRow(shortcut) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutRow(shortcut: Shortcut) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(shortcut.does), Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
        // As wide as its keys need, up to its share, so a row's words have the rest.
        FlowRow(
            Modifier.widthIn(max = KEYS_WIDTH),
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

/** How wide the keys' side of a row grows: enough for four short combinations on two lines. */
private val KEYS_WIDTH = 200.dp

/** The dialog at its widest, two columns of rows side by side. */
private val DIALOG_MAX_WIDTH = 1040.dp

/** How wide the filter stands beside the title. */
private val FILTER_WIDTH = 240.dp

/** How tall the fade over the last rows is while more of the list is below. */
private val FADE_HEIGHT = 24.dp

/** How tall the list grows before it scrolls. */
private val DIALOG_MAX_HEIGHT = 600.dp

/** From this width of the list on, its groups stand in two columns. */
private val TWO_COLUMNS_FROM = 720.dp
