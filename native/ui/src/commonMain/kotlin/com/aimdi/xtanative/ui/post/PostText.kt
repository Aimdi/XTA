package com.aimdi.xtanative.ui.post

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.parse.XRun
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.ui.common.clickableNoRipple
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_show_more
import org.jetbrains.compose.resources.stringResource

/** The line cap a post has to break before it folds: sixteen is long-form territory (_ExpandableTweetText.dart). */
const val POST_TEXT_MAX_LINES = 16

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
    val cashtags = LocalCashtagOpener.current
    val text = remember(runs, linkColor, actions, cashtags) { annotated(runs, linkColor, actions, cashtags) }
    if (text.isEmpty()) return
    Text(text, modifier = modifier, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/**
 * A post's text that folds past [maxLines] (null never folds) under a fade
 * and XTA's "Show more", which unfolds it in place. [selectable] text can be
 * selected, as an opened post's is.
 */
@Composable
fun ExpandablePostText(
    post: XPost,
    actions: PostActions,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int? = POST_TEXT_MAX_LINES,
    selectable: Boolean = false,
) {
    val runs = remember(post) { XText.runs(post) }
    val linkColor = MaterialTheme.colorScheme.primary
    val cashtags = LocalCashtagOpener.current
    val text = remember(runs, linkColor, actions, cashtags) { annotated(runs, linkColor, actions, cashtags) }
    if (text.isEmpty()) return
    var expanded by remember(post.id, post.text) { mutableStateOf(false) }
    var folds by remember(post.id, post.text, maxLines) { mutableStateOf(false) }
    val clipped = maxLines != null && !expanded
    Column(modifier.animateContentSize()) {
        val drawn: @Composable () -> Unit = {
            Text(
                text,
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (clipped) maxLines!! else Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                onTextLayout = { layout -> if (clipped && layout.hasVisualOverflow) folds = true },
                modifier = if (clipped && folds) Modifier.fadingBottom() else Modifier,
            )
        }
        if (selectable) SelectionContainer { drawn() } else drawn()
        if (clipped && folds) {
            Text(
                stringResource(Res.string.posts_show_more),
                color = linkColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(min = 36.dp).padding(top = 8.dp, end = 12.dp).clickableNoRipple { expanded = true },
            )
        }
    }
}

/** The last lines of a folded text fading out, as XTA's ShaderMask fades them. */
private fun Modifier.fadingBottom(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(0.6f to Color.Black, 0.8f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/** [runs] with each link, mention and tag leading where X's would; a cashtag goes to [cashtags]' screen first ([LocalCashtagOpener]). */
internal fun annotated(runs: List<XRun>, linkColor: Color, actions: PostActions, cashtags: ((String) -> Boolean)? = null): AnnotatedString = buildAnnotatedString {
    val styles = TextLinkStyles(SpanStyle(color = linkColor))
    for (run in runs) {
        when (run) {
            is XRun.Text -> append(run.text)
            is XRun.Link -> withLink(LinkAnnotation.Clickable(run.url, styles) { actions.openLink(run.url) }) { append(run.text) }
            is XRun.Mention -> withLink(LinkAnnotation.Clickable("@" + run.screenName, styles) { actions.openProfile(run.screenName) }) {
                append(run.text)
            }
            is XRun.Hashtag -> withLink(LinkAnnotation.Clickable("#" + run.tag, styles) { actions.search("#" + run.tag) }) { append(run.text) }
            is XRun.Cashtag -> withLink(LinkAnnotation.Clickable("$" + run.symbol, styles) { if (cashtags?.invoke(run.symbol) != true) actions.search("$" + run.symbol) }) {
                append(run.text)
            }
        }
    }
}
