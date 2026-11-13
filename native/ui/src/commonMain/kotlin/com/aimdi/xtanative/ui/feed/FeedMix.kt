package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.x.model.XThread

/**
 * Other networks' posts among X's in one timeline (XTA's interleaved items):
 * the feed pages on X's cursors, which nothing else can page on, so the
 * plugins' posts are slotted between the threads by time instead.
 */
object FeedMix {
    /** Where [thread] sits in time: its newest post (XTA's newestDateOf); null when none says when. */
    fun timeOf(thread: XThread): Long? = thread.posts.mapNotNull { it.createdAt?.toEpochMilli() }.maxOrNull()

    /**
     * [posts] placed among [threads] (XTA's placeInterleaved): one bucket per
     * thread, holding the posts newer than it that no thread above took, then
     * one more for the posts older than every thread read so far, which ride
     * at the end until paging reaches their place. A thread that says no time
     * is passed over: there is nothing to compare a post with.
     */
    fun place(threads: List<XThread>, posts: List<SourcedPost>): List<List<SourcedPost>> {
        val buckets = List(threads.size + 1) { ArrayList<SourcedPost>() }
        if (posts.isEmpty()) return buckets
        val times = threads.map(::timeOf)
        posts.sortedByDescending { it.time }.forEach { post ->
            val slot = times.indexOfFirst { it != null && post.time > it }
            buckets[if (slot < 0) threads.size else slot] += post
        }
        return buckets
    }

    /** What the row of the thread at [index] carries: the posts above it, and below it for the last thread, those older than all. */
    fun around(buckets: List<List<SourcedPost>>, index: Int): Pair<List<SourcedPost>, List<SourcedPost>> {
        val above = buckets.getOrNull(index).orEmpty()
        val below = if (index == buckets.size - 2) buckets.lastOrNull().orEmpty() else emptyList()
        return above to below
    }
}
