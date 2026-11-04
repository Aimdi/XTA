package com.aimdi.xtanative.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.search.FollowMenu
import com.aimdi.xtanative.ui.search.FollowTarget

/**
 * One person in a list (followers, following, reposters), as XTA's UserTile:
 * the face, the name and its check, the handle, and XTA's follow button at
 * the end (Subscribe or Unsubscribe, Add to group, the main feed); the row
 * opens the profile.
 */
@Composable
fun UserRow(user: XUser, actions: PostActions) {
    Row(
        Modifier.fillMaxWidth().clickable { actions.openProfile(user.screenName) }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.largeAvatarUrl, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (user.verified) Icon(XIcons.Verified, null, tint = Color.Unspecified, modifier = Modifier.padding(start = 4.dp).size(16.dp))
            }
            Text("@" + user.screenName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FollowMenu(FollowTarget.Account(user))
    }
}
