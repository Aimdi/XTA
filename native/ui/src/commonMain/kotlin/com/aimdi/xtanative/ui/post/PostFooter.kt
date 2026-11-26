package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.aimdi.xtanative.ui.theme.keyboardFocusShown
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.SessionStore
import com.aimdi.xtanative.services.ai.TranslationStatus
import com.aimdi.xtanative.ui.common.KeyedSheet
import com.aimdi.xtanative.ui.common.LocalSessions
import com.aimdi.xtanative.ui.common.LocalSheetCloser
import com.aimdi.xtanative.ui.common.RingedFocus
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.common.keyboardFocusRing
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.common.onSecondaryClickIf
import com.aimdi.xtanative.ui.common.rememberSheetState
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_add_to_group
import com.aimdi.xtanative.ui.generated.resources.posts_open_post
import com.aimdi.xtanative.ui.generated.resources.posts_quotes
import com.aimdi.xtanative.ui.generated.resources.posts_retweets
import com.aimdi.xtanative.ui.generated.resources.posts_share
import com.aimdi.xtanative.ui.generated.resources.posts_show_original
import com.aimdi.xtanative.ui.generated.resources.posts_subscribe
import com.aimdi.xtanative.ui.generated.resources.posts_translate
import com.aimdi.xtanative.ui.generated.resources.posts_translation_banned
import com.aimdi.xtanative.ui.generated.resources.posts_translation_failed
import com.aimdi.xtanative.ui.generated.resources.posts_translation_not_ready
import com.aimdi.xtanative.ui.generated.resources.posts_translation_other
import com.aimdi.xtanative.ui.generated.resources.posts_translation_too_many
import com.aimdi.xtanative.ui.generated.resources.posts_translation_unsupported
import com.aimdi.xtanative.ui.generated.resources.posts_views_count
import com.aimdi.xtanative.ui.generated.resources.saved_note_quote
import com.aimdi.xtanative.ui.plugins.CountButton
import com.aimdi.xtanative.ui.plugins.NetworkCount
import com.aimdi.xtanative.ui.profile.AuthorGroupsSheet
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import com.aimdi.xtanative.ui.saved.LikeButton
import com.aimdi.xtanative.ui.saved.LocalKeeper
import com.aimdi.xtanative.ui.saved.NoteAbout
import com.aimdi.xtanative.ui.saved.SaveButton
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The strip under a post, as XTA lays it out (tweet_footer.dart): replies,
 * reposts with quotes, the heart and the views spread across, then the
 * bookmark and Share. Replies open the post (a long press shows zen mode's
 * replies); reposts open its quotes, and a long press offers its quotes, who
 * reposted it and a note about it; the heart likes it on this device; the
 * bookmark saves it here, a long press filing it in a folder; Share opens its
 * share sheet. Nothing here is sent to X. In zen and calm mode the glyphs
 * stay, without numbers; where the width runs short the views go first, then
 * the numbers, and at last the buttons wrap ([FooterFit]). [picture] is the
 * post as drawn, for "Share … as image"; the counts spread across at most
 * [countsMax], the bookmark and Share staying at the end.
 */
@Composable
fun PostFooter(post: XPost, actions: PostActions, counts: FooterCounts, modifier: Modifier = Modifier, picture: PostPicture? = null, countsMax: Dp = Dp.Infinity) {
    var sharing by rememberSaveable(post.id) { mutableStateOf(false) }
    var noting by rememberSaveable(post.id) { mutableStateOf(false) }
    val sharePicture = rememberPictureShare(post, picture)
    val labels = FooterLabels(counts.replies.label(), counts.reposts.label(), counts.likes.label(), counts.views.label())
    val numbers = remember(labels) { FooterNumbers() }
    val shown = if (numbers.shown) counts else FooterCounts(null, null, null, null)
    val views = counts.views?.takeIf { numbers.shown }
    FooterLayout(
        numbers,
        hasViews = views != null,
        countsMax = countsMax,
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        counts = { FooterCountItems(post, actions, shown, views, onNote = { noting = true }) },
        end = { FooterEndButtons(post, actions, onShare = { sharing = true }) },
    )
    if (noting) actions.keep.NoteAbout(post) { noting = false }
    if (sharing) {
        // Opened with the keys (Enter on the button), the menu stands at the post's end beside the button, as the ⋯ menu
        // the Menu key opens does, rather than at a pointer the reader is not using; fixed as it opens, so a later click
        // on a row does not move it away from under the pointer.
        val byKeys = keyboardFocusShown()
        val atButton = remember { byKeys }
        CompositionLocalProvider(LocalMenuAtPost provides atButton) { PostShareSheet(post, sharePicture, onDismiss = { sharing = false }) }
    }
}

