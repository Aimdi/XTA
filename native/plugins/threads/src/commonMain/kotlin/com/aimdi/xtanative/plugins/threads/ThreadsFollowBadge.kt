package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.plugins.addToGroupText
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * Where a follow badge sits on a face, as the app draws X's
 * (PostGeometry's followBadge*): a disc about two fifths of the face, at most
 * 20 dp, centred on the rim at the lower right (45°), answering in a 48 dp
 * target.
 */
internal object ThreadsFollowBadgeGeometry {
    private const val COS_45 = 0.70710677f
    const val TARGET = 48f

    fun disc(avatar: Float): Float = (avatar * 0.42f).coerceIn(14f, 20f)

    fun ring(disc: Float): Float = if (disc >= 18f) 2f else 1.5f

    /** The disc's centre from the face's top start corner, in both directions. */
    fun center(avatar: Float): Float = avatar / 2 * (1 + COS_45)

    /** Whether a card shows the badge: the follows are known and this author is not among them. */
    fun shown(followsKnown: Boolean, followed: Boolean): Boolean = followsKnown && !followed
}

/**
 * The author's face on a Threads card with X's follow badge: a small + on its
 * rim while the author is not followed here. A tap follows them on this
 * device, never on Threads; a long press files them in a group, which follows
 * them too. [avatar] is the face itself, [size] wide.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ThreadsFollowBadge(app: ThreadsApp, account: ThreadsAccount, shown: Boolean, size: Dp, avatar: @Composable () -> Unit) {
    Box(Modifier.size(size)) {
        avatar()
        if (!shown) return@Box
        val disc = ThreadsFollowBadgeGeometry.disc(size.value)
        val ring = ThreadsFollowBadgeGeometry.ring(disc).dp
        val target = ThreadsFollowBadgeGeometry.TARGET.dp
        val at = ThreadsFollowBadgeGeometry.center(size.value).dp - target / 2
        val page = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .offset(at, at)
                .size(target)
                .combinedClickable(
                    interactionSource = null,
                    indication = ripple(bounded = false, radius = disc.dp),
                    role = Role.Button,
                    onClickLabel = pluginString(Strings.follow),
                    onLongClickLabel = addToGroupText(),
                    onClick = { app.follow(account) },
                    onLongClick = { app.addToGroup(account) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(disc.dp + ring * 2)
                    .background(page, CircleShape)
                    .border(ring, page, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(disc.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = page, modifier = Modifier.size(disc.dp * 0.75f))
                }
            }
        }
    }
}
