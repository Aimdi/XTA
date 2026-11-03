package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import java.time.Instant

/** Posts and threads for the feed tests, with only what a test sets. */
internal object Posts {
    fun user(id: String, screenName: String = id) = XUser(id, screenName, screenName)

    fun post(
        id: Long,
        by: String = "a",
        text: String = "post $id",
        likes: Long = 0,
        reposts: Long = 0,
        quotes: Long = 0,
        at: Instant? = null,
        replyTo: String? = null,
        repostOf: XPost? = null,
        quoted: XPost? = null,
        screenName: String = by,
    ) = XPost(
        id = id.toString(),
        author = user(by, screenName),
        createdAt = at,
        text = text,
        quoted = quoted,
        reposted = repostOf,
        repostCount = reposts,
        quoteCount = quotes,
        likeCount = likes,
        replyToScreenName = replyTo,
    )

    fun thread(post: XPost, id: String = post.id) = XThread(id, listOf(post))

    fun thread(id: Long, by: String = "a", text: String = "post $id", likes: Long = 0, at: Instant? = null) =
        thread(post(id, by, text, likes = likes, at = at))

    fun page(vararg threads: XThread, next: String? = null) = XTimelinePage(threads.toList(), next, null)

    fun ids(threads: List<XThread>) = threads.map { it.id }
}
