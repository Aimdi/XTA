package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.services.ai.TranslationStatus
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.downloads.Downloading
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
import com.aimdi.xtanative.ui.plugins.NetworkCount
import com.aimdi.xtanative.ui.profile.AuthorGroupsSheet
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import com.aimdi.xtanative.ui.saved.LikeButton
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
 * post as drawn, for "Share … as image".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostFooter(post: XPost, actions: PostActions, counts: FooterCounts, modifier: Modifier = Modifier, picture: PostPicture? = null) {
    var sharing by rememberSaveable(post.id) { mutableStateOf(false) }
    var noting by rememberSaveable(post.id) { mutableStateOf(false) }
    val sharePicture = rememberPictureShare(post, picture)
    val labels = FooterLabels(counts.replies.label(), counts.reposts.label(), counts.likes.label(), counts.views.label())
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 6.dp)) {
        val fit = rememberFooterFit(maxWidth, labels)
        val shown = if (fit.showCounts) counts else FooterCounts(null, null, null, null)
        val views = counts.views?.takeIf { fit.showViews }
        if (fit.wraps) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FooterCountItems(post, actions, shown, views, onNote = { noting = true })
                FooterEndButtons(post, actions, onShare = { sharing = true })
            }
        } else {
            Row(Modifier.fillMaxWidth().height(FooterFit.BUTTON.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    FooterCountItems(post, actions, shown, views, onNote = { noting = true })
                }
                Spacer(Modifier.width(FooterFit.GROUP_GAP.dp))
                FooterEndButtons(post, actions, onShare = { sharing = true })
            }
        }
    }
    if (noting) actions.keep.NoteAbout(post) { noting = false }
    if (sharing) PostShareSheet(post, sharePicture, onDismiss = { sharing = false })
}

/** The plural form of a views label: once abbreviated, the count is "thousands", which takes the many form. */
internal fun viewsPluralCount(views: Long): Int = if (views < 1000) views.toInt() else 1000

/** The numbers a footer would show, as drawn: none for a count not shown or at 0. */
private data class FooterLabels(val replies: String?, val reposts: String?, val likes: String?, val views: String?)

@Composable
private fun Long?.label(): String? = this?.takeIf { it > 0 }?.let { countText(it) }

/** What fits of [labels] in [available], measured as the counts draw their numbers. */
@Composable
private fun rememberFooterFit(available: Dp, labels: FooterLabels): FooterFit {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val style = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    return remember(available, labels, style, density) {
        fun width(label: String?): Float = if (label == null) 0f else with(density) { measurer.measure(label, style).size.width.toDp().value }
        FooterFit.of(available.value, listOf(width(labels.replies), width(labels.reposts), width(labels.likes)), labels.views?.let(::width))
    }
}

/** Replies, reposts with their menu, the heart and the views ([views] null for none). */
@Composable
private fun FooterCountItems(post: XPost, actions: PostActions, counts: FooterCounts, views: Long?, onNote: () -> Unit) {
    val scope = postScope()
    var quoteMenu by remember { mutableStateOf(false) }
    NetworkCount(
        XIcons.Reply,
        counts.replies ?: 0,
        stringResource(Res.string.posts_open_post),
        onLongClick = { scope.revealReplies?.invoke() ?: actions.openPost(post.id) },
    ) { actions.openPost(post.id) }
    Box {
        NetworkCount(XIcons.Repost, counts.reposts ?: 0, stringResource(Res.string.posts_quotes), onLongClick = { quoteMenu = true }) { actions.openQuotes(post.id) }
        QuoteMenu(quoteMenu, onDismiss = { quoteMenu = false }, onQuotes = { actions.openQuotes(post.id) }, onRetweets = { actions.openReposters(post.id) }, onNote = onNote)
    }
    actions.keep.LikeButton(post, count = counts.likes ?: 0)
    if (views != null) {
        val label = pluralStringResource(Res.plurals.posts_views_count, viewsPluralCount(views), countText(views))
        Box(Modifier.padding(FooterFit.PADDING.dp)) { NetworkCount(XIcons.Views, views, label) }
    }
}

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
    Box(
        Modifier
            .size(FooterFit.BUTTON.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onShare),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Share, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/**
 * "Share … as image" for [post] drawn as [picture]: taken, then handed to
 * the share sheet (the desktop, which has none, saves it); null without a
 * picture. It runs with the footer, beyond the sheet that asked for it.
 */
@Composable
private fun rememberPictureShare(post: XPost, picture: PostPicture?): (() -> Unit)? {
    val graph = LocalGraph.current
    val coroutines = rememberCoroutineScope()
    return remember(graph, coroutines, post, picture) {
        if (picture == null) {
            null
        } else {
            fun() {
                coroutines.launch {
                    val png = picture.png()
                    if (png == null) KeepNotices.show(KeepNotice.SomethingWentWrong) else Downloading.shareMade(graph, png, PostShare.imageName(post.shown), "image/png")
                }
            }
        }
    }
}

/**
 * A glyph at a post's top-right, beside the author: translate and ⋯, each a
 * [PostGeometry.TOUCH_TARGET] target. In a timeline the glyph sits at the
 * target's top, level with the author line, and the target reaches down over
 * the text as X's ⋯ does ([glyphTop]); elsewhere it is centred.
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
    Box(
        Modifier
            .size(PostGeometry.TOUCH_TARGET.dp)
            .clip(CircleShape)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label },
        contentAlignment = if (glyphTop) Alignment.TopCenter else Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = if (glyphTop) PostGeometry.GLYPH_TOP.dp else 0.dp).size(18.dp))
    }
}

/**
 * The translate control: translates, or shows the original again; red once
 * X refused; a long press translates every post of the screen.
 */
@Composable
internal fun TranslateButton(translation: PostTranslation, scope: PostScope, glyphTop: Boolean = true) {
    val coroutines = rememberCoroutineScope()
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
            onLongClick = { scope.translateAll?.request() ?: coroutines.launch { translation.translate() } },
            glyphTop = glyphTop,
        ) {
            coroutines.launch { translation.translate() }
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
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val followed = session?.followed(author.id) != null
    var sheet by rememberSaveable(author.id) { mutableStateOf(BadgeSheet.NONE) }
    val coroutines = rememberCoroutineScope()
    val follow = { coroutines.launch { graph.sessions.update { it.follow(author, System.currentTimeMillis()) } } }
    val subscribe = stringResource(Res.string.posts_subscribe)
    val addToGroup = stringResource(Res.string.posts_add_to_group)
    Box(Modifier.size(size)) {
        avatar()
        if (!followed && session != null) {
            val surface = MaterialTheme.colorScheme.background
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground)
                    .border(1.5.dp, surface, CircleShape)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = subscribe,
                        onLongClickLabel = addToGroup,
                        onClick = { follow() },
                        onLongClick = { sheet = BadgeSheet.CHOICES },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, subscribe, tint = surface, modifier = Modifier.size(14.dp))
            }
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

/** The sheet a long press on the follow badge opens: subscribe to the author, or add them to groups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscribeSheet(onSubscribe: () -> Unit, onGroups: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            MenuRow(GroupIcons.PersonAdd, stringResource(Res.string.posts_subscribe), onSubscribe)
            MenuRow(PostIcons.GroupAdd, stringResource(Res.string.posts_add_to_group), onGroups)
        }
    }
}
