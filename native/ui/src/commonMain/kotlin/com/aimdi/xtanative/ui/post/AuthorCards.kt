package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_followers
import com.aimdi.xtanative.ui.generated.resources.posts_following
import com.aimdi.xtanative.ui.generated.resources.posts_private_profile
import com.aimdi.xtanative.ui.theme.edgeColor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the card X's site shows over a post's author tells, from what the
 * post already carries about them: nothing here is asked of the network.
 */
object AuthorCard {
    /** The bio's lines the card shows at most, before an ellipsis. */
    const val BIO_LINES = 4

    /**
     * The card's facts: [bio] with its links written as X displays them, null
     * when the post's author has none; [following] and [followers] null where
     * X did not send them with the post.
     */
    data class Facts(
        val name: String,
        val handle: String,
        val bio: String?,
        val following: Long?,
        val followers: Long?,
        val locked: Boolean,
    )

    fun of(user: XUser): Facts = Facts(
        name = user.name.ifBlank { user.screenName },
        handle = user.screenName,
        bio = XText.bio(user).joinToString("") { it.text }.trim().ifEmpty { null },
        following = user.following,
        followers = user.followers,
        locked = user.protected,
    )

    /**
     * Where a card [card] big goes in a window [window] big, for an author
     * shown at [anchor]: under it, [gap] apart, or above it where it would
     * run off the window's foot; its start at the author's, kept [margin]
     * inside the window's sides.
     */
    fun position(anchor: IntRect, window: IntSize, card: IntSize, gap: Int, margin: Int): IntOffset {
        val below = anchor.bottom + gap
        val above = anchor.top - gap - card.height
        val y = when {
            below + card.height <= window.height - margin -> below
            above >= margin -> above
            else -> below.coerceAtMost(window.height - margin - card.height).coerceAtLeast(margin)
        }
        val x = anchor.left.coerceAtMost(window.width - margin - card.width).coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}

/**
 * [content], a post's avatar or author name, with a card about [user]
 * (picture, name, checks, handle, bio, counts) shown while the pointer rests
 * on it, as on X's site; [onOpen] opens the profile from the card. Only what
 * the post carries is shown, the picture at [avatarUrl], the one the post
 * already loaded. Nothing more on Android, where there is no pointer to rest.
 */
@Composable
internal expect fun AuthorHoverCard(user: XUser, avatarUrl: String?, onOpen: () -> Unit, content: @Composable () -> Unit)

/** The card [AuthorHoverCard] shows: a click anywhere on it opens the profile. */
@Composable
internal fun AuthorCardBody(user: XUser, avatarUrl: String?, onOpen: () -> Unit) {
    val facts = remember(user) { AuthorCard.of(user) }
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Dp.Hairline, edgeColor()),
        shadowElevation = 8.dp,
        modifier = Modifier.width(300.dp),
    ) {
        Column(Modifier.clickable(onClick = onOpen).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // The picture the post already loaded, so the card asks for nothing new.
            Avatar(avatarUrl, 56.dp, Modifier.padding(bottom = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TipText(
                    facts.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // The checks without an organisation's badge, which is a picture the post did not load.
                VerifiedBadges(remember(user) { user.copy(affiliation = null) }, 18.dp, Modifier.padding(start = 2.dp))
                if (facts.locked) {
                    Icon(Icons.Filled.Lock, stringResource(Res.string.posts_private_profile), tint = grey, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                }
            }
            TipText("@" + facts.handle, style = MaterialTheme.typography.bodyMedium, color = grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
            facts.bio?.let {
                TipText(it, style = MaterialTheme.typography.bodyMedium, maxLines = AuthorCard.BIO_LINES, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
            if (facts.following != null || facts.followers != null) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    facts.following?.let { CardCount(it, Res.string.posts_following) }
                    facts.followers?.let { CardCount(it, Res.string.posts_followers) }
                }
            }
        }
    }
}

/** "1.2K Followers", the number strong, as the profile writes its counts. */
@Composable
private fun CardCount(n: Long, label: StringResource) {
    val number = countText(n)
    val word = stringResource(label)
    val strong = MaterialTheme.colorScheme.onSurface
    Text(
        remember(number, word, strong) {
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(number) }
                append(" ")
                append(word)
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
