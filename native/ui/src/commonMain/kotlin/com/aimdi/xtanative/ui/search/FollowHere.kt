package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.core.backup.FollowGroup
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

/** A group as the group picker lists it, and whether the account or search is in it. */
data class GroupPick(val id: String, val name: String, val emoji: String?, val member: Boolean) {
    val label: String get() = listOfNotNull(emoji, name).joinToString(" ")
}

/** Which groups the account or search is in, as the picker's status line says it. */
sealed interface Placed {
    data object None : Placed
    data class One(val name: String) : Placed
    data class Two(val first: String, val second: String) : Placed
    data class Many(val count: Int) : Placed
}

/** XTA's group picker, on the session: which groups hold a member, and what picking one does. */
object GroupPicker {
    /** Every group in XTA's order, with whether [memberId] is in it. */
    fun picks(session: Session, memberId: String): List<GroupPick> =
        FollowGroup.ordered(session.groups).map { GroupPick(it.id, it.name, it.emoji, memberId in it.memberIds) }

    /** The groups whose name holds what is typed, ignoring case; all of them while nothing is. */
    fun matching(picks: List<GroupPick>, typed: String): List<GroupPick> {
        val query = typed.trim()
        return if (query.isEmpty()) picks else picks.filter { it.name.contains(query, ignoreCase = true) }
    }

    /** A group named as typed may be made: something is typed, and no group has that name. */
    fun canCreate(picks: List<GroupPick>, typed: String): Boolean {
        val name = typed.trim()
        return name.isNotEmpty() && picks.none { it.name.equals(name, ignoreCase = true) }
    }

    fun placed(picks: List<GroupPick>): Placed {
        val names = picks.filter { it.member }.map { it.label }
        return when (names.size) {
            0 -> Placed.None
            1 -> Placed.One(names[0])
            2 -> Placed.Two(names[0], names[1])
            else -> Placed.Many(names.size)
        }
    }

    /**
     * [target] put in [groupId], or taken out of it. Put in a group, it is
     * followed first, as XTA's picker does: a group of accounts the app does
     * not follow would be a feed of nobody it reads.
     */
    fun inGroup(session: Session, target: FollowTarget, groupId: String, member: Boolean, at: Long): Session =
        (if (member) target.followed(session, at) else session).withMembership(groupId, target.memberId, member)

    /** A new group named [name], holding [target], which is followed first. */
    fun created(session: Session, target: FollowTarget, groupId: String, name: String, at: Long): Session =
        target.followed(session, at).withNewGroup(groupId, name, listOf(target.memberId))
}
