package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.ui.plugins.FeedReads

/**
 * The newest works of the creators followed on this device, merged newest
 * first: each creator's own first page, a few read at a time. One creator
 * who cannot be read leaves the others; only when none can is it a failure.
 */
internal object PixivFollowedHere {
    /** How many creators one read asks for, the most recently followed first. */
    const val PER_READ = 30

    suspend fun read(runtime: PixivRuntime): Page<PixivIllust> {
        val follows = PixivFollows.of(runtime.settings.now).asReversed().take(PER_READ)
        if (follows.isEmpty()) return Page(emptyList(), null)
        val works = FeedReads.each(follows.map { "${it.id}" }, FeedReads.ACCOUNTS_AT_ONCE) { id -> runtime.client.userIllusts(id.toLong()).items }
        return Page(merged(listOf(works)), null)
    }

    /** Every creator's works in one list, newest first, each work once. */
    fun merged(works: List<List<PixivIllust>>): List<PixivIllust> =
        works.flatten().distinctBy { it.id }.sortedByDescending { it.createdAt?.toEpochMilli() ?: 0 }
}
