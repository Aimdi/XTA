package com.aimdi.xtanative.core.x.model

import com.aimdi.xtanative.core.json.J

/**
 * The community note X shows under a post (its `birdwatch_pivot`): the note's
 * text, its [links] in code points of that text, and X's heading for it.
 */
data class XCommunityNote(val text: String, val links: List<XEntity.Link> = emptyList(), val title: String? = null, val url: String? = null)

/**
 * An X article a post carries: what a row shows of it. [content] is the
 * article as X sent it (its `content_state` blocks and media), for the article
 * reader to lay out; it is not kept when posts are written to disk.
 */
data class XArticle(
    val id: String?,
    val title: String,
    val previewText: String,
    val coverUrl: String?,
    val content: J = J(null),
)

/**
 * A shared Grok conversation X attaches to a post: the question asked, the
 * start of the answer, and Grok's own account.
 */
data class XGrokShare(
    val url: String,
    val question: String,
    val answer: String,
    val grokScreenName: String = "grok",
    val grokImageUrl: String? = null,
)
