package com.aimdi.xtanative.ui.post

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.parse.XRun
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.networks.AddressPiece
import com.aimdi.xtanative.networks.CryptoAddresses
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.Motion
import com.aimdi.xtanative.ui.common.clickableNoRipple
import com.aimdi.xtanative.ui.common.foundIn
import com.aimdi.xtanative.ui.common.rememberCopyContract
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_show_more
import com.aimdi.xtanative.ui.saved.KeepNotices
import org.jetbrains.compose.resources.stringResource

/** The line cap a post has to break before it folds: sixteen is long-form territory (_ExpandableTweetText.dart). */
const val POST_TEXT_MAX_LINES = 16

/**
 * The networks the running plugins name for contract addresses (Stocks'
 * watched tokens), by address: a post's text writes a watched token's
 * network in front of its address. Empty without such a plugin.
 */
val LocalContractNetworks = compositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * How the contract addresses in a text are drawn (rich_text.dart's
 * _addressPart): cut in the middle in [color], led by the network
 * [networks] names for a watched token's, and copied whole by [copy] on a tap.
 */
internal class ContractSpans(val color: Color, val networks: Map<String, String>, val copy: (String) -> Unit)

/** The contract spans of the screen's texts, as the running plugins' watched tokens change. */
@Composable
internal fun rememberContractSpans(): ContractSpans {
    val networks = LocalContractNetworks.current
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val copy = rememberCopyContract(KeepNotices::say)
    return remember(networks, color, copy) { ContractSpans(color, networks, copy) }
}

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

/** Text runs drawn with their links, mentions and tags in blue: a post's text, or a bio. A long press on a cashtag opens its sheet, on a hashtag its topic sheet. */
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
    val contracts = rememberContractSpans()
    val text = remember(runs, linkColor, actions, cashtags, contracts) { annotated(runs, linkColor, actions, cashtags, contracts) }
    if (text.isEmpty()) return
    val layout = remember { TextLayoutHolder() }
    var topic by remember { mutableStateOf<String?>(null) }
    topic?.let { TopicSheet(it) { topic = null } }
    Text(
        text,
        modifier = modifier.tagPresses(text, layout) { topic = it }.linkMiddleClicks(text, layout, actions.openInBrowser).linkDrags(text, layout),
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = layout::take,
    )
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
    findKey: Any? = post.id,
) {
    val runs = remember(post) { XText.runs(post) }
    val linkColor = MaterialTheme.colorScheme.primary
    val cashtags = LocalCashtagOpener.current
    val contracts = rememberContractSpans()
    val text = remember(runs, linkColor, actions, cashtags, contracts) { annotated(runs, linkColor, actions, cashtags, contracts) }
    if (text.isEmpty()) return
    // Kept by post, so a text unfolded stays so when the list scrolls it away and back, or the screen comes back.
    var expanded by rememberSaveable(post.id, post.text) { mutableStateOf(false) }
    // Kept as well: a screen coming back draws its Show more in the first frame, rather than a frame later 36 dp taller,
    // which moved a list read to its end down once the place it came back to had been held to the shorter text.
    var folds by rememberSaveable(post.id, post.text, maxLines) { mutableStateOf(false) }
    var unfolding by remember(post.id) { mutableStateOf(false) }
    val clipped = maxLines != null && !expanded
    val found = foundIn(findKey, text)
    val unfold = {
        unfolding = true
        expanded = true
    }
    // The find bar's match shown is in the folded part: the text unfolds, as Show more does.
    LaunchedEffect(found.holdsCurrent) { if (found.holdsCurrent) unfold() }
    val laidOut = remember { TextLayoutHolder() }
    var topic by remember { mutableStateOf<String?>(null) }
    topic?.let { TopicSheet(it) { topic = null } }
    val resizes = ShowMore.resizes(LocalReducedMotion.current, folds, clipped, unfolding)
    val settled = remember { { _: IntSize, _: IntSize -> unfolding = false } }
    Column(modifier.then(if (resizes) Modifier.animateContentSize(tween(Motion.STANDARD_MS), finishedListener = settled) else Modifier)) {
        val drawn: @Composable () -> Unit = {
            Text(
                found.text,
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (clipped) maxLines!! else Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                onTextLayout = { layout ->
                    laidOut.take(layout)
                    found.laidOut(layout)
                    if (clipped && layout.hasVisualOverflow) folds = true
                },
                modifier = (if (clipped && folds) Modifier.fadingBottom() else Modifier).tagPresses(text, laidOut) { topic = it }.linkMiddleClicks(text, laidOut, actions.openInBrowser).linkDrags(text, laidOut).then(found.modifier),
            )
        }
        if (selectable) SelectionContainer { drawn() } else drawn()
        if (clipped && folds) {
            Text(
                stringResource(Res.string.posts_show_more),
                color = linkColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(min = 36.dp).padding(top = 8.dp, end = 12.dp).clickableNoRipple(onClick = unfold),
            )
        }
    }
}

