package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_collapse
import com.aimdi.xtanative.ui.generated.resources.posts_expand
import com.aimdi.xtanative.ui.generated.resources.posts_replies_hidden
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What collapses the reply branch the posts drawn here belong to, in an
 * opened post's threaded replies ([PostScreen]); null everywhere else, where
 * a post's rail is only a line.
 */
internal val LocalBranchCollapse = compositionLocalOf<(() -> Unit)?> { null }

/**
 * A press on the post's rail, the [PostGeometry.TOUCH_TARGET] wide strip
 * through its avatar ([PostGeometry.onRail]), runs [collapse]: a tap, or a
 * primary click of the mouse. It goes after the card's own press in the
 * chain, so it hears a press before the card does and after everything in
 * the card: the avatar, a link, a picture take theirs first, and a press
 * anywhere else is the card's as before. A scroll that starts on the rail
 * stays a scroll. TalkBack gets it as the card's "Collapse" action.
 * Nothing while [collapse] is null.
 */
internal fun Modifier.collapsesBranch(collapse: (() -> Unit)?): Modifier = if (collapse == null) this else composed {
    val latest by rememberUpdatedState(collapse)
    val label = stringResource(Res.string.posts_collapse)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    pointerInput(rtl) {
        awaitEachGesture {
            // Unconsumed only: what is inside the card consumed its own press already.
            val down = awaitFirstDown()
            val primary = down.type != PointerType.Mouse || currentEvent.buttons.isPrimaryPressed
            val onRail = PostGeometry.onRail(down.position.x.toDp().value, size.width.toDp().value, rtl)
            if (!primary || !onRail) return@awaitEachGesture
            down.consume()
            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
            up.consume()
            latest()
        }
    }.semantics {
        collapse(label) {
            latest()
            true
        }
    }
}

/**
 * A collapsed reply branch in its reply's place ([ThreadRow.Collapsed]): the
 * author's picture on the rail, the name, and how many replies the row
 * stands for; a tap opens the branch again.
 */
@Composable
internal fun CollapsedBranch(row: ThreadRow.Collapsed, onExpand: () -> Unit) {
    val author = row.node.thread.posts.firstOrNull()?.shown?.author
    val label = stringResource(Res.string.posts_expand)
    val small = PostGeometry.COLLAPSED_AVATAR.dp
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PostGeometry.TOUCH_TARGET.dp)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onExpand)
                .semantics {
                    expand(label) {
                        onExpand()
                        true
                    }
                }
                .padding(
                    start = (ThreadTree.indentLevels(row.visualDepth) * 40).dp + (PostGeometry.RAIL_CENTER.dp - small / 2),
                    end = PostGeometry.END_PADDING.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(author?.avatarUrl, small)
            Spacer(Modifier.width(PostGeometry.COLUMN_START.dp - PostGeometry.RAIL_CENTER.dp - small / 2))
            if (author != null) {
                Text(
                    author.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                )
            }
            Text(
                pluralStringResource(Res.plurals.posts_replies_hidden, row.hidden, row.hidden),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        Hairline()
    }
}
