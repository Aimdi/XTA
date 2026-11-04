package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.aimdi.xtanative.networks.pixiv.PixivUserListKind
import com.aimdi.xtanative.networks.pixiv.PixivUserPreview
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept

/**
 * Whom a creator follows, or who follows them. The reader's own following
 * offers Pixiv's public and private follows and those made on this device.
 */
@Composable
internal fun PixivUsersScreen(kind: PixivUserListKind, userId: Long) {
    val runtime = LocalPixiv.current
    val own = userId == 0L || userId == runtime.ownId
    var restrict by rememberSaveable { mutableStateOf(PixivFavoritesSource.PUBLIC) }
    PluginScaffold(title = pluginString(if (kind == PixivUserListKind.FOLLOWING) Strings.profile_following else Strings.profile_followers), onBack = runtime.host::back) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (own && kind == PixivUserListKind.FOLLOWING) {
                ChipRow {
                    Chip(pluginString(Strings.follow_public), restrict == PixivFavoritesSource.PUBLIC, { restrict = PixivFavoritesSource.PUBLIC })
                    Chip(pluginString(Strings.follow_private), restrict == PixivFavoritesSource.PRIVATE, { restrict = PixivFavoritesSource.PRIVATE })
                    Chip(pluginString(Strings.follows_here), restrict == PixivFavoritesSource.DEVICE, { restrict = PixivFavoritesSource.DEVICE })
                }
            }
            Box(Modifier.weight(1f)) {
                if (restrict == PixivFavoritesSource.DEVICE) FollowsHere() else UserList(kind, userId, restrict.api)
            }
        }
    }
}

@Composable
private fun UserList(kind: PixivUserListKind, userId: Long, restrict: String) {
    val runtime = LocalPixiv.current
    val list = rememberKept("pixiv-users-${kind.name}-$userId-$restrict") { scope ->
        PixivList<PixivUserPreview>(scope, { it.user.id }, { next ->
            runtime.social.userList(kind, if (userId == 0L) runtime.social.ownUserId() else userId, restrict, next)
        })
    }
    val empty = if (kind == PixivUserListKind.FOLLOWING) Strings.profile_following_empty else Strings.profile_followers_empty
    PixivUsersFeed(list, pluginString(empty), PaddingValues())
}

/** The creators followed on this device, the latest first; a tap opens them. */
@Composable
internal fun FollowsHere() {
    val runtime = LocalPixiv.current
    val follows = PixivFollows.of(pixivSettings()).asReversed()
    if (follows.isEmpty()) {
        PixivEmpty(pluginString(Strings.follows_here_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(follows, key = { it.id }) { follow ->
            val user = follow.toUser()
            ListItem(
                headlineContent = { Text(user.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = user.account.takeIf { it.isNotEmpty() }?.let { { Text("@$it", maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                leadingContent = { PixivAvatar(user.avatarUrl, user.name) },
                trailingContent = { FollowButton(user) },
                modifier = Modifier.clickable { runtime.openUser(user.id) },
            )
        }
    }
}

/** Creators Pixiv suggests, as cards with their works. */
@Composable
internal fun PixivRecommendedUsersScreen() {
    val runtime = LocalPixiv.current
    val list = rememberKept("pixiv-recommended-users") { scope -> PixivList<PixivUserPreview>(scope, { it.user.id }, { next -> runtime.discovery.recommendedUsers(next) }) }
    PluginScaffold(title = pluginString(Strings.recommended_users), onBack = runtime.host::back) { padding ->
        Box(Modifier.padding(padding)) { PixivUsersFeed(list, pluginString(Strings.recommended_users_empty)) }
    }
}
