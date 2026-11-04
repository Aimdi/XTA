package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.GroupFeedPlan
import com.aimdi.xtanative.core.feed.SavedFeed
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.store.follows.KnownFollow
import com.aimdi.xtanative.ui.plugins.DiscoverySignal

/**
 * X's candidates for a group's Discover (group_discovery_x.dart,
 * group_discovery_feed.dart), cheapest first: what the group feed kept, then
 * a rotating few members' latest posts, then a rotating few members'
 * Following lists. Every repost, quote, reply and mention in the members' own
 * posts points at an account some member cares about.
 */
object XDiscovery {
    /** The accounts the members' own posts in [threads] point at, each with the member as its supporter; members are never candidates. */
    fun fromPosts(threads: List<XThread>, members: Map<String, FollowedAccount>, includeReplies: Boolean): List<Candidate> {
        val handles = members.values.mapTo(HashSet()) { it.screenName.lowercase() }
        return threads.flatMap { it.posts }.filter { it.author.id in members }.flatMap { PostMiner(it, members.keys, handles).mined(includeReplies) }
    }

    /** Accounts the members follow, each vouched for by the member following it (xFollowDiscoveryAccounts). */
    fun fromFollows(byMember: Map<String, List<KnownFollow>>, members: Map<String, FollowedAccount>): List<Candidate> =
        byMember.flatMap { (memberId, follows) ->
            val member = members[memberId] ?: return@flatMap emptyList()
            follows.filter { it.id !in members }.map { follow ->
                Candidate(follow.id, follow.handle, follow.name, follow.avatarUrl, follow.bio, listOf(Supporter(member.id, member.screenName, member.name)))
            }
        }

    /** What the group feed kept to read from: the posts it showed and those each of its searches held back. */
    fun feedThreads(saved: SavedFeed?): List<XThread> = saved?.let { it.threads + it.state?.chunks.orEmpty().flatMap { chunk -> chunk.unread } }.orEmpty()

    /**
     * The [members] whose recent posts the kept feed looked at: those of each
     * search of [plan] it kept a reading of, and whoever posted in it.
     */
    fun coveredByFeed(saved: SavedFeed?, plan: GroupFeedPlan, members: Set<String>): Set<String> {
        if (saved == null) return emptySet()
        val kept = saved.state?.chunks.orEmpty().mapTo(HashSet()) { it.query }
        val searched = plan.chunks.filter { plan.queryOf(it) in kept }.flatMap { chunk -> chunk.filterNot { it.search }.map { it.id } }
        val authors = feedThreads(saved).flatMap { thread -> thread.posts.map { it.author.id } }
        return (searched + authors).filterTo(HashSet()) { it in members }
    }
}

/** One member's post as Discover reads it: each account it points at once, a member never. */
private class PostMiner(private val post: XPost, private val members: Set<String>, private val memberHandles: Set<String>) {
    private val seen = HashSet<String>()

    /** What the post shares first, then whom it answers, then whom it mentions: an account met earlier is not met again. */
    fun mined(includeReplies: Boolean): List<Candidate> {
        val repost = post.reposted
        val quote = post.quoted ?: repost?.quoted
        val shared = listOfNotNull(
            repost?.let { shared(it, DiscoverySignal.REPOSTED) },
            quote?.let { shared(it, DiscoverySignal.QUOTED) },
            post.replyToScreenName?.takeIf { includeReplies }?.let { named(it, DiscoverySignal.REPLIED) },
        )
        return shared + post.entities.filterIsInstance<XEntity.Mention>().mapNotNull { named(it.screenName, DiscoverySignal.MENTIONED) }
    }

    private fun supporter(signal: DiscoverySignal) = Supporter(post.author.id, post.author.screenName, post.author.name, signal, post.createdAt?.toEpochMilli())

    /** Whether the account ([id] null when only its [handle] is known) is no member and new to this post. */
    private fun fresh(id: String?, handle: String): Boolean {
        val lower = handle.trim().removePrefix("@").lowercase()
        if (lower.isEmpty() || lower in memberHandles || (id != null && (id.isEmpty() || id in members))) return false
        val newId = id == null || seen.add("#$id")
        val newHandle = seen.add("@$lower")
        return if (id == null) newHandle else newId
    }

    /** A reposted or quoted post: its author wrote it, so it is what shows them. */
    private fun shared(shared: XPost, signal: DiscoverySignal): Candidate? {
        val author = shared.author
        if (!fresh(author.id, author.screenName)) return null
        return Candidate(
            id = author.id,
            handle = author.screenName,
            name = author.name,
            avatarUrl = author.avatarUrl,
            bio = author.description,
            supporters = listOf(supporter(signal)),
            text = XText.plain(shared),
            postUrl = statusUrl(shared.id),
            date = shared.createdAt?.toEpochMilli(),
            post = ShownPost.X(shared),
        )
    }

    /** A reply's target or a mention: only its handle is known, and the member's own post shows why. */
    private fun named(handle: String, signal: DiscoverySignal): Candidate? {
        if (!fresh(null, handle)) return null
        val clean = handle.trim().removePrefix("@")
        return Candidate(
            id = "",
            handle = clean,
            name = clean,
            supporters = listOf(supporter(signal)),
            text = XText.plain(post),
            postUrl = statusUrl(post.id),
            date = post.createdAt?.toEpochMilli(),
            post = ShownPost.X(post),
        )
    }

    private fun statusUrl(id: String) = "https://x.com/i/status/$id"
}
