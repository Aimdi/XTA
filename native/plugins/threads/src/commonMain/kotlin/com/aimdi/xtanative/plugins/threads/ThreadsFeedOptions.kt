package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.feed.MutedKeywords
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Which posts of the merged feed the tab shows. */
internal enum class ThreadsFeedContent { ALL, MEDIA, LINKS }

/**
 * What the reader chose to see in the tab (threads_feed_options.dart),
 * applied to what is loaded: nothing here changes what Meta is asked. Kept
 * in XTA's `plugin.threads.feed_options` as XTA writes it, with the words
 * muted on this device beside XTA's keys when there are some. The muted
 * words hide a post in Home and in For you alike, matched as the groups'
 * muted words are (a single word whole, a phrase anywhere).
 */
internal data class ThreadsFeedOptions(
    val content: ThreadsFeedContent = ThreadsFeedContent.ALL,
    val hideReplies: Boolean = false,
    val hideReposts: Boolean = false,
    val mutedWords: List<String> = emptyList(),
) {
    /** How many choices differ from showing everything, for the filter's badge. */
    val activeCount: Int get() = listOf(content != ThreadsFeedContent.ALL, hideReplies, hideReposts, mutedWords.isNotEmpty()).count { it }

    val filtered: Boolean get() = activeCount > 0

    /** Whether something but the muted words is chosen, which Reset filters puts back. */
    val resettable: Boolean get() = copy(mutedWords = emptyList()).filtered

    /** These choices shown everything again, the muted words kept: they are a list the reader wrote, not a switch. */
    fun reset(): ThreadsFeedOptions = ThreadsFeedOptions(mutedWords = mutedWords)

    /** [posts] as these choices show them. */
    fun shown(posts: List<ThreadsPost>): List<ThreadsPost> = if (!filtered) posts else posts.filter { post ->
        !(hideReplies && post.isReply) && !(hideReposts && post.isRepost) && matches(post) && !muted(post)
    }

    /** [posts] without those a muted word hides, as For you shows them. */
    fun unmuted(posts: List<ThreadsPost>): List<ThreadsPost> = if (mutedWords.isEmpty()) posts else posts.filterNot(::muted)

    /** [typed] muted too, split at commas and line breaks; a word already muted, in any case, is not added twice. */
    fun muting(typed: String): ThreadsFeedOptions {
        val known = mutedWords.map { it.lowercase() }.toMutableSet()
        return copy(mutedWords = mutedWords + MutedKeywords.parseTyped(typed).filter { known.add(it.lowercase()) })
    }

    fun unmuting(word: String): ThreadsFeedOptions = copy(mutedWords = mutedWords - word)

    fun encode(): String = JsonObject(
        mapOf("content" to JsonPrimitive(content.name.lowercase()), "hideReplies" to JsonPrimitive(hideReplies), "hideReposts" to JsonPrimitive(hideReposts)) +
            (if (mutedWords.isEmpty()) emptyMap() else mapOf("mutedWords" to JsonArray(mutedWords.map(::JsonPrimitive)))),
    ).toString()

    private fun matches(post: ThreadsPost): Boolean = when (content) {
        ThreadsFeedContent.ALL -> true
        ThreadsFeedContent.MEDIA -> post.hasMedia || post.quoted?.hasMedia == true
        ThreadsFeedContent.LINKS -> hasLink(post)
    }

    private fun muted(post: ThreadsPost): Boolean {
        if (mutedWords.isEmpty()) return false
        val text = readable(post)
        return mutedWords.any { MutedKeywords.matches(text, it) }
    }

    companion object {
        private val address = Regex("https?://", RegexOption.IGNORE_CASE)

        /** The choices XTA wrote; anything unreadable is the default, everything shown. */
        fun decode(text: String): ThreadsFeedOptions {
            val root = J.parse(text)
            if (root.raw !is JsonObject) return ThreadsFeedOptions()
            return ThreadsFeedOptions(
                content = ThreadsFeedContent.entries.firstOrNull { it.name.lowercase() == root["content"].string } ?: ThreadsFeedContent.ALL,
                hideReplies = root["hideReplies"].boolean == true,
                hideReposts = root["hideReposts"].boolean == true,
                mutedWords = root["mutedWords"].list.mapNotNull { it.string?.trim()?.ifEmpty { null } }.distinct(),
            )
        }

        /** Whether [post] points off Threads: a preview card, a link Meta resolved in the caption, or a bare address. */
        fun hasLink(post: ThreadsPost): Boolean =
            post.linkCard != null || post.fragments.any { it.kind == ThreadsTextFragment.Kind.LINK } || address.containsMatchIn(post.text)

        /** What a muted word is looked for in: the words on screen, the quote's and the link card's included, and the topic. */
        private fun readable(post: ThreadsPost): String = listOfNotNull(
            post.text,
            post.topicTag,
            post.linkCard?.title,
            post.linkCard?.description,
            post.quoted?.text,
        ).joinToString("\n")
    }
}
