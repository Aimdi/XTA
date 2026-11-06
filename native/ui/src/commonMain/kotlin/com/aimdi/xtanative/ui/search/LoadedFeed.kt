package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.ui.feed.SourcedPost
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.TextRun

/**
 * Searching the posts a feed has already loaded, as XTA's loaded-feed search
 * does: nothing is asked of X, typing only filters what is at hand.
 */
object LoadedFeed {
    /** How deep reposts and quotes inside a post are searched. */
    private const val DEPTH = 3
    private val spaces = Regex("""\s+""")

    /** What a row can be found by: each post's text, author, and links, with what it reposts and quotes. */
    fun textOf(thread: XThread): String = thread.posts.joinToString("\n") { textOf(it, 0) }

    private fun textOf(post: XPost, depth: Int): String = (
        listOf(post.text, post.author.name, post.author.screenName) +
            post.entities.filterIsInstance<XEntity.Link>().flatMap { listOf(it.expanded, it.display) } +
            listOfNotNull(post.reposted, post.quoted).filter { depth < DEPTH }.map { textOf(it, depth + 1) }
        ).joinToString("\n")

    /**
     * What a plugin's post among X's can be found by, as XTA's loaded plugin
     * items: its link, its plugin, and every text it carries: title, author,
     * text and the links in it, its card and what it quotes.
     */
    fun textOf(post: SourcedPost): String = (listOfNotNull(post.post.url, post.source) + textOf(post.post, 0)).joinToString("\n")

    private fun textOf(post: NetworkPost, depth: Int): String = (
        listOfNotNull(post.title, post.authorName, post.authorHandle, post.repostedBy, post.text.joinToString("") { it.text }) +
            post.text.filterIsInstance<TextRun.Link>().map { it.url } +
            listOfNotNull(post.card?.url, post.card?.title, post.card?.description) +
            listOfNotNull(post.quoted).filter { depth < DEPTH }.map { textOf(it, depth + 1) }
        ).joinToString("\n")

    /** Whether [text] holds every word of [query], whatever their case; an empty query keeps everything. */
    fun matches(text: String, query: String): Boolean {
        val haystack = text.lowercase()
        return query.lowercase().trim().split(spaces).all { haystack.contains(it) }
    }

    /** [threads] and the plugins' [posts] among them, with what each can be found by, worked out once for all the typing. */
    class Index(private val threads: List<XThread>, private val posts: List<SourcedPost> = emptyList()) {
        private val texts = threads.map { textOf(it) }
        private val postTexts = posts.map { textOf(it) }

        /** The rows that hold every word of [query], in their order. */
        fun matching(query: String): List<XThread> = threads.filterIndexed { index, _ -> matches(texts[index], query) }

        /** The plugins' posts that hold every word of [query], in their order. */
        fun matchingPosts(query: String): List<SourcedPost> = posts.filterIndexed { index, _ -> matches(postTexts[index], query) }
    }
}
