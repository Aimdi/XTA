package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.store.StoreDir
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Posts liked on this device, newest first, kept by [KeptPosts] under
 * `saved/likes`: a like writes that one post, not every like. A like is the
 * heart remembering: no network is ever told.
 */
class LikesStore(dir: StoreDir, private val clock: () -> Long = System::currentTimeMillis) {
    private val kept = KeptPosts(dir, "likes", LikesShape)

    /** The likes with what was kept of each post, newest first; null before [likes] first reads them. */
    val state: StateFlow<List<LikedPost>?> = kept.whole

    /**
     * The likes without their content, newest first: enough to say what is
     * liked, by whom and when, at a fraction of the memory. Null before the
     * first call of any kind reads them.
     */
    val index: StateFlow<List<LikedPost>?> = kept.bare

    /** Every like with its content, which reads each post kept the first time. */
    suspend fun likes(): List<LikedPost> = kept.whole()

    /** The likes as [index] holds them. */
    suspend fun index(): List<LikedPost> = kept.bare()

    /** What was kept of post [id] alone; null when it is not liked or nothing was kept. */
    suspend fun contentOf(id: String): JsonElement? = kept.content(id)

    suspend fun isLiked(id: String): Boolean = index().any { it.id == id }

    /**
     * Likes [id], newest first. The same post can show twice in a feed (a
     * pinned or reposted copy and its own), so liking it again only moves it to
     * the top with [content] as it is now, never a second entry.
     */
    suspend fun like(id: String, authorId: String?, content: JsonElement?): LikedPost = kept.edit { likes ->
        val like = LikedPost(id, authorId, content, clock())
        Edit(listOf(like) + likes.filterNot { it.id == id }, like, fresh = id)
    }

    /** Takes the like back, and deletes what was kept of the post. */
    suspend fun unlike(id: String) {
        kept.edit { likes -> Edit(if (likes.none { it.id == id }) likes else likes.filterNot { it.id == id }, Unit) }
    }

    /** [unlike], answering the like with what was kept of the post, for [putBack]; null when it was not liked. */
    suspend fun unlikeTaking(id: String): TakenLike? = kept.editReading { likes, content ->
        // Only this post's content is read, and the change is one line of the log.
        val took = PutBack.taken(likes, { it.id == id }, { it.id }) ?: return@editReading Edit(likes, null)
        Edit(likes.filterNot { it.id == id }, Taken(took.items.map { it.copy(content = content(it.id)) }, took.order))
    }

    /** Undoes [unlikeTaking]: the like back where it was, as liked then; false when it is liked again since. */
    suspend fun putBack(taken: TakenLike): Boolean = kept.edit { likes ->
        PutBack.into(likes, taken) { it.id }?.let { Edit(it, true, alsoFresh = taken.items.map(LikedPost::id)) } ?: Edit(likes, false)
    }

    /** Everything kept, for the backup. */
    suspend fun content(): List<LikedPost> = likes()

    /** Replaces every like with [likes], newest first, as a restored backup does. */
    suspend fun replace(likes: List<LikedPost>) {
        kept.rewrite { likes.distinctBy { it.id } }
    }

    /** A restore: [merge] gets the likes kept now and gives those to keep (null for no change), under the lock every like takes. */
    suspend fun restore(merge: (List<LikedPost>) -> List<LikedPost>?) {
        kept.rewrite { now -> merge(now)?.distinctBy { it.id } }
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
