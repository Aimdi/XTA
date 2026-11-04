package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XUserPage

/** What importing the accounts a profile follows reads from X: the profile's id, then whom it follows, page by page. */
interface FollowingReads {
    suspend fun userId(screenName: String): String

    suspend fun following(userId: String, cursor: String?): XUserPage
}

/**
 * XTA's import of subscriptions (_import.dart): the public following of each
 * handle becomes local follows. Nothing is followed on X.
 */
object FollowingImport {
    /**
     * Reads whom [screenName] follows, page by page, handing [onPage] each
     * page's accounts not seen before. Paging ends on a missing, `0` or
     * repeated cursor, or a page that brought nobody new. Returns how many.
     */
    suspend fun read(screenName: String, reads: FollowingReads, onPage: suspend (List<XUser>) -> Unit): Int {
        val userId = reads.userId(screenName)
        val seen = HashSet<String>()
        var cursor: String? = null
        var total = 0
        while (true) {
            val page = reads.following(userId, cursor)
            val fresh = page.users.filter { it.id.isNotBlank() && it.screenName.isNotBlank() && seen.add(it.id) }
            if (fresh.isNotEmpty()) {
                total += fresh.size
                onPage(fresh)
            }
            val next = page.bottomCursor
            if (next.isNullOrEmpty() || next == "0" || next == cursor || fresh.isEmpty()) break
            cursor = next
        }
        return total
    }

    /** [users] followed here from [at], each in the Following feed; those already followed keep their place and switches. */
    fun followed(session: Session, users: List<XUser>, at: Long): Session = users.fold(session) { next, user -> next.follow(user, at) }
}