/** The plural form of a views label: once abbreviated, the count is "thousands", which takes the many form. */
internal fun viewsPluralCount(views: Long): Int = if (views < 1000) views.toInt() else 1000

/** The numbers a footer would show, as drawn: none for a count not shown or at 0. */
private data class FooterLabels(val replies: String?, val reposts: String?, val likes: String?, val views: String?)

@Composable
private fun Long?.label(): String? = this?.takeIf { it > 0 }?.let { countText(it) }

/**
 * Whether a footer's counts draw their numbers: its layout takes them away
 * when they do not fit ([FooterFit.withNumbers]), noting the row they
 * needed ([needed], pixels), and gives them back once the footer is that
 * wide again. A footer whose numbers fit, as nearly all do, never changes it.
 */
@Stable
private class FooterNumbers {
    var shown by mutableStateOf(true)
    var needed = Int.MAX_VALUE
}

/**
 * The footer's line, laid out without composing anything twice: [counts]
 * (replies, reposts, the heart, then the views when [hasViews]) spread from
 * the start across at most [countsMax], the bookmark and Share ([end]) at the end. The views stay
 * unplaced where they do not fit; where the numbers do not either, the
 * layout asks [numbers] to drop them, and the bare counts wrap if they must.
 */
@Composable
private fun FooterLayout(
    numbers: FooterNumbers,
    hasViews: Boolean,
    countsMax: Dp,
    modifier: Modifier,
    counts: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(contents = listOf(counts, end), modifier) { (countItems, endItems), constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val countPlaced = countItems.map { it.measure(loose) }
        val endPlaced = endItems.map { it.measure(loose) }
        val gap = FooterFit.GROUP_GAP.dp.roundToPx()
        val endWidth = endPlaced.sumOf { it.width }
        val plain = if (hasViews) countPlaced.dropLast(1) else countPlaced
        val widths = plain.map { it.width }
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth else FooterFit.rowWidth(countPlaced.map { it.width }, endWidth, gap)
        val drawnWithNumbers = numbers.shown
        val fit = if (drawnWithNumbers) {
            FooterFit.withNumbers(available, widths, countPlaced.lastOrNull()?.width?.takeIf { hasViews }, endWidth, gap)
        } else {
            FooterFit.bare(available, widths, endWidth, gap)
        }
        when {
            drawnWithNumbers && !fit.showCounts -> {
                numbers.needed = FooterFit.rowWidth(widths, endWidth, gap)
                numbers.shown = false
            }
            !drawnWithNumbers && numbers.needed <= available -> numbers.shown = true
        }
        // Until the counts are drawn without their numbers, the numbers that did not fit wrap.
        val oneLine = if (drawnWithNumbers) fit.showCounts else !fit.wraps
        if (oneLine) {
            val line = (if (fit.showViews) countPlaced else plain) + endPlaced
            val most = if (countsMax == Dp.Infinity) Int.MAX_VALUE else countsMax.roundToPx()
            placeRow(available, line, line.size - endPlaced.size, gap, FooterFit.BUTTON.dp.roundToPx(), most)
        } else {
            placeFlow(available, plain + endPlaced)
        }
    }
}

/** [items] on one line [available] wide and [height] high, centred on it: the first [counts] spread across at most [countsMax], the rest at the end. */
private fun MeasureScope.placeRow(available: Int, items: List<Placeable>, counts: Int, gap: Int, height: Int, countsMax: Int): MeasureResult {
    val starts = FooterPlacement.row(available, items.take(counts).map { it.width }, items.drop(counts).map { it.width }, gap, countsMax)
    return layout(available, height) {
        items.forEachIndexed { index, item -> item.placeRelative(starts[index], (height - item.height) / 2) }
    }
}

