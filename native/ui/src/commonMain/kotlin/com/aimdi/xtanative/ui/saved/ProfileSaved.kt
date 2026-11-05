package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.saved.ArchiveFilter
import com.aimdi.xtanative.store.saved.ArchiveItem
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.profileArchive
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.saved_bookmarks
import com.aimdi.xtanative.ui.generated.resources.saved_empty_likes
import com.aimdi.xtanative.ui.generated.resources.saved_empty_saved
import com.aimdi.xtanative.ui.generated.resources.saved_sensitive_profile
import com.aimdi.xtanative.ui.generated.resources.saved_show
import com.aimdi.xtanative.ui.generated.resources.saved_tab_all
import com.aimdi.xtanative.ui.generated.resources.saved_tab_likes
import com.aimdi.xtanative.ui.post.PostActions
import org.jetbrains.compose.resources.stringResource

/** A profile's Saved tab as it stands: which kept posts it shows, and those of the account. */
@Stable
class ProfileSavedState internal constructor(filter: ArchiveFilter, val items: List<ArchiveItem>?, private val pick: (ArchiveFilter) -> Unit) {
    val filter: ArchiveFilter = filter

    fun select(filter: ArchiveFilter) = pick(filter)
}

/**
 * The saved and liked posts of the account [userId] (_saved.dart): its saves
 * in their order, then its likes not already saved, or one of the two. All
 * of it is the device's; nothing is read from X.
 */
@Composable
fun rememberProfileSaved(userId: String?): ProfileSavedState {
    val graph = LocalGraph.current
    val library by graph.saved.state.collectAsStateWithLifecycle()
    val likes by graph.likes.state.collectAsStateWithLifecycle()
    LaunchedEffect(graph) {
        graph.saved.library()
        graph.likes.likes()
    }
    var filter by rememberSaveable { mutableStateOf(ArchiveFilter.ALL) }
    val loaded = library
    val liked = likes
    val items = if (loaded == null || liked == null) null else userId?.let { profileArchive(loaded.posts, liked, it, filter) }.orEmpty()
    return ProfileSavedState(filter, items) { filter = it }
}

/**
 * A profile's Saved tab, for the profile's list: All, Likes and Bookmarks
 * as chips, then the posts, or what it says when there are none. A profile X
 * marks as sensitive asks before showing them.
 */
fun LazyListScope.profileSaved(state: ProfileSavedState, user: XUser?, actions: PostActions, revealed: Boolean, onReveal: () -> Unit) {
    item(key = "profile-saved-filter", contentType = "filter") { ProfileSavedFilter(state.filter, state::select) }
    if (user?.sensitive == true && !revealed) {
        item(key = "profile-saved-sensitive") { SensitiveGate(onReveal) }
        return
    }
    val items = state.items ?: return
    if (items.isEmpty()) {
        item(key = "profile-saved-empty") {
            val likes = profileSavedEmptyIsLikes(state.filter)
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (likes) Icons.Filled.FavoriteBorder else SavedIcons.BookmarkBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(if (likes) Res.string.saved_empty_likes else Res.string.saved_empty_saved),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        return
    }
    items(items, key = { "profile-saved-" + it.id }, contentType = { "kept" }) { item ->
        KeptPost(SavedContent.of(item.content), actions)
        Hairline()
    }
}

/** All, Likes and Bookmarks, as the profile's filter bar offers them. */
@Composable
fun ProfileSavedFilter(selected: ArchiveFilter, onSelect: (ArchiveFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(ArchiveFilter.ALL, Icons.AutoMirrored.Filled.List, Res.string.saved_tab_all),
            Triple(ArchiveFilter.LIKES, Icons.Filled.FavoriteBorder, Res.string.saved_tab_likes),
            Triple(ArchiveFilter.BOOKMARKS, SavedIcons.BookmarkBorder, Res.string.saved_bookmarks),
        ).forEach { (filter, icon, label) ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(label)) },
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun SensitiveGate(onReveal: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.saved_sensitive_profile), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onReveal) { Text(stringResource(Res.string.saved_show)) }
    }
}
