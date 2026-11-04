package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsCaption
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.LinkCard
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A Threads post as the app's archive keeps it, in XTA's record
 * (threads_archive.dart): `threads:<its link>` (its id without one), its
 * author's handle, and `{"xtaPlugin": "threads", "post": …}` with the post as
 * XTA keeps one, so Saved, a backup and the offline copies read it the same
 * in both apps. A post XTA once saved as a link keeps its id.
 */
internal object ThreadsArchive {
    fun id(post: ThreadsPost): String = "threads:" + (post.url ?: post.id)

    fun of(post: ThreadsPost): PluginPostArchive =
        PluginPostArchive(id(post), post.handle, buildJsonObject { put("xtaPlugin", "threads"); put("post", ThreadsPostJson.json(post)) })

    /** The post a record holds; null for one of another network, or one without its id. */
    fun post(content: JsonElement): ThreadsPost? {
        val root = J(content)
        return if (root["xtaPlugin"].string == "threads") ThreadsPostJson.post(root["post"]) else null
    }
}

/**
 * Threads in the app's shared timelines (threads_interleaved.dart): which
 * posts a mixed feed takes, and each as the shared row draws it.
 */
internal object ThreadsShared {
    /** XTA's kThreadsInterleavedPageSize: an account dropping its whole page in would own the top of the feed. */
    const val PER_ACCOUNT = 10

    /**
     * What a feed taking [kinds] mixes in of [posts], in their order: the
     * kinds first, so hidden replies and reposts use none of an account's
     * [limit], then at most that many of each account (a repost is its
     * reposter's), and only those with a date to be placed by.
     */
    fun mixed(posts: List<ThreadsPost>, kinds: FeedKinds, limit: Int = PER_ACCOUNT): List<ThreadsPost> {
        val counts = HashMap<String, Int>()
        return posts.filter { kinds.takes(it.isReply, it.isRepost) }
            .filter { post -> counts.merge(post.sourceHandle.lowercase(), 1, Int::plus)!! <= limit }
            .filter { it.publishedAt != null }
    }

    /**
     * [post] as the shared row draws it among other networks' posts: who
     * reposted it, whom it answers, its caption with its mentions, links and
     * tags, its media at the [quality] the setting picks, its link card and
     * its quote; a count Meta did not give is none.
     */
    fun post(post: ThreadsPost, quality: MediaQuality): NetworkPost = NetworkPost(
        id = post.id,
        authorId = post.handle,
        authorName = post.authorName,
        authorHandle = post.handle,
        avatarUrl = post.avatarUrl,
        createdAt = post.publishedAt,
        text = threadsRuns(post.text, post.fragments),
        media = ThreadsMediaItems.items(post, quality),
        card = post.linkCard?.let { LinkCard(it.url, it.title, it.description, it.imageUrl) },
        quoted = post.quoted?.let { post(it, quality) },
        replies = post.replyCount ?: 0,
        reposts = post.repostCount ?: 0,
        quotes = post.quoteCount ?: 0,
        likes = post.likeCount ?: 0,
        repostedBy = post.reposterDisplayName.takeIf { post.isRepost },
        replyingTo = post.replyToHandle?.takeIf { it.isNotEmpty() },
        url = post.url,
    )
}

/** A caption's runs as the shared text draws them: Meta's own split when the post carried it, else what a pattern finds. */
internal fun threadsRuns(text: String, fragments: List<ThreadsTextFragment>): List<TextRun> = ThreadsCaption.runs(text, fragments).map { run ->
    val target = run.target
    when {
        target == null -> TextRun.Plain(run.text)
        run.kind == ThreadsTextFragment.Kind.MENTION -> TextRun.Mention(run.text, target)
        run.kind == ThreadsTextFragment.Kind.LINK -> TextRun.Link(run.text, target)
        run.kind == ThreadsTextFragment.Kind.TAG -> TextRun.Tag(run.text, target)
        else -> TextRun.Plain(run.text)
    }
}