/** [items] wrapped onto lines [available] wide, each spaced evenly and as high as its tallest. */
private fun MeasureScope.placeFlow(available: Int, items: List<Placeable>): MeasureResult {
    val lines = FooterPlacement.flow(available, items.map { it.width })
    val heights = lines.map { line -> line.maxOf { (index, _) -> items[index].height } }
    return layout(available, heights.sum()) {
        var top = 0
        lines.forEachIndexed { at, line ->
            line.forEach { (index, x) -> items[index].placeRelative(x, top) }
            top += heights[at]
        }
    }
}

/** Replies, reposts with their menu, the heart and the views ([views] null for none). */
@Composable
private fun FooterCountItems(post: XPost, actions: PostActions, counts: FooterCounts, views: Long?, onNote: () -> Unit) {
    val scope = postScope()
    var quoteMenu by remember { mutableStateOf(false) }
    FooterCount(
        XIcons.Reply,
        counts.replies ?: 0,
        stringResource(Res.string.posts_open_post),
        MaterialTheme.colorScheme.primary,
        onLongClick = { scope.revealReplies?.invoke() ?: actions.openPost(post.id) },
    ) { actions.openPost(post.id) }
    Box {
        FooterCount(XIcons.Repost, counts.reposts ?: 0, stringResource(Res.string.posts_quotes), REPOST_GREEN, onLongClick = { quoteMenu = true }) { actions.openQuotes(post.id) }
        QuoteMenu(quoteMenu, onDismiss = { quoteMenu = false }, onQuotes = { actions.openQuotes(post.id) }, onRetweets = { actions.openReposters(post.id) }, onNote = onNote)
    }
    actions.keep.LikeButton(post, count = counts.likes)
    if (views != null) {
        val label = pluralStringResource(Res.plurals.posts_views_count, viewsPluralCount(views), countText(views))
        Box(Modifier.padding(FooterFit.PADDING.dp)) { NetworkCount(XIcons.Views, views, label) }
    }
}

/** A count of the footer that is a button, its ripple in [hue] ([CountButton]). */
@Composable
private fun FooterCount(icon: ImageVector, n: Long, label: String, hue: Color, onLongClick: () -> Unit, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    CountButton({ Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp)) }, n, label, tint, onLongClick, onClick, hue)
}

/** X's repost green, which lights the reposts under the pointer as X's footer does. */
internal val REPOST_GREEN = Color(0xFF00BA7C)

/** The long press on the reposts (quote_actions.dart): its quotes, who reposted it, and a note on this device quoting it. */
@Composable
private fun QuoteMenu(open: Boolean, onDismiss: () -> Unit, onQuotes: () -> Unit, onRetweets: () -> Unit, onNote: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        listOf(Res.string.posts_quotes to onQuotes, Res.string.posts_retweets to onRetweets, Res.string.saved_note_quote to onNote).forEach { (label, action) ->
            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                onDismiss()
                action()
            })
        }
    }
}

