package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Which posts of the merged feed the tab shows. */
internal enum class ThreadsFeedContent { ALL, MEDIA, LINKS }

/**
 * What the reader chose to see in the tab (threads_feed_options.dart),
 * applied to what is loaded: nothing here changes what Meta is asked. Kept
 * in XTA's `plugin.threads.feed_options` as XTA writes it.
 */
internal data class ThreadsFeedOptions(
    val content: ThreadsFeedContent = ThreadsFeedContent.ALL,
    val hideReplies: Boolean = false,
    val hideReposts: Boolean = false,
) {
    /** How many choices differ from showing everything, for the filter's badge. */
    val activeCount: Int get() = (if (content == ThreadsFeedContent.ALL) 0 else 1) + (if (hideReplies) 1 else 0) + (if (hideReposts) 1 else 0)

    val filtered: Boolean get() = activeCount > 0

    /** [posts] as these choices show them. */
    fun shown(posts: List<ThreadsPost>): List<ThreadsPost> = if (!filtered) posts else posts.filter { post ->
        !(hideReplies && post.isReply) && !(hideReposts && post.isRepost) && matches(post)
    }

    fun encode(): String = JsonObject(
        mapOf("content" to JsonPrimitive(content.name.lowercase()), "hideReplies" to JsonPrimitive(hideReplies), "hideReposts" to JsonPrimitive(hideReposts)),
    ).toString()

    private fun matches(post: ThreadsPost): Boolean = when (content) {
        ThreadsFeedContent.ALL -> true
        ThreadsFeedContent.MEDIA -> post.hasMedia || post.quoted?.hasMedia == true
        ThreadsFeedContent.LINKS -> hasLink(post)
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
            )
        }

        /** Whether [post] points off Threads: a preview card, a link Meta resolved in the caption, or a bare address. */
        fun hasLink(post: ThreadsPost): Boolean =
            post.linkCard != null || post.fragments.any { it.kind == ThreadsTextFragment.Kind.LINK } || address.containsMatchIn(post.text)
    }
}
