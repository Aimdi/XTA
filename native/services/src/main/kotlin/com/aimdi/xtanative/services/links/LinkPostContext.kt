package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.core.x.model.XPost

/**
 * The post a link was opened from, carried into XTA's browser screen so the
 * reader sees, and can get back to, where the page came from
 * (link_post_context.dart). [network] is the network's id (`x`, `threads`,
 * …). A count is what the network reported; null when it reported none, and
 * then the bar shows the icon without inventing a zero. Nothing here acts on
 * the network: the counts are read, never changed.
 */
data class LinkPostContext(
    val network: String,
    val author: String,
    val avatarUrl: String? = null,
    val replies: Long? = null,
    val reposts: Long? = null,
    val likes: Long? = null,
    /** Where the post itself can be shared from. */
    val postUrl: String? = null,
) {
    companion object {
        const val X = "x"

        /**
         * An X post's context: reposts and quotes count together, as the
         * bar's single repost figure. [shareBaseUrl] is the reader's share
         * host; empty or unset is x.com.
         */
        fun of(post: XPost, shareBaseUrl: String? = null): LinkPostContext = LinkPostContext(
            network = X,
            author = post.author.name.ifEmpty { post.author.screenName },
            avatarUrl = post.author.avatarUrl,
            replies = post.replyCount,
            reposts = post.repostCount + post.quoteCount,
            likes = post.likeCount,
            postUrl = XLinks.shareUrl(post, shareBaseUrl?.ifEmpty { null } ?: "https://x.com"),
        )
    }
}
