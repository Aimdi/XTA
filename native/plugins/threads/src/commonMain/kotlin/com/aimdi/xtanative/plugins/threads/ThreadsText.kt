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
import com.aimdi.xtanative.ui.plugins.TextRun

/**
 * A caption with its mentions, links and tags tappable (threads_rich_text.dart):
 * Meta's own split when the post carried it, else the @handles, addresses and
 * #tags a pattern finds. A mention opens the profile, a tag its posts, a link
 * as the app opens links. The opened post's text can be selected and copied.
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
    if (text.isEmpty()) return
    val runs = remember(text, fragments) { threadsRuns(text, fragments) }
    val linked = @Composable {
        LinkedText(
            runs,
            onMention = { who -> app.openProfile(ThreadsLinks.normaliseHandle(who) ?: who.lowercase()) },
            onTag = app::openTag,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
        )
    }
    if (selectable) SelectionContainer { linked() } else linked()
}

/** The caption's runs as the shared text draws them. */
internal fun threadsRuns(text: String, fragments: List<ThreadsTextFragment>): List<TextRun> = ThreadsCaption.runs(text, fragments).map { run ->
    val target = run.target
    when {
        target == null -> TextRun.Plain(run.text)
        run.kind == ThreadsTextFragment.Kind.MENTION -> TextRun.Mention(run.text, target)
        run.kind == ThreadsTextFragment.Kind.LINK -> TextRun.Link(run.text, target)
        run.kind == ThreadsTextFragment.Kind.TAG -> TextRun.Tag(run.text, target)
        else -> TextRun.Plain(run.text)
    }
}

@Composable
internal fun threadsBodyStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp)
