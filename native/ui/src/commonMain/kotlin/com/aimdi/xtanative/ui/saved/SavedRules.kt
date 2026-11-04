package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.store.saved.LikedPost
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.LocalPosts
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedEmptyKind
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedPost
import com.aimdi.xtanative.store.saved.SavedSort
import com.aimdi.xtanative.store.saved.SavedTabs
import com.aimdi.xtanative.store.saved.SavedView
import com.aimdi.xtanative.store.saved.likesByGroup
import com.aimdi.xtanative.ui.media.MediaGridItem
import com.aimdi.xtanative.ui.media.MediaGridItems

/** The Archive's menu, in XTA's order (saved_chrome.dart). */
enum class SavedMenuAction { SEARCH_LIBRARY, CREATE_FOLDER, MANAGE_FOLDERS, DOWNLOADS, OFFLINE, CLEANUP, SETTINGS }

/** What Filter and sort offers. */
enum class SavedFilterAction { SORT_NEWEST, SORT_OLDEST, MEDIA_ONLY, SELECT }

/**
 * Filter and sort in the open tab: the order and Select for saved posts,
 * Media only for saved posts and likes; with neither the button is off.
 */
data class SavedFilterMenu(val sortable: Boolean, val showMedia: Boolean) {
    val enabled: Boolean get() = sortable || showMedia

    companion object {
        fun of(view: SavedView) = SavedFilterMenu(view.sortable, view.tab != SavedTabs.NOTES)
    }
}

/** [this] after a choice in Filter and sort: Select starts a selection, which the media grid gives way to. */
fun SavedView.after(action: SavedFilterAction): SavedView = when (action) {
    SavedFilterAction.SORT_NEWEST -> copy(sort = SavedSort.NEWEST)
    SavedFilterAction.SORT_OLDEST -> copy(sort = SavedSort.OLDEST)
    SavedFilterAction.MEDIA_ONLY -> copy(mediaOnly = !mediaOnly)
    SavedFilterAction.SELECT -> beginSelection()
}

/** The line the lock beside the title says: what the open tab holds stays on this device. */
enum class SavedNotice {
    SAVES,
    LIKES,
    NOTES,
    ;

    companion object {
        fun of(tab: String): SavedNotice = when (tab) {
            SavedTabs.FAVORITES -> LIKES
            SavedTabs.NOTES -> NOTES
            else -> SAVES
        }
    }
}

/** A chip of the tab row; a folder's carries [folder], and a long press can rename or delete it. */
data class SavedChip(val token: String, val folder: SavedFolder? = null) {
    val editable: Boolean get() = folder != null
}

/** The tab row's chips, in the reader's order: the built-in tabs settings show, and the folders. */
fun savedChips(folders: List<SavedFolder>, storedOrder: List<String>, showAll: Boolean, showUnfiled: Boolean, showFavorites: Boolean): List<SavedChip> {
    val ordered = SavedLibrary.ordered(folders)
    val tokens = SavedTabs.shown(SavedTabs.ordered(ordered, storedOrder), ordered, showAll, showUnfiled, showFavorites)
    return tokens.map { token -> SavedChip(token, ordered.firstOrNull { it.id == token }.takeIf { !SavedTabs.isBuiltIn(token) }) }
}

/**
 * A tile of the grid Media only draws when every post shown is X's: [item],
 * a picture, video or broadcast of the kept post (the post shown, for a
 * repost); [savedId] is what a long press removes.
 */
data class SavedMediaTile(val savedId: String, val item: MediaGridItem) {
    val postId: String get() = item.post.id
}

/**
 * A row of the likes broken down by group: a heading ([name] null for the
 * likes in no group), or a like under the heading of [groupId]; a like whose
 * author is in two groups is under both.
 */
sealed interface LikesRow {
    val key: String

    data class Heading(val groupId: String?, val name: String?) : LikesRow {
        override val key: String get() = "group:" + (groupId ?: "")
    }

    data class Like(val like: LikedPost, val groupId: String?) : LikesRow {
        override val key: String get() = "like:" + (groupId ?: "") + ":" + like.id
    }
}

