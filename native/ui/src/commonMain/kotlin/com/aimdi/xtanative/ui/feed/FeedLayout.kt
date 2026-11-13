package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.post.BoostRuns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** An article's address as the feeds compare it (link_identity.dart): one key for every way of writing the same link. */
object ArticleLinks {
    private val SOCIAL = listOf("x.com", "twitter.com", "t.co", "bsky.app", "threads.net", "threads.com", "instagram.com", "facebook.com")
    private val SIGN_IN = setOf("login", "signin", "oauth", "auth")
    private val TRACKERS = setOf("fbclid", "gclid", "dclid", "mc_cid", "mc_eid")

    /**
     * [value] as an article: http or https with no sign-in in it, on no
     * social network, past the site's home page and on no sign-in page; its
     * host in lower case, its query sorted without the trackers (`utm_…`,
     * `fbclid` …) and its fragment dropped. Null for anything else: a post,
     * a profile, a home page, an app's route in the fragment.
     */
    fun canonical(value: String?): String? {
        val url = value?.trim()?.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val host = url.host.lowercase()
        if (SOCIAL.any { host == it || host.endsWith(".$it") }) return null
        if (url.encodedPath == "/" || url.pathSegments.any { it in SIGN_IN }) return null
        val fragment = url.fragment.orEmpty()
        if (fragment.startsWith("/") || fragment.startsWith("!")) return null
        val kept = url.queryParameterNames.filterNot { it.lowercase().startsWith("utm_") || it in TRACKERS }.sorted()
        val builder = url.newBuilder().query(null).fragment(null)
        kept.forEach { name -> url.queryParameterValues(name).forEach { builder.addQueryParameter(name, it) } }
        return builder.build().toString().trimEnd('?', '#')
    }

    /**
     * A thread's one article (_chainLink): a lone post (a repost stands for
     * the post it reposts) whose links name exactly one; null for a
     * conversation, which may discuss several, or a post linking none or many.
     */
    fun of(thread: XThread): String? {
        val post = thread.posts.singleOrNull()?.shown ?: return null
        return post.entities.filterIsInstance<XEntity.Link>().mapNotNull { canonical(it.expanded) }.distinct().singleOrNull()
    }

    /** A plugin's post's article: what its card links to, else its own address when it is an article with a title (an RSS item, a story). */
    fun of(post: NetworkPost): String? = canonical(post.card?.url ?: post.url?.takeIf { post.title != null })
}

/** What a feed list draws in one post's place. */
sealed interface FeedPart {
    /** The post itself. */
    data object Plain : FeedPart

    /** Nothing: the post is shown in a stack or a run of reposts above it. */
    data object Hidden : FeedPart

    /**
     * The newest of the posts sharing one article: it, then "More posts
     * sharing this link", which shows the others. [keys] are all of them,
     * newest first, as [FeedLayout.xKey] and [SourcedPost.key] name them.
     */
    data class Stack(val keys: List<String>) : FeedPart

    /** A run of reposts starting here, drawn as one row. */
    data class Run(val threads: List<XThread>) : FeedPart
}

/**
 * How a feed list draws what it read (paginated_tweet_list.dart,
 * feed_link_grouping.dart, boost_runs.dart): the posts sharing one article,
 * X's and the plugins' alike, stacked under the newest of them; and, with
 * Settings' "Group runs of reposts" on, two or more reposts in a row as one
 * row, unless one of them is in a stack, which would hide it twice.
 */
class FeedLayout private constructor(
    private val stacks: Map<String, List<String>>,
    private val stacked: Set<String>,
    private val runs: Map<Int, List<XThread>>,
    private val inRuns: Set<Int>,
) {
    /** What the list draws for the thread at [index]. */
    fun ofThread(index: Int, thread: XThread): FeedPart {
        val key = xKey(thread)
        stacks[key]?.let { return FeedPart.Stack(it) }
        return when {
            key in stacked -> FeedPart.Hidden
            index in runs -> FeedPart.Run(runs.getValue(index))
            index in inRuns -> FeedPart.Hidden
            else -> FeedPart.Plain
        }
    }

    /** What the list draws for a plugin's post among the threads. */
    fun ofPost(post: SourcedPost): FeedPart = stacks[post.key]?.let(FeedPart::Stack) ?: if (post.key in stacked) FeedPart.Hidden else FeedPart.Plain

    companion object {
        val None = FeedLayout(emptyMap(), emptySet(), emptyMap(), emptySet())

        /** What a stack calls an X thread. */
        fun xKey(thread: XThread): String = "x:" + thread.id

        fun of(threads: List<XThread>, posts: List<SourcedPost> = emptyList(), collapseReposts: Boolean): FeedLayout {
            val groups = sharedLinks(threads, posts)
            if (groups.isEmpty() && !collapseReposts) return None
            val inStacks = groups.flatMapTo(HashSet()) { it }
            val anchors = groups.associateBy { it.first() }
            val runs = if (collapseReposts) runsOf(threads, inStacks) else emptyMap()
            val inRuns = runs.flatMapTo(HashSet()) { (start, run) -> (start + 1) until (start + run.size) }
            return FeedLayout(anchors, inStacks - anchors.keys, runs, inRuns)
        }

        private class Entry(val key: String, val time: Long, val link: String?)

        /** The keys of the posts sharing each article read twice or more, newest first (repeatedLinks). */
        private fun sharedLinks(threads: List<XThread>, posts: List<SourcedPost>): List<List<String>> {
            val entries = threads.map { Entry(xKey(it), FeedMix.timeOf(it) ?: Long.MIN_VALUE, ArticleLinks.of(it)) } +
                posts.map { Entry(it.key, it.time, ArticleLinks.of(it.post)) }
            return entries.filter { it.link != null }
                .sortedByDescending { it.time }
                .groupBy { it.link }
                .values
                .filter { it.size >= 2 }
                .map { group -> group.map { it.key } }
        }

        /** The runs of reposts by where they start, leaving out any that holds a stacked post ([stacked]). */
        private fun runsOf(threads: List<XThread>, stacked: Set<String>): Map<Int, List<XThread>> {
            val runs = HashMap<Int, List<XThread>>()
            var index = 0
            BoostRuns.collapse(threads).forEach { item ->
                when (item) {
                    is BoostRuns.Item.Single -> index++
                    is BoostRuns.Item.Run -> {
                        if (item.threads.none { xKey(it) in stacked }) runs[index] = item.threads
                        index += item.threads.size
                    }
                }
            }
            return runs
        }
    }
}
