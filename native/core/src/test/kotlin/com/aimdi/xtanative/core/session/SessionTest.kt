package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.backup.XtaBackup
import com.aimdi.xtanative.core.x.XAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionTest {
    private val session = Session(
        accounts = listOf(XAccount("ct0", "maya", mapOf("Cookie" to "auth_token=a; ct0=ct0", "x-csrf-token" to "ct0"))),
        following = listOf(
            FollowedAccount("1", "nasa", "NASA", "https://pbs.twimg.com/a_normal.jpg", verified = true, inFeed = true, followedAt = 5),
            FollowedAccount("2", "esa", "ESA", null, verified = false, inFeed = false, followedAt = 3),
        ),
        includeReplies = false,
    )

    @Test
    fun aSessionReadsBackAsItWasWritten() {
        assertEquals(session, Session.decode(Session.encode(session)))
    }

    @Test
    fun theFeedReadsOnlyTheAccountsXtaShowsInIt() {
        assertEquals(listOf("nasa"), session.feedMembers.map { it.screenName })
    }

    @Test
    fun importingReplacesSessionsAndFollowsButKeepsTheSwitches() {
        val imported = session.importing(XtaBackup(emptyList(), session.following.take(1), emptyList()))
        assertEquals(emptyList(), imported.accounts)
        assertEquals(1, imported.following.size)
        assertEquals(false, imported.includeReplies)
    }

    @Test
    fun nothingToReadIsNoSession() {
        assertNull(Session.decode(""))
        assertNull(Session.decode("""{"v": 7}"""))
    }
}