/** A note Notes lists: how many replies it has, and where its thread opens. */
data class NoteRow(val note: LocalPost, val replies: Int, val threadRoot: String)

/** What the open tab shows. */
sealed interface SavedBody {
    data class Empty(val kind: SavedEmptyKind) : SavedBody

    /** Media only over X's posts; with no tile, "no post with media" is said. */
    data class Grid(val tiles: List<SavedMediaTile>) : SavedBody

    data class Posts(val posts: List<SavedPost>) : SavedBody

    data class Likes(val likes: List<LikedPost>) : SavedBody

    data class LikesByGroup(val rows: List<LikesRow>) : SavedBody

    data class Notes(val notes: List<NoteRow>) : SavedBody
}

/**
 * What [view] shows of what the device keeps (saved_screen.dart): saved
 * posts, likes (or likes under one heading per group of [groups], in their
 * order) or notes. Media only becomes a grid when every post in view is X's;
 * among other networks' posts it keeps those with pictures or video.
 */
fun savedBody(view: SavedView, saved: List<SavedPost>, likes: List<LikedPost>, notes: List<LocalPost>, groups: List<FollowGroup>): SavedBody =
    when (view.tab) {
        SavedTabs.NOTES -> notesBody(view, notes)
        SavedTabs.FAVORITES -> likesBody(view, likes, groups)
        else -> savedPostsBody(view, saved)
    }

private fun savedPostsBody(view: SavedView, saved: List<SavedPost>): SavedBody {
    val inView = view.copy(mediaOnly = false).posts(saved)
    if (view.mediaOnly && inView.isNotEmpty() && inView.all { it.about.kind == SavedContent.Kind.X }) {
        return SavedBody.Grid(inView.flatMap { mediaTiles(it.id, it.about) })
    }
    val shown = if (view.mediaOnly) inView.filter { it.about.hasMedia } else inView
    return if (shown.isEmpty()) SavedBody.Empty(view.emptyKind) else SavedBody.Posts(shown)
}

private fun likesBody(view: SavedView, likes: List<LikedPost>, groups: List<FollowGroup>): SavedBody {
    val inView = view.copy(mediaOnly = false).likes(likes)
    if (view.mediaOnly && inView.isNotEmpty() && inView.all { it.about.kind == SavedContent.Kind.X }) {
        return SavedBody.Grid(inView.flatMap { mediaTiles(it.id, it.about) })
    }
    val shown = if (view.mediaOnly) inView.filter { it.about.hasMedia } else inView
    return when {
        shown.isEmpty() -> SavedBody.Empty(view.emptyKind)
        view.likesByGroup -> SavedBody.LikesByGroup(likesRows(shown, groups))
        else -> SavedBody.Likes(shown)
    }
}

private fun notesBody(view: SavedView, notes: List<LocalPost>): SavedBody {
    val roots = view.notes(notes)
    if (roots.isEmpty()) return SavedBody.Empty(view.emptyKind)
    return SavedBody.Notes(roots.map { NoteRow(it, LocalPosts.directReplyCount(notes, it.id), LocalPosts.threadRootId(notes, it.id)) })
}

/** The likes under one heading per group, in [groups]' order, then those whose author is in none. */
fun likesRows(likes: List<LikedPost>, groups: List<FollowGroup>): List<LikesRow> {
    val names = groups.associate { it.id to it.name }
    return likesByGroup(likes, { it.authorId }, groups).flatMap { section ->
        listOf(LikesRow.Heading(section.groupId, section.groupId?.let(names::get))) + section.items.map { LikesRow.Like(it, section.groupId) }
    }
}

/** The tiles of a kept X post, as the grids draw them (media_grid_item.dart); a repost's are those of the post reposted. */
fun mediaTiles(savedId: String, content: SavedContent): List<SavedMediaTile> {
    val post = content.xPost ?: return emptyList()
    return MediaGridItems.of(post).map { SavedMediaTile(savedId, it) }
}

/** The ids Select all takes: the saved posts the list shows. */
fun visibleIds(body: SavedBody): List<String> = (body as? SavedBody.Posts)?.posts?.map { it.id }.orEmpty()
