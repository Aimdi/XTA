package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What this session changed on Pixiv, by id (works, novels or creators), and
 * the writes still on their way. What Pixiv sent with each card stays the
 * truth until a write: every list and screen showing it then agrees.
 */
internal data class PixivMarks(val marked: Map<Long, Boolean> = emptyMap(), val busy: Set<Long> = emptySet()) {
    /** Whether [id] is on, given what its card was [sent]. */
    fun on(id: Long, sent: Boolean): Boolean = marked[id] ?: sent

    /** [total] as Pixiv sent it, moved by one when this session changed [id]. */
    fun count(id: Long, sent: Boolean, total: Long): Long {
        val now = on(id, sent)
        return when {
            now == sent -> total
            now -> total + 1
            else -> (total - 1).coerceAtLeast(0)
        }
    }

    fun isBusy(id: Long): Boolean = id in busy
}

/** One kind's [PixivMarks] for the whole plugin (XTA's PixivBookmarkOverrides and PixivFollowStore). */
internal class PixivMarkStore {
    private val mutable = MutableStateFlow(PixivMarks())
    val state: StateFlow<PixivMarks> = mutable.asStateFlow()
    val now: PixivMarks get() = mutable.value

    /** Records that a write left [id] on or off. */
    fun mark(id: Long, on: Boolean) = mutable.update { it.copy(marked = it.marked + (id to on)) }

    /** Runs [write] for [id] unless one already is, so a second tap waits; null when skipped. */
    suspend fun <T> exclusive(id: Long, write: suspend () -> T): T? {
        if (!take(id)) return null
        try {
            return write()
        } finally {
            mutable.update { it.copy(busy = it.busy - id) }
        }
    }

    /** Forgets every change, for another account. */
    fun clear() {
        mutable.value = PixivMarks(busy = mutable.value.busy)
    }

    @Synchronized
    private fun take(id: Long): Boolean {
        if (id in mutable.value.busy) return false
        mutable.update { it.copy(busy = it.busy + id) }
        return true
    }
}

/**
 * Following on Pixiv, as XTA's PixivFollowStore: one state for every follow
 * button of a creator. A failed write leaves the follow as it was and throws.
 */
internal class PixivFollowing(
    private val sendFollow: suspend (userId: Long, restrict: String) -> Unit,
    private val sendUnfollow: suspend (userId: Long) -> Unit,
) {
    val marks = PixivMarkStore()

    fun isFollowed(user: PixivUser): Boolean = marks.now.on(user.id, user.isFollowed)

    /** Follows publicly or unfollows: whether the reader follows now; null while a write for them is on its way. */
    suspend fun toggle(user: PixivUser): Boolean? = if (isFollowed(user)) unfollow(user) else follow(user)

    /** Follows [user], or moves the follow to [restrict] (`public` or `private`). */
    suspend fun follow(user: PixivUser, restrict: String = PixivRestrict.PUBLIC): Boolean? = marks.exclusive(user.id) {
        sendFollow(user.id, restrict)
        marks.mark(user.id, true)
        true
    }

    suspend fun unfollow(user: PixivUser): Boolean? = marks.exclusive(user.id) {
        sendUnfollow(user.id)
        marks.mark(user.id, false)
        false
    }
}

/** The two visibilities of a bookmark or a follow on Pixiv. */
internal object PixivRestrict {
    const val PUBLIC = "public"
    const val PRIVATE = "private"
}
