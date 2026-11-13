package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XUser

/** Whether an account or a search is followed here, and read in the Following feed. X is never told. */
data class Followed(val followed: Boolean = false, val inFeed: Boolean = false)

/**
 * What XTA's follow button follows: an X account, or a search. Following
 * happens on the device alone, in the session.
 */
sealed interface FollowTarget {
    /** What a group holds it by: an account's id, a search's query. */
    val memberId: String

    /** Whether the Following feed can leave it out: XTA offers that switch for accounts only. */
    val hasFeedSwitch: Boolean

    fun state(session: Session): Followed

    /** Followed from [at] (epoch milliseconds); one already followed keeps its place and switches. */
    fun followed(session: Session, at: Long): Session

    /** No longer followed, and out of every group. */
    fun unfollowed(session: Session): Session

    /** Followed, or no longer, as XTA's Subscribe and Unsubscribe do. */
    fun toggled(session: Session, at: Long): Session = if (state(session).followed) unfollowed(session) else followed(session, at)

    data class Account(val user: XUser) : FollowTarget {
        override val memberId: String get() = user.id
        override val hasFeedSwitch: Boolean get() = true

        override fun state(session: Session): Followed = session.followed(user.id)?.let { Followed(true, it.inFeed) } ?: Followed()

        override fun followed(session: Session, at: Long): Session = session.follow(user, at)

        override fun unfollowed(session: Session): Session = session.unfollow(user.id)

        /** Shown in the Following feed, or only in its groups. */
        fun feedToggled(session: Session): Session = session.withInFeed(user.id, !state(session).inFeed)
    }

    data class Search(val query: String) : FollowTarget {
        override val memberId: String get() = query.trim()
        override val hasFeedSwitch: Boolean get() = false

        /** A followed search counts as in the feed, as XTA keeps it: groups read it. */
        override fun state(session: Session): Followed =
            if (session.followedSearch(query) != null) Followed(followed = true, inFeed = true) else Followed()

        override fun followed(session: Session, at: Long): Session = session.followSearch(query, at)

        override fun unfollowed(session: Session): Session = session.unfollowSearch(query)
    }
}
