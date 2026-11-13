package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsConversation
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.store.StoreFile
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * How a conversation is kept on the device ([ThreadsConversationShelf]): the
 * post, what it answers and its author's thread, then as many reply threads
 * as fit in [POSTS] posts (each cut to its first [CHAIN_POSTS]) and the first
 * [RELATED] suggestions. Never the cursor to the replies after these: what is
 * kept is shown only until the page is read again.
 */
internal object ThreadsConversationsKept {
    /** How many posts a kept conversation holds at most, all its parts together. */
    const val POSTS = 40
    const val CHAIN_POSTS = 6
    const val RELATED = 6

    /** What of [conversation] is kept; null for one not read yet, which has nothing to keep. */
    fun trimmed(conversation: ThreadsConversation): ThreadsConversation? {
        if (!conversation.loaded) return null
        val own = conversation.ancestors.size + 1 + conversation.continuation.size
        var room = POSTS - own
        val replies = conversation.replies.map { it.take(CHAIN_POSTS) }.takeWhile { chain -> (chain.size <= room).also { if (it) room -= chain.size } }
        val ids = replies.flatten().mapTo(HashSet()) { it.id }
        return conversation.copy(
            replies = replies,
            levels = conversation.levels.filterKeys { it in ids },
            unfinished = conversation.unfinished.filterTo(HashSet()) { it in ids },
            moreReplies = conversation.moreReplies || replies.size < conversation.replies.size,
            repliesNext = null,
            related = conversation.related.take(RELATED),
        )
    }

    fun json(conversation: ThreadsConversation): JsonObject = JsonObject(
        mapOf(
            "focus" to ThreadsPostJson.json(conversation.focus),
            "ancestors" to posts(conversation.ancestors),
            "continuation" to posts(conversation.continuation),
            "replies" to JsonArray(conversation.replies.map(::posts)),
            "levels" to JsonObject(conversation.levels.mapValues { JsonPrimitive(it.value) }),
            "unfinished" to JsonArray(conversation.unfinished.map(::JsonPrimitive)),
            "moreReplies" to JsonPrimitive(conversation.moreReplies),
            "related" to posts(conversation.related),
        ),
    )

    /** The conversation [row] keeps, as read; null without its post. */
    fun conversationOf(row: J): ThreadsConversation? {
        val focus = ThreadsPostJson.post(row["focus"]) ?: return null
        return ThreadsConversation(
            focus = focus,
            ancestors = postsOf(row["ancestors"]),
            continuation = postsOf(row["continuation"]),
            replies = row["replies"].list.map(::postsOf).filter { it.isNotEmpty() },
            loaded = true,
            levels = row["levels"].keys.mapNotNull { id -> row["levels"][id].long?.let { id to it.toInt() } }.toMap(),
            unfinished = row["unfinished"].list.mapNotNull { it.string }.toSet(),
            moreReplies = row["moreReplies"].boolean == true,
            related = postsOf(row["related"]),
        )
    }

    /** The key a conversation is kept under: its post's short code, else its link. */
    fun key(url: String): String = ThreadsLinks.shortcode(url) ?: url.trim()

    private fun posts(posts: List<ThreadsPost>): JsonElement = JsonArray(posts.map(ThreadsPostJson::json))

    private fun postsOf(row: J): List<ThreadsPost> = row.list.mapNotNull(ThreadsPostJson::post)
}

/**
 * The conversations opened lately, kept on the device ([ThreadsKeptShelf]) so
 * that one opened again shows at once what it showed last while its page is
 * read again: the [CAP] newest of the last [MAX_AGE_DAYS] days, at most
 * [MAX_BYTES] in all.
 */
internal class ThreadsConversationShelf(file: (String) -> StoreFile, clock: () -> Instant = Instant::now) {
    private val shelf = ThreadsKeptShelf(
        file, FOLDER, CAP, MAX_AGE_DAYS * 24 * 60 * 60 * 1000L, MAX_BYTES,
        encode = ThreadsConversationsKept::json, decode = ThreadsConversationsKept::conversationOf, clock = clock,
    )

    /** What the conversation at [url] showed last, and when; null when it was not kept. */
    suspend fun of(url: String): ThreadsKeptEntry<ThreadsConversation>? = shelf.of(ThreadsConversationsKept.key(url))

    /** [conversation] kept as [url]'s, read at [at]; one not read yet changes nothing. */
    suspend fun keep(url: String, conversation: ThreadsConversation, at: Instant) {
        val kept = ThreadsConversationsKept.trimmed(conversation) ?: return
        shelf.keep(ThreadsConversationsKept.key(url), kept, at)
    }

    companion object {
        const val CAP = 50
        const val MAX_AGE_DAYS = 7L
        const val MAX_BYTES = 8L * 1024 * 1024
        private const val FOLDER = "kept_conversations"
    }
}
