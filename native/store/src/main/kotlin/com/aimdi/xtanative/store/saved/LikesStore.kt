package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.store.StoreDir
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Posts liked on this device, newest first, in `saved/likes.json`. A like is
 * the heart remembering: no network is ever told.
 */
class LikesStore(dir: StoreDir, private val clock: () -> Long = System::currentTimeMillis) {
    private val stored = StoredJson(dir, "saved/likes.json", emptyList(), SavedCodec::likes, SavedCodec::likes)

    /** The likes once read, newest first; null before the first call reads them. */
    val state: StateFlow<List<LikedPost>?> = stored.state

    suspend fun likes(): List<LikedPost> = stored.get()

    suspend fun isLiked(id: String): Boolean = likes().any { it.id == id }

    /**
     * Likes [id], newest first. The same post can show twice in a feed (a
     * pinned or reposted copy and its own), so liking it again only moves it to
     * the top with [content] as it is now, never a second entry.
     */
    suspend fun like(id: String, authorId: String?, content: JsonElement?): LikedPost = stored.updateWith { likes ->
        val like = LikedPost(id, authorId, content, clock())
        (listOf(like) + likes.filterNot { it.id == id }) to like
    }

    suspend fun unlike(id: String) {
        stored.update { likes -> if (likes.none { it.id == id }) likes else likes.filterNot { it.id == id } }
    }

    /** Everything kept, for the backup. */
    suspend fun content(): List<LikedPost> = likes()

    /** Replaces every like with [likes], newest first, as a restored backup does. */
    suspend fun replace(likes: List<LikedPost>) {
        stored.update { likes.distinctBy { it.id } }
    }
}

/** One heading of likes broken down by group: [groupId] null holds the likes whose author is in none of the groups shown. */
data class LikesSection<T>(val groupId: String?, val items: List<T>) {
    val isUngrouped: Boolean get() = groupId == null
}

/**
 * [items] split into one section per group of [groups], in their order, then
 * the likes whose author is in none of them. A post whose author is in two
 * groups shows under both: picking one would hide it from the other. A like
 * kept before its author was recorded counts as ungrouped rather than being
 * dropped, and a group none of the likes come from is left out.
 */
fun <T> likesByGroup(items: List<T>, authorOf: (T) -> String?, groups: List<FollowGroup>): List<LikesSection<T>> {
    val groupsOfAuthor = groups.flatMap { group -> group.memberIds.map { it to group.id } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }
    fun groupsOf(item: T): Set<String> = authorOf(item)?.let { groupsOfAuthor[it] }.orEmpty()
    val inGroups = groups.map { group -> LikesSection(group.id, items.filter { group.id in groupsOf(it) }) }
    val shown = groups.map { it.id }.toSet()
    val ungrouped = items.filter { item -> groupsOf(item).none { it in shown } }
    return (inGroups + LikesSection(null, ungrouped)).filter { it.items.isNotEmpty() }
}
