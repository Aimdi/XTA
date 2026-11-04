package com.aimdi.xtanative.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_follow
import com.aimdi.xtanative.ui.generated.resources.posts_followed
import com.aimdi.xtanative.ui.post.PostActions
import java.util.UUID
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * One person in a list (followers, following, reposters), as XTA's UserTile:
 * the face, the name and its check, the handle, and following here at the
 * end; the row opens the profile.
 */
@Composable
fun UserRow(user: XUser, actions: PostActions) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val followed = session?.followed(user.id) != null
    var managing by rememberSaveable(user.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
        IconButton(onClick = { if (followed) managing = true else scope.launch { Follows.follow(graph, user) } }) {
            Icon(if (followed) Icons.Filled.Check else Icons.Filled.Add, stringResource(if (followed) Res.string.posts_followed else Res.string.posts_follow))
        }
    }
    if (managing) AuthorGroupsSheet(user, onDismiss = { managing = false })
}

/** Following someone here, never on X: the session's changes, as a profile and a post's author make them. */
internal object Follows {
    suspend fun change(graph: AppGraph, change: (Session) -> Session) = graph.sessions.update(change)

    suspend fun follow(graph: AppGraph, user: XUser) = change(graph) { it.follow(user, System.currentTimeMillis()) }

    suspend fun unfollow(graph: AppGraph, user: XUser) = change(graph) { it.unfollow(user.id) }

    suspend fun showInFeed(graph: AppGraph, user: XUser, shown: Boolean) = change(graph) { it.withInFeed(user.id, shown) }

    /** Into [groupId] or out of it; into a group follows the account here first, as XTA's group picker does. */
    suspend fun putInGroup(graph: AppGraph, user: XUser, groupId: String, member: Boolean) = change(graph) { session ->
        (if (member) session.follow(user, System.currentTimeMillis()) else session).withMembership(groupId, user.id, member)
    }

    suspend fun newGroup(graph: AppGraph, user: XUser, name: String) = change(graph) { session ->
        session.follow(user, System.currentTimeMillis()).withNewGroup(UUID.randomUUID().toString(), name, listOf(user.id))
    }
}

/**
 * Where [user] is followed here: the Following feed, the groups (filing in a
 * group follows), new group, unfollow, as XTA's group picker offers them for
 * a post's author or a person in a list.
 */
@Composable
fun AuthorGroupsSheet(user: XUser, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val follow = FollowUi.of(session ?: Session(), user.id, user.screenName)
    val scope = rememberCoroutineScope()
    FollowSheet(
        screenName = user.screenName,
        follow = follow,
        onInFeed = { shown -> scope.launch { Follows.showInFeed(graph, user, shown) } },
        onGroup = { group, member -> scope.launch { Follows.putInGroup(graph, user, group, member) } },
        onNewGroup = { name -> scope.launch { Follows.newGroup(graph, user, name) } },
        onUnfollow = { scope.launch { Follows.unfollow(graph, user) } },
        onDismiss = onDismiss,
    )
}
