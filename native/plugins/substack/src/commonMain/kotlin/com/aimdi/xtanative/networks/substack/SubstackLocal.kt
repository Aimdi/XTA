package com.aimdi.xtanative.networks.substack

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where one piece of what the reader keeps on the device is written, as the
 * text XTA keeps it as: a preference (the read marks, likes, saves, pins,
 * highlights) or a file of the plugin's own.
 */
interface SubstackKept {
    suspend fun read(): String?

    suspend fun write(text: String)
}

/**
 * A value kept as text in [kept]: read once, then each change written in
 * turn, starting from what is there. A value that cannot be read is the empty
 * one ([decode] says so), as XTA's reads of its preferences forgive.
 */
open class SubstackKeptValue<T>(private val kept: SubstackKept, private val empty: T, private val decode: (String) -> T, private val encode: (T) -> String) {
    private val mutable = MutableStateFlow(empty)
    private val mutex = Mutex()
    private var loaded = false

    val state: StateFlow<T> = mutable.asStateFlow()

    suspend fun load(): T = mutex.withLock { loaded() }

    /** [change] applied to what is kept now, written, and shown; what it gave. */
    suspend fun update(change: (T) -> T): T = mutex.withLock {
        val next = change(loaded())
        kept.write(encode(next))
        mutable.value = next
        next
    }

    private suspend fun loaded(): T {
        if (!loaded) {
            mutable.value = kept.read()?.takeIf { it.isNotEmpty() }?.let(decode) ?: empty
            loaded = true
        }
        return mutable.value
    }
}

/** The posts read, by key, newest first: XTA's `plugin.substack.read_ids`, at most [CAP]. */
class SubstackReadMarks(kept: SubstackKept) :
    SubstackKeptValue<Set<String>>(kept, emptySet(), { SubstackPostJson.strings(it).toSet() }, { SubstackPostJson.stringsText(it.toList()) }) {
    suspend fun markRead(posts: Collection<SubstackPost>) = markAll(posts.map { it.key })

    /** A post read is kept under its key; its id goes too, as reads were kept before posts had keys. */
    suspend fun markUnread(post: SubstackPost) {
        update { marks -> marks - setOf(post.key, post.id) }
    }

    fun isRead(post: SubstackPost): Boolean = SubstackPosts.isRead(state.value, post)

    private suspend fun markAll(ids: List<String>) {
        val requested = ids.filter { it.isNotEmpty() }.toSet()
        if (requested.isEmpty()) return
        update { marks -> (requested + marks.filterNot { it in requested }).take(CAP).toCollection(LinkedHashSet()) }
    }

    companion object {
        const val CAP = 400
    }
}

/**
 * Posts liked or saved on this device, newest first, at most [capacity]:
 * XTA's `plugin.substack.liked_posts` and `saved_posts`. Nothing is told to
 * Substack: a heart and a bookmark stay here.
 */
class SubstackPostShelf(kept: SubstackKept, private val capacity: Int) :
    SubstackKeptValue<List<SubstackPost>>(kept, emptyList(), SubstackPostJson::list, SubstackPostJson::listText) {
    /** Whether either copy of [post] is here. */
    fun contains(post: SubstackPost): Boolean = state.value.any { SubstackPosts.same(it, post) }

    /** [post] added first, or taken away when it was here. */
    suspend fun toggle(post: SubstackPost) {
        if (post.id.isEmpty()) return
        update { posts ->
            val rest = posts.filterNot { SubstackPosts.same(it, post) }
            (if (rest.size == posts.size) listOf(post.copy(bodyHtml = null)) + rest else rest).take(capacity)
        }
    }

    companion object {
        const val LIKES = 400
        const val SAVES = 200
    }
}

/** The publications pinned first in the follows, in pin order: XTA's `plugin.substack.pinned_publications`, comma-separated. */
class SubstackPins(kept: SubstackKept) :
    SubstackKeptValue<List<String>>(kept, emptyList(), { text -> text.split(',').map(String::trim).filter(String::isNotEmpty) }, { it.joinToString(",") }) {
    fun isPinned(id: String): Boolean = id in state.value

    suspend fun toggle(id: String) {
        if (id.isEmpty()) return
        update { pins -> if (id in pins) pins - id else pins + id }
    }

    suspend fun forget(id: String) {
        if (id in state.value) update { it - id }
    }
}