/**
 * When a folding text's size animates: only one that has a Show more, while
 * it is folded or unfolding, and not while motion is [reduced]. A list's
 * every text carrying the animation would animate as rows are reused in a
 * fling; a text that never folds, or has finished unfolding, has no need of it.
 */
internal object ShowMore {
    fun resizes(reduced: Boolean, folds: Boolean, clipped: Boolean, unfolding: Boolean): Boolean =
        !reduced && folds && (clipped || unfolding)
}

/** The find bar's key of the text of quoted post [id] ([QuotedPost], [ThreadFind]), apart from the post's own where it is shown too. */
internal fun quoteFindKey(id: String): String = "quote-$id"

/** The last lines of a folded text fading out, as XTA's ShaderMask fades them. */
private fun Modifier.fadingBottom(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(0.6f to Color.Black, 0.8f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * [runs] with each link, mention and tag leading where X's would; a cashtag
 * goes to [cashtags]' screen first ([LocalCashtagOpener]), and a contract
 * address in the words between is drawn as [contracts] says.
 */
internal fun annotated(
    runs: List<XRun>,
    linkColor: Color,
    actions: PostActions,
    cashtags: ((String) -> Boolean)? = null,
    contracts: ContractSpans? = null,
): AnnotatedString = buildAnnotatedString {
    val styles = linkStyles(linkColor)
    for (run in runs) {
        when (run) {
            is XRun.Text -> if (contracts == null) append(run.text) else appendWithContracts(run.text, contracts)
            is XRun.Link -> withLink(LinkAnnotation.Clickable(run.url, styles) { actions.openLink(run.url) }) { append(run.text) }
            is XRun.Mention -> withLink(LinkAnnotation.Clickable("@" + run.screenName, styles) { actions.openProfile(run.screenName) }) {
                append(run.text)
            }
            is XRun.Hashtag -> withLink(LinkAnnotation.Clickable("#" + run.tag, styles) { actions.search("#" + run.tag) }) { append(run.text) }
            is XRun.Cashtag -> withLink(LinkAnnotation.Clickable(CashtagLinks.tag(run.symbol), styles) { if (cashtags?.invoke(run.symbol) != true) actions.search(CashtagLinks.tag(run.symbol)) }) {
                append(run.text)
            }
        }
    }
}

/** [text] with each contract address in it cut in the middle, led by its watched token's network, a tap copying it whole. */
private fun AnnotatedString.Builder.appendWithContracts(text: String, contracts: ContractSpans) {
    val styles = linkStyles(contracts.color)
    for (piece in CryptoAddresses.pieces(text)) {
        when (piece) {
            is AddressPiece.Text -> append(piece.text)
            is AddressPiece.Address -> withLink(LinkAnnotation.Clickable(CONTRACT_TAG + piece.address, styles) { contracts.copy(piece.address) }) {
                append(CryptoAddresses.postLabel(piece.address, CryptoAddresses.networkIn(piece.address, contracts.networks)))
            }
        }
    }
}

/**
 * How a link, mention or tag in a post's text is drawn: in [color],
 * underlined while the pointer is over it or the keys are on it, and
 * washed in its colour while pressed, so a click shows where it lands.
 */
internal fun linkStyles(color: Color): TextLinkStyles {
    val marked = SpanStyle(color = color, textDecoration = TextDecoration.Underline)
    return TextLinkStyles(
        style = SpanStyle(color = color),
        focusedStyle = marked,
        hoveredStyle = marked,
        pressedStyle = marked.copy(background = color.copy(alpha = LINK_PRESSED_ALPHA)),
    )
}

/** How strongly a pressed link is washed: enough to see, not enough to hide its letters. */
internal const val LINK_PRESSED_ALPHA = 0.16f

/** What a contract address's link is tagged with, before the address: never a cashtag's `$`. */
private const val CONTRACT_TAG = "contract:"
