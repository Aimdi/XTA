package com.aimdi.xtanative.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_follow
import com.aimdi.xtanative.ui.generated.resources.posts_followed
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.search.FollowMenu
import com.aimdi.xtanative.ui.search.FollowTarget
import com.aimdi.xtanative.ui.search.Followed
import com.aimdi.xtanative.ui.search.GroupsSheet
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import org.jetbrains.compose.resources.stringResource

/**
 * A profile's Subscribe / Subscribed (profile_chrome.dart's ProfileFollowFace
 * over user.dart's FollowButton): its icon says what the follow is (a plus,
 * a check, or the eye struck through for an account kept out of the main
 * feed), and a tap opens XTA's menu: Subscribe or Unsubscribe, Add to group,
 * Hide from or Show in the main feed. All of it stays in XTA Native.
 */
@Composable
fun ProfileFollowButton(user: XUser) {
    FollowMenu(FollowTarget.Account(user)) { state, open -> FollowFace(state, open) }
}

@Composable
private fun FollowFace(state: Followed, onClick: () -> Unit) {
    val color = if (state.followed) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary
    val icon = when {
        !state.followed -> Icons.Filled.Add
        state.inFeed -> Icons.Filled.Check
        else -> PostIcons.VisibilityOff
    }
    Row(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            stringResource(if (state.followed) Res.string.posts_followed else Res.string.posts_follow),
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
        Icon(GroupIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp).size(18.dp))
    }
}

/** Add to group for [user], an author or a person in a list (user.dart's pickUserGroups): XTA's membership sheet, which follows them first. */
@Composable
fun AuthorGroupsSheet(user: XUser, onDismiss: () -> Unit) = GroupsSheet(FollowTarget.Account(user), onDismiss)
