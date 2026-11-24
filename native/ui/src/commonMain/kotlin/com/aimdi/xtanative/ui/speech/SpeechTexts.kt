package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.parse.XRun
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.services.article.Article

/**
 * What reading a post, a thread or an article aloud says, and the title the
 * speech bar names while it does. A link is left out of a post's words: an
 * address read letter by letter says nothing a listener can use.
 */
object SpeechTexts {
    /** [post]'s own words (a repost's, the post reposted), without its links, spaces between them evened out. */
    fun post(post: XPost): String = tidy(XText.runs(post.shown).filterNot { it is XRun.Link }.joinToString("") { it.text })

    /** The posts of a thread in order, one paragraph each; posts with nothing to read are passed over. */
    fun thread(posts: List<XPost>): String = posts.map(::post).filter { it.isNotEmpty() }.joinToString("\n\n")

    fun article(article: Article): String = article.plainText

    /**
     * What Listen reads on an opened post: [focal], then the thread its
     * author went on with under it, the first of [replies] that answers it
     * in the author's own words, as far as the author answers themself.
     * Other people's replies are not read: a listener asked for the post.
     */
    fun listened(focal: XPost, replies: List<XThread>): List<XPost> {
        val opened = focal.shown
        val own = replies.firstOrNull { thread -> thread.posts.firstOrNull()?.let { continues(it, opened) } == true }?.posts.orEmpty()
        val previous = listOf(opened) + own
        return listOf(focal) + own.withIndex().takeWhile { (index, post) -> continues(post, previous[index]) }.map { it.value }
    }

    /** Whether [post] is its author going on from [previous], their own post. */
    private fun continues(post: XPost, previous: XPost): Boolean = post.replyToPostId == previous.id && sameAuthor(post, previous)

    private fun sameAuthor(a: XPost, b: XPost): Boolean =
        if (a.author.id.isNotEmpty() && b.author.id.isNotEmpty()) a.author.id == b.author.id else a.author.screenName.equals(b.author.screenName, ignoreCase = true)

    /** Who wrote what is read: the bar's title for a post or a thread. */
    fun title(post: XPost): String = post.shown.author.let { it.name.trim().ifEmpty { "@" + it.screenName } }

    /** Runs of spaces as one, each line trimmed, and no more than one empty line in a row. */
    private fun tidy(text: String): String = text.lines()
        .map { it.replace(spaces, " ").trim() }
        .joinToString("\n")
        .replace(emptyLines, "\n\n")
        .trim()

    private val spaces = Regex("[ \\t\\u00A0]+")
    private val emptyLines = Regex("\n{3,}")
}
