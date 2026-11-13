package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.feed.FeedShaping
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.store.saved.ProfileFilters
import com.aimdi.xtanative.store.saved.ProfileFiltersStore
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

/**
 * What the reader hid of some accounts in every feed, from their profiles'
 * filters (XTA's retweet_filter and reply_filter): their reposts, or their
 * replies to others. XTA's feeds read the filters at each load; here a feed
 * follows them, and reads again when they change.
 */
object HiddenPosts {
    /** The filters as [store] holds them: read first, then each time they change. */
    fun of(store: ProfileFiltersStore): Flow<ProfileFilters> = flow {
        emit(store.filters())
        emitAll(store.state.filterNotNull())
    }.distinctUntilChanged()

    /** [shaping] hiding what [filters] hide, by the accounts' handles as XTA matches them. */
    fun shaping(filters: ProfileFilters, shaping: FeedShaping = FeedShaping()): FeedShaping =
        shaping.copy(hiddenReposts = filters.hiddenRepostHandles, hiddenReplies = filters.hiddenReplyHandles)

    /** [threads] without what [filters] hide: what a feed shows of its cache until X answers, as XTA's preview does. */
    fun visible(threads: List<XThread>, filters: ProfileFilters, now: Instant = Instant.now()): List<XThread> {
        val hiding = shaping(filters)
        return threads.filter { hiding.keeps(it, now) }
    }
}
