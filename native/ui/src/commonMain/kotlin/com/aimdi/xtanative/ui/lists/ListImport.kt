package com.aimdi.xtanative.ui.lists

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XUser
import java.net.URI

/** X lists by their id, as the reader types or links one. */
object ListIds {
    private val digits = Regex("""^\d+$""")

    /**
     * The id of the list [input] names: a bare id, or a list link
     * (`https://x.com/i/lists/123`, with or without its `https://`); null
     * for anything else.
     */
    fun of(input: String): String? {
        val trimmed = input.trim()
        if (digits.matches(trimmed)) return trimmed
        val path = runCatching { URI(if ("://" in trimmed) trimmed else "https://$trimmed").path }.getOrNull() ?: return null
        val parts = path.split('/').filter { it.isNotEmpty() }
        return parts.takeIf { it.size == 3 && it[0] == "i" && it[1] == "lists" && digits.matches(it[2]) }?.get(2)
    }
}

/**
 * XTA's import of an X list as a group, page by page of its members: each
 * page's new members are followed here (one already followed keeps its date
 * and its place in the feed), and once the last page is in, a group named
 * after the list holds them all. Nothing is followed on X.
 */
object ListImport {
    /** The members of a page not met on an earlier one, each added to [seen]; one without a handle could not be opened. */
    fun fresh(users: List<XUser>, seen: MutableSet<String>): List<XUser> =
        users.filter { it.id.isNotEmpty() && it.screenName.isNotEmpty() && seen.add(it.id) }

    /** The members are all in: no cursor, X's `0`, the cursor asked with, or a page with no one new. */
    fun ended(next: String?, asked: String?, fresh: List<XUser>): Boolean =
        next.isNullOrEmpty() || next == "0" || next == asked || fresh.isEmpty()

    /** [fresh] followed from [at], in the Following feed, but for those already followed, which stay as they are. */
    fun followed(session: Session, fresh: List<XUser>, at: Long): Session =
        fresh.filter { session.followed(it.id) == null }.fold(session) { followed, user -> followed.follow(user, at) }

    /** The list's group: named as the list, holding every member, in the order they came. */
    fun grouped(session: Session, groupId: String, listName: String, memberIds: Collection<String>): Session =
        session.withNewGroup(groupId, listName, memberIds.toList())
}
