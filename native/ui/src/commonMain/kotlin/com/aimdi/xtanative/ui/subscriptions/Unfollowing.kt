package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.SavedSearch
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import com.aimdi.xtanative.ui.search.FollowTarget

/** What unsubscribing took away, kept for its undo (local_undo.dart): each account or search at its place, the groups each was in, the caps. */
data class Unfollowed(
    val accounts: List<IndexedValue<FollowedAccount>> = emptyList(),
    val searches: List<IndexedValue<SavedSearch>> = emptyList(),
    val groups: Map<String, List<String>> = emptyMap(),
    val postCaps: Map<String, Int> = emptyMap(),
) {
    val isEmpty: Boolean get() = accounts.isEmpty() && searches.isEmpty()
}

/** Unsubscribing X accounts and searches on the device, and putting them back as XTA's undo does. */
object Unfollows {
    /** [session] without the accounts [ids], and what that took. */
    fun accounts(session: Session, ids: Collection<String>): Pair<Session, Unfollowed> {
        val wanted = ids.toSet()
        val taken = Unfollowed(
            accounts = session.following.withIndex().filter { it.value.id in wanted },
            groups = groupsOf(session, wanted),
            postCaps = session.postCaps.filterKeys { it in wanted },
        )
        return wanted.fold(session) { next, id -> next.unfollow(id) } to taken
    }

    /** [session] without the search [query], and what that took. */
    fun search(session: Session, query: String): Pair<Session, Unfollowed> {
        val trimmed = query.trim()
        val taken = Unfollowed(searches = session.searches.withIndex().filter { it.value.query == trimmed }, groups = groupsOf(session, setOf(trimmed)))
        return session.unfollowSearch(trimmed) to taken
    }

    private fun groupsOf(session: Session, members: Set<String>): Map<String, List<String>> =
        members.associateWith { member -> session.groups.filter { member in it.memberIds }.map { it.id } }.filterValues { it.isNotEmpty() }

    /**
     * [session] with what [unfollowed] took put back where it was; null when
     * any of it was followed again since, as an undo never writes over a later
     * change. A group deleted since stays deleted.
     */
    fun restored(session: Session, unfollowed: Unfollowed): Session? {
        if (unfollowed.accounts.any { session.followed(it.value.id) != null }) return null
        if (unfollowed.searches.any { session.followedSearch(it.value.query) != null }) return null
        val back = session.copy(
            following = putBack(session.following, unfollowed.accounts),
            searches = putBack(session.searches, unfollowed.searches),
            postCaps = session.postCaps + unfollowed.postCaps,
        )
        return unfollowed.groups.entries.fold(back) { next, (member, groups) -> next.addingTo(member, groups) }
    }

    private fun <T> putBack(list: List<T>, taken: List<IndexedValue<T>>): List<T> =
        taken.sortedBy { it.index }.fold(list) { into, (index, item) -> into.toMutableList().apply { add(index.coerceAtMost(size), item) } }
}

/**
 * Unsubscribing here with XTA's undo (subscription_unfollow.dart, UndoHost):
 * the account or search goes, and "Changes saved" offers it back while it is
 * shown. Nothing reaches X.
 */
object Unfollowing {
    suspend fun unfollow(graph: AppGraph, target: FollowTarget) = when (target) {
        is FollowTarget.Account -> accounts(graph, listOf(target.user.id))
        is FollowTarget.Search -> search(graph, target.query)
    }

    suspend fun accounts(graph: AppGraph, ids: Collection<String>) = change(graph) { Unfollows.accounts(it, ids) }

    suspend fun search(graph: AppGraph, query: String) = change(graph) { Unfollows.search(it, query) }

    private suspend fun change(graph: AppGraph, unfollow: (Session) -> Pair<Session, Unfollowed>) {
        var taken = Unfollowed()
        graph.sessions.update { session ->
            val (next, took) = unfollow(session)
            taken = took
            next
        }
        if (!taken.isEmpty) offer { restore(graph, taken) }
    }

    private suspend fun restore(graph: AppGraph, taken: Unfollowed): Boolean {
        var done = false
        graph.sessions.update { session -> Unfollows.restored(session, taken)?.also { done = true } ?: session }
        return done
    }

    /** "Changes saved", with [undo] answering whether it could take the change back. */
    fun offer(undo: suspend () -> Boolean) = KeepNotices.show(KeepNotice.ChangesSaved(undo))
}
