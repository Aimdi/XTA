package com.aimdi.xtanative.ui.post

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.parse.XRun
import com.aimdi.xtanative.core.x.parse.XText

/** A post's text with its links, mentions, hashtags and cashtags in blue, each leading where X's would. */
@Composable
fun PostText(
    post: XPost,
    actions: PostActions,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
) {
    val runs = remember(post) { XText.runs(post) }
    RunsText(runs, actions, modifier, style, maxLines)
}

/** Text runs drawn with their links, mentions and tags in blue: a post's text, or a bio. */
@Composable
fun RunsText(
    runs: List<XRun>,
    actions: PostActions,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(runs, linkColor, actions) { annotated(runs, linkColor, actions) }
    if (text.isEmpty()) return
    Text(text, modifier = modifier, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

private fun annotated(runs: List<XRun>, linkColor: Color, actions: PostActions): AnnotatedString = buildAnnotatedString {
    val styles = TextLinkStyles(SpanStyle(color = linkColor))
    for (run in runs) {
        when (run) {
            is XRun.Text -> append(run.text)
            is XRun.Link -> withLink(LinkAnnotation.Url(run.url, styles)) { append(run.text) }
            is XRun.Mention -> withLink(LinkAnnotation.Clickable("@" + run.screenName, styles) { actions.openProfile(run.screenName) }) {
                append(run.text)
            }
            is XRun.Hashtag -> withLink(LinkAnnotation.Clickable("#" + run.tag, styles) { actions.search("#" + run.tag) }) { append(run.text) }
            is XRun.Cashtag -> withLink(LinkAnnotation.Clickable("$" + run.symbol, styles) { actions.search("$" + run.symbol) }) {
                append(run.text)
            }
        }
    }
}