/** The bookmark and Share, at the end of the footer. */
@Composable
private fun FooterEndButtons(post: XPost, actions: PostActions, onShare: () -> Unit) {
    actions.keep.SaveButton(post)
    val label = stringResource(Res.string.posts_share)
    // The keyboard's focus: the accent's ring round its round, as on an icon button, not an accent disc.
    RingedFocus {
        Box(
            Modifier
                .size(FooterFit.BUTTON.dp)
                .keyboardFocusRing(0.dp)
                .clip(CircleShape)
                .clickable(interactionSource = null, indication = ripple(color = MaterialTheme.colorScheme.primary), role = Role.Button, onClickLabel = label, onClick = onShare),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Share, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * "Share … as image" for [post] drawn as [picture]: taken, then handed to
 * the share sheet (the desktop, which has none, saves it); null without a
 * picture. It runs with the footer, beyond the sheet that asked for it.
 */
@Composable
private fun rememberPictureShare(post: XPost, picture: PostPicture?): (() -> Unit)? {
    val keeper = LocalKeeper.current
    val coroutines = rememberCoroutineScope()
    return remember(keeper, coroutines, post, picture) {
        if (picture == null) {
            null
        } else {
            fun() {
                coroutines.launch {
                    val png = picture.png()
                    if (png == null) KeepNotices.show(KeepNotice.SomethingWentWrong) else keeper.shareMade(png, PostShare.imageName(post.shown), "image/png")
                }
            }
        }
    }
}

/**
 * A glyph at a post's top-right, beside the author: translate and ⋯, each a
 * [PostGeometry.TOUCH_TARGET] target. In a timeline the glyph sits level with
 * the author line ([glyphTop]): its slot is laid out from the line's top, as
 * X's ⋯ is, and the target, centred on the glyph, reaches up into the post's
 * top padding ([PostGeometry.GLYPH_LIFT]), so the pointer's wash, a press and
 * the keyboard's ring are rounds about the glyph, as an icon button's are.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HeaderButton(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onLongClick: (() -> Unit)? = null,
    glyphTop: Boolean = true,
    onClick: () -> Unit,
) {
    // The ring alone marks the keyboard's focus, as on an icon button, not the ring over a grey disc.
    RingedFocus {
        Box(
            Modifier
                .offset(y = if (glyphTop) -PostGeometry.GLYPH_LIFT.dp else 0.dp)
                .size(PostGeometry.TOUCH_TARGET.dp)
                .keyboardFocusRing()
                .onSecondaryClickIf(onLongClick)
                .combinedClickable(
                    interactionSource = null,
                    indication = ripple(bounded = false, radius = (PostGeometry.TOUCH_TARGET / 2 - HEADER_RING_INSET).dp),
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(PostGeometry.GLYPH_SIZE.dp))
        }
    }
}

/** How far in from a header target's edge its ripple round ends: an icon button's 40 dp in its 48 dp target. */
private const val HEADER_RING_INSET = 4f

/**
 * The translate control: translates, or shows the original again; red once
 * X refused; a long press translates every post of the screen.
 */
@Composable
internal fun TranslateButton(translation: PostTranslation, scope: PostScope, glyphTop: Boolean = true) {
    when (translation.status) {
        TranslationStatus.TRANSLATING -> Box(
            Modifier.size(PostGeometry.TOUCH_TARGET.dp),
            contentAlignment = if (glyphTop) Alignment.TopCenter else Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.padding(top = if (glyphTop) PostGeometry.GLYPH_TOP.dp else 0.dp).size(16.dp), strokeWidth = 2.dp)
        }
        TranslationStatus.TRANSLATED -> HeaderButton(
            PostIcons.Translate,
            stringResource(Res.string.posts_show_original),
            MaterialTheme.colorScheme.primary,
            glyphTop = glyphTop,
        ) {
            translation.showOriginal()
        }
        TranslationStatus.ORIGINAL, TranslationStatus.FAILED -> HeaderButton(
            PostIcons.Translate,
            stringResource(Res.string.posts_translate),
            if (translation.status == TranslationStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            onLongClick = { scope.translateAll?.request() ?: translation.translate() },
            glyphTop = glyphTop,
        ) {
            translation.translate()
        }
    }
}

@Composable
internal fun translationProblemText(problem: TranslationProblem): String = stringResource(
    when (problem) {
        TranslationProblem.UNSUPPORTED_LANGUAGE -> Res.string.posts_translation_unsupported
        TranslationProblem.BANNED -> Res.string.posts_translation_banned
        TranslationProblem.TOO_MANY -> Res.string.posts_translation_too_many
        TranslationProblem.FAILED -> Res.string.posts_translation_failed
        TranslationProblem.NOT_READY -> Res.string.posts_translation_not_ready
        TranslationProblem.OTHER -> Res.string.posts_translation_other
    },
)

/** What the follow badge has open: its sheet (Subscribe, Add to group), or the author's groups. */
private enum class BadgeSheet { NONE, CHOICES, GROUPS }

/**
 * XTA's follow badge (avatar_follow_badge.dart): a small + on the rim of the
 * face of an author not followed. A tap follows them here, never on X; a
 * long press offers to subscribe or to file them in groups right away,
 * which follows them too (tweet.dart's _showSubscribeSheet).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FollowBadge(author: XUser, size: Dp, avatar: @Composable () -> Unit) {
    val sessions = LocalSessions.current
    val badged = notFollowed(sessions, author.id)
    var sheet by rememberSaveable(author.id) { mutableStateOf(BadgeSheet.NONE) }
    val coroutines = rememberCoroutineScope()
    val follow = { coroutines.launch { sessions.update { it.follow(author, System.currentTimeMillis()) } } }
    val subscribe = stringResource(Res.string.posts_subscribe)
    val addToGroup = stringResource(Res.string.posts_add_to_group)
    Box(Modifier.size(size)) {
        avatar()
        if (badged) {
            BadgeDisc(size, subscribe, addToGroup, onFollow = { follow() }, onMore = { sheet = BadgeSheet.CHOICES })
        }
    }
    when (sheet) {
        BadgeSheet.CHOICES -> SubscribeSheet(
            onSubscribe = {
                sheet = BadgeSheet.NONE
                follow()
            },
            onGroups = { sheet = BadgeSheet.GROUPS },
            onDismiss = { sheet = BadgeSheet.NONE },
        )
        BadgeSheet.GROUPS -> AuthorGroupsSheet(author, onDismiss = { sheet = BadgeSheet.NONE })
        BadgeSheet.NONE -> Unit
    }
}

/**
 * Whether the session, once read, does not follow [authorId], as that
 * changes: read from the session the app's frame follows for every card,
 * where a change redraws only the badges it changes, else followed here.
 */
@Composable
private fun notFollowed(sessions: SessionStore, authorId: String): Boolean {
    val shared = sharedSession(sessions)
    if (shared != null) return remember(shared, authorId) { derivedStateOf { shared.value?.isFollowed(authorId) == false } }.value
    val session by sessions.state.collectAsStateWithLifecycle()
    return session?.isFollowed(authorId) == false
}

/**
 * The badge itself (avatar_follow_badge.dart's _Disc): a solid disc in the
 * text colour with a + in the page colour, ringed in the page colour so it
 * stands off the face, centred on the avatar's rim at the lower right. It
 * answers in a 48 dp target around it, larger than the disc.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BadgeDisc(avatar: Dp, subscribe: String, addToGroup: String, onFollow: () -> Unit, onMore: () -> Unit) {
    val disc = PostGeometry.followBadgeSize(avatar.value)
    val ring = PostGeometry.followBadgeRing(disc).dp
    val target = PostGeometry.TOUCH_TARGET.dp
    val at = PostGeometry.followBadgeCenter(avatar.value).dp - target / 2
    val page = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .offset(at, at)
            .size(target)
            .onSecondaryClick(onMore)
            .combinedClickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = disc.dp),
                role = Role.Button,
                onClickLabel = subscribe,
                onLongClickLabel = addToGroup,
                onClick = onFollow,
                onLongClick = onMore,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(disc.dp).background(page, CircleShape).padding(ring).background(MaterialTheme.colorScheme.onBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, subscribe, tint = page, modifier = Modifier.size(disc.dp - ring * 2 - 2.dp))
        }
    }
}

/** The sheet a long press on the follow badge opens: subscribe to the author, or add them to groups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscribeSheet(onSubscribe: () -> Unit, onGroups: () -> Unit, onDismiss: () -> Unit) {
    KeyedSheet(sheetState = rememberSheetState(), onDismissRequest = onDismiss) {
        val closer = LocalSheetCloser.current
        Column(Modifier.padding(bottom = 16.dp)) {
            MenuRow(GroupIcons.PersonAdd, stringResource(Res.string.posts_subscribe), closer.closing(onSubscribe))
            MenuRow(PostIcons.GroupAdd, stringResource(Res.string.posts_add_to_group), closer.closing(onGroups))
        }
    }
}
