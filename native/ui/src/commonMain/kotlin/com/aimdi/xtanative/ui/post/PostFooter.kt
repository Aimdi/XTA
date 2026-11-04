package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.services.ai.TranslationStatus
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_follow
import com.aimdi.xtanative.ui.generated.resources.posts_open_post
import com.aimdi.xtanative.ui.generated.resources.posts_quotes
import com.aimdi.xtanative.ui.generated.resources.posts_retweets
import com.aimdi.xtanative.ui.generated.resources.posts_show_original
import com.aimdi.xtanative.ui.generated.resources.posts_translate
import com.aimdi.xtanative.ui.generated.resources.posts_translation_banned
import com.aimdi.xtanative.ui.generated.resources.posts_translation_failed
import com.aimdi.xtanative.ui.generated.resources.posts_translation_not_ready
import com.aimdi.xtanative.ui.generated.resources.posts_translation_other
import com.aimdi.xtanative.ui.generated.resources.posts_translation_too_many
import com.aimdi.xtanative.ui.generated.resources.posts_translation_unsupported
import com.aimdi.xtanative.ui.generated.resources.posts_views_count
import com.aimdi.xtanative.ui.profile.AuthorGroupsSheet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The counts under a post, as X lays them out (tweet_footer.dart): replies
 * open the post, reposts and quotes together open its quotes (a long press
 * offers the people who reposted it), then likes and views. Nothing here is
 * sent to X; in zen and calm mode the glyphs stay, without numbers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostFooter(post: XPost, actions: PostActions, counts: FooterCounts, modifier: Modifier = Modifier) {
    val scope = postScope()
    var quoteMenu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp, end = 24.dp).height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Count(
            XIcons.Reply,
            counts.replies,
            stringResource(Res.string.posts_open_post),
            onClick = { actions.openPost(post.id) },
            onLongClick = { scope.revealReplies?.invoke() ?: actions.openPost(post.id) },
        )
        Box {
            Count(
                XIcons.Repost,
                counts.reposts,
                stringResource(Res.string.posts_quotes),
                onClick = { actions.openQuotes(post.id) },
                onLongClick = { quoteMenu = true },
            )
            DropdownMenu(expanded = quoteMenu, onDismissRequest = { quoteMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.posts_quotes)) }, onClick = {
                    quoteMenu = false
                    actions.openQuotes(post.id)
                })
                DropdownMenuItem(text = { Text(stringResource(Res.string.posts_retweets)) }, onClick = {
                    quoteMenu = false
                    actions.openReposters(post.id)
                })
            }
        }
        Count(XIcons.Like, counts.likes, null)
        val views = counts.views
        if (views != null) {
            val label = pluralStringResource(Res.plurals.posts_views_count, viewsPluralCount(views), countText(views))
            Count(XIcons.Views, views, label)
        } else {
            Box(Modifier.size(17.dp))
        }
    }
}

/** The plural form of a views label: once abbreviated, the count is "thousands", which takes the many form. */
internal fun viewsPluralCount(views: Long): Int = if (views < 1000) views.toInt() else 1000

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Count(icon: ImageVector, n: Long?, label: String?, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null) {
    val clickable = if (onClick != null) {
        Modifier.clip(CircleShape).combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Row(
        clickable.padding(horizontal = 4.dp, vertical = 4.dp).let { if (label != null) it.semantics { contentDescription = label } else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        if (n != null && n > 0) {
            Text(
                countText(n),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** A glyph at a post's top-right, beside the author: translate and ⋯. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HeaderButton(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .offset(y = (-4).dp)
            .size(32.dp)
            .clip(CircleShape)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * The translate control: translates, or shows the original again; red once
 * X refused; a long press translates every post of the screen.
 */
@Composable
internal fun TranslateButton(translation: PostTranslation, scope: PostScope) {
    val coroutines = rememberCoroutineScope()
    when (translation.status) {
        TranslationStatus.TRANSLATING -> Box(Modifier.offset(y = (-4).dp).size(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        TranslationStatus.TRANSLATED -> HeaderButton(PostIcons.Translate, stringResource(Res.string.posts_show_original), MaterialTheme.colorScheme.primary) {
            translation.showOriginal()
        }
        TranslationStatus.ORIGINAL, TranslationStatus.FAILED -> HeaderButton(
            PostIcons.Translate,
            stringResource(Res.string.posts_translate),
            if (translation.status == TranslationStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            onLongClick = { scope.translateAll?.request() ?: coroutines.launch { translation.translate() } },
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

/**
 * XTA's follow badge (avatar_follow_badge.dart): a small + on the rim of the
 * face of an author not followed. A tap follows them here, never on X; a
 * long press opens their groups.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FollowBadge(author: XUser, size: Dp, avatar: @Composable () -> Unit) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val followed = session?.followed(author.id) != null
    var groups by rememberSaveable(author.id) { mutableStateOf(false) }
    val coroutines = rememberCoroutineScope()
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
                        onClick = { coroutines.launch { graph.sessions.update { it.follow(author, System.currentTimeMillis()) } } },
                        onLongClick = { groups = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val label = stringResource(Res.string.posts_follow)
                Icon(Icons.Filled.Add, label, tint = surface, modifier = Modifier.size(14.dp))
            }
        }
    }
    if (groups) {
        AuthorGroupsSheet(author, onDismiss = { groups = false })
    }
}
