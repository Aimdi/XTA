package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.core.x.model.XPost
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
