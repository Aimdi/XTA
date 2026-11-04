package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.LocalMarkRow
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray

/**
 * Likes that live on this device (threads_likes_store.dart): Meta is never
 * told, the heart remembers. Which posts are liked is XTA's
 * `threads_local_like` rows (its backup's `threadsLocalLikes`), kept in the
 * plugin's folder; the posts themselves, so the Liked tab can show them, are
 * in XTA's `plugin.threads.liked_posts`, newest first.
 */
internal class ThreadsLikes(
    private val file: StoreFile,
    private val settings: suspend () -> Settings,
    private val update: suspend ((Settings) -> Settings) -> Unit,
) {
    private val mutable = MutableStateFlow<Set<String>?>(null)

    /** The liked posts' ids; null until the file has been read. */
    val state: StateFlow<Set<String>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): Set<String> = mutex.withLock { loaded() }

    fun isLiked(id: String): Boolean = mutable.value?.contains(id) == true

    /** Likes [post] on this device, or takes the like back. */
    suspend fun toggle(post: ThreadsPost) = mutex.withLock {
        val ids = loaded()
        val liked = post.id in ids
        val next = if (liked) ids - post.id else ids + post.id
        save(next)
        update { stored -> stored.with(ThreadsPrefs.likedPosts, ThreadsPostJson.listText(toggled(posts(stored), post, liked))) }
    }

    /** The rows as XTA's backup writes `threadsLocalLikes`. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    suspend fun import(text: String) = mutex.withLock { save(decode(text)) }

    /** The liked posts as the Liked tab shows them: those kept whose like is still held. */
    suspend fun posts(): List<ThreadsPost> = liked(settings(), load())

    private suspend fun loaded(): Set<String> = mutable.value ?: decode(file.read().orEmpty()).also { mutable.value = it }

    private suspend fun save(ids: Set<String>) {
        file.write(encode(ids))
        mutable.value = ids
    }

    companion object {
        /** As many posts as XTA keeps (threadsLikedPostsCap). */
        const val CAP = 400

        fun posts(settings: Settings): List<ThreadsPost> = ThreadsPostJson.list(settings[ThreadsPrefs.likedPosts])

        fun liked(settings: Settings, ids: Set<String>?): List<ThreadsPost> = posts(settings).filter { ids.orEmpty().contains(it.id) }

        /** [posts] without [post] when it [wasLiked], else with it first. */
        fun toggled(posts: List<ThreadsPost>, post: ThreadsPost, wasLiked: Boolean): List<ThreadsPost> {
            val others = posts.filter { it.id != post.id }
            return if (wasLiked) others else (listOf(post) + others).take(CAP)
        }

        fun encode(ids: Set<String>): String = JsonArray(ids.map { LocalMarkRow(it).toJson() }).toString()

        fun decode(text: String): Set<String> = J.parse(text).list.map { LocalMarkRow.from(it).id }.filterTo(LinkedHashSet()) { it.isNotEmpty() }
    }
}
