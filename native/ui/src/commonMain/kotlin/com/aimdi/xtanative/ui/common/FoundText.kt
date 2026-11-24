package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp

/**
 * The find bar of the page shown (find in page, [PageFind]): whether it is
 * [open], what is typed ([query]), the page's texts ([blocks]) and the
 * matches in them, and the one shown ([current]). Its texts draw their
 * matches through [foundIn].
 */
@Stable
class PageFindState {
    var open by mutableStateOf(false)

    var query by mutableStateOf("")
        private set

    /** The page's texts, in its order, as its data has them. */
    var blocks by mutableStateOf<List<FindBlock>>(emptyList())

    /** The texts as drawn where that differs from [blocks]' (a translation shown in place), by key. */
    private val drawn = mutableStateMapOf<Any, String>()

    val matches: List<FoundMatch> by derivedStateOf {
        if (!open) emptyList() else PageFind.matches(query, blocks.map { block -> drawn[block.key]?.let { block.copy(text = it) } ?: block })
    }

    var index by mutableStateOf(-1)
        private set

    /** Counted at every move, so the match shown is brought into view again even when it stays the same. */
    var moves by mutableStateOf(0)
        private set

    val current: FoundMatch? get() = matches.getOrNull(index)

    /** [query] typed: the bar goes to its first match at or past the list's row [firstItem] ([PageFind.startAt]). */
    fun search(query: String, firstItem: Int?) {
        this.query = query
        index = PageFind.startAt(matches, firstItem)
        moves++
    }

    /** To the next match ([by] 1) or the one before (-1), round past either end. */
    fun step(by: Int) {
        index = PageFind.step(index, matches.size, by)
        moves++
    }

    /** The text [key] drawn as [text]: matches are counted in what the reader sees. */
    fun drawnAs(key: Any, text: String) {
        val block = blocks.firstOrNull { it.key == key } ?: return
        when {
            block.text == text -> if (key in drawn) drawn.remove(key)
            drawn[key] != text -> drawn[key] = text
        }
    }
}

/** The find bar of the page these texts are on; null where it has none (off the desktop, or a page without one). */
val LocalPageFind = staticCompositionLocalOf<PageFindState?> { null }

/**
 * A text as the find bar shows it ([foundIn]): [text] with its matches
 * marked, [modifier] for the Text that draws it (last in its chain), and
 * [laidOut] to call with its layout. [holdsCurrent] while the match shown is
 * in it, so a folded text unfolds.
 */
class FoundText(val text: AnnotatedString, val modifier: Modifier, val laidOut: (TextLayoutResult) -> Unit, val holdsCurrent: Boolean)

/**
 * [text], named [key] among the page's texts ([FindBlock]), with the find
 * bar's matches marked on it: each on a soft background, the one shown on a
 * strong one and brought into view, its line clear of the page's edges.
 * Links, selection and the rest of the text stay as they are. Unchanged where
 * no find bar is open.
 */
@Composable
fun foundIn(key: Any?, text: AnnotatedString): FoundText {
    val find = LocalPageFind.current
    if (find == null || key == null || !find.open) return FoundText(text, Modifier, {}, false)
    SideEffect { find.drawnAs(key, text.text) }
    val query = find.query
    val ranges = remember(text, query) { PageFind.ranges(query, text.text) }
    val current = find.current?.takeIf { it.key == key }?.ordinal?.takeIf { it in ranges.indices } ?: -1
    val colors = MaterialTheme.colorScheme
    val soft = SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer)
    val strong = SpanStyle(background = colors.tertiary, color = colors.onTertiary)
    val shown = remember(text, ranges, current, soft, strong) {
        if (ranges.isEmpty()) {
            text
        } else {
            buildAnnotatedString {
                append(text)
                ranges.forEachIndexed { at, range -> addStyle(if (at == current) strong else soft, range.first, range.last + 1) }
            }
        }
    }
    val requester = remember { BringIntoViewRequester() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val margin = with(LocalDensity.current) { CLEARANCE.toPx() }
    LaunchedEffect(current, find.moves, layout != null) {
        val laid = layout ?: return@LaunchedEffect
        val range = ranges.getOrNull(current) ?: return@LaunchedEffect
        val at = range.first.coerceAtMost(laid.layoutInput.text.length - 1).coerceAtLeast(0)
        val line = laid.getLineForOffset(at)
        val box = laid.getBoundingBox(at)
        requester.bringIntoView(Rect(box.left, laid.getLineTop(line) - margin, box.right, laid.getLineBottom(line) + margin))
    }
    return FoundText(shown, Modifier.bringIntoViewRequester(requester), { layout = it }, current >= 0)
}

/** How much of the page stays above and below the line of the match shown. */
private val CLEARANCE = 48.dp
