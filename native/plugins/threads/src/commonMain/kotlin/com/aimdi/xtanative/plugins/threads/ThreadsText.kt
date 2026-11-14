package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.threads.ThreadsCaption
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.ui.plugins.LinkedText

/**
 * A caption with its mentions, links and tags tappable (threads_rich_text.dart):
 * Meta's own split when the post carried it, else the @handles, addresses and
 * #tags a pattern finds. A mention opens the profile, a tag its posts, a link
 * as the app opens links. The opened post's text can be selected and copied.
 * Stickers among the words are drawn in the line ([ThreadsStickerText]).
 */
@Composable
internal fun ThreadsRichText(
    app: ThreadsApp,
    text: String,
    fragments: List<ThreadsTextFragment>,
    modifier: Modifier = Modifier,
    style: TextStyle = threadsBodyStyle(),
    maxLines: Int = Int.MAX_VALUE,
    selectable: Boolean = false,
) {
    val stickers = fragments.any { it.isSticker }
    if (text.isEmpty() && !stickers) return
    val onMention = remember(app) { { who: String -> app.openProfile(ThreadsLinks.normaliseHandle(who) ?: who.lowercase()) } }
    val onTag = remember(app) { { tag: String -> app.openTag(tag) } }
    val linked = @Composable {
        if (stickers) {
            ThreadsStickerText(ThreadsCaption.runs(text, fragments), style, maxLines, modifier, onMention, onTag)
        } else {
            val runs = remember(text, fragments) { threadsRuns(text, fragments) }
            LinkedText(runs, onMention = onMention, onTag = onTag, modifier = modifier, style = style, maxLines = maxLines)
        }
    }
    if (selectable) SelectionContainer { linked() } else linked()
}

@Composable
internal fun threadsBodyStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp)
