package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.XClient

/** The feed's reads over X. */
fun XClient.feedReads(): FeedReads {
    val client = this
    return object : FeedReads {
        override suspend fun search(query: String, cursor: String?) = client.search(query, cursor)

        override suspend fun userPosts(userId: String) = client.userPosts(userId)
    }
}

/**
 * The Following feed, or a group's, from its members and the two switches
 * alone: a [GroupFeed] with no rules of its own. A feed with its settings
 * comes from `Session.feedPlan`.
 */
class FollowingFeed private constructor(private val feed: GroupFeed) : FeedTimeline by feed {
    val chunkCount: Int get() = feed.chunkCount

    companion object {
        operator fun invoke(
            members: List<FeedMember>,
            reads: FeedReads,
            includeReplies: Boolean = true,
            includeReposts: Boolean = true,
            concurrency: Int = ChunkConcurrency.MIN,
            pageSize: Int = 20,
            fallbackMillis: Long = 12_000,
        ): FollowingFeed = FollowingFeed(
            GroupFeed(GroupFeedPlan(FeedKeys.FOLLOWING, members, includeReplies, includeReposts), reads, concurrency, pageSize, fallbackMillis),
        )
    }
}
