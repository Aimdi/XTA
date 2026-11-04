package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XUserPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class FollowingImportTest {
    private fun user(id: String) = XUser(id, "user$id", "User $id")

    private class Pages(private val pages: Map<String?, XUserPage>) : FollowingReads {
        val asked = ArrayList<String?>()

        override suspend fun userId(screenName: String): String = "id-$screenName"

        override suspend fun following(userId: String, cursor: String?): XUserPage {
            asked += cursor
            return pages.getValue(cursor)
        }
    }

    @Test
    fun everyPageIsReadUntilTheCursorEndsAndNobodyComesTwice(): Unit = runBlocking {
        val reads = Pages(
            mapOf(
                null to XUserPage(listOf(user("1"), user("2")), "a"),
                "a" to XUserPage(listOf(user("2"), user("3"), XUser("", "nobody", "No id")), "b"),
                "b" to XUserPage(listOf(user("4")), "0"),
            ),
        )
        val pages = ArrayList<List<String>>()
        val total = FollowingImport.read("maya", reads) { users -> pages += users.map { it.id } }
        assertEquals(4, total)
        assertEquals(listOf(listOf("1", "2"), listOf("3"), listOf("4")), pages)
        assertEquals(listOf(null, "a", "b"), reads.asked)
    }

    @Test
    fun aPageThatBringsNobodyNewOrTheSameCursorEndsIt(): Unit = runBlocking {
        val repeated = Pages(mapOf(null to XUserPage(listOf(user("1")), "a"), "a" to XUserPage(listOf(user("1")), "b")))
        assertEquals(1, FollowingImport.read("maya", repeated) {})
        assertEquals(listOf(null, "a"), repeated.asked)
        val stuck = Pages(mapOf(null to XUserPage(listOf(user("1")), "a"), "a" to XUserPage(listOf(user("2")), "a")))
        assertEquals(2, FollowingImport.read("maya", stuck) {})
        assertEquals(listOf(null, "a"), stuck.asked)
    }

    @Test
    fun theImportedAreFollowedAndThoseFollowedBeforeKeepTheirPlace() {
        val known = FollowedAccount("1", "old", "Old", null, false, inFeed = false, followedAt = 5)
        val session = FollowingImport.followed(Session(following = listOf(known)), listOf(user("1"), user("2")), at = 99)
        assertEquals(listOf("1", "2"), session.following.map { it.id })
        assertEquals(known.copy(screenName = "user1", name = "User 1"), session.following[0])
        assertEquals(99, session.following[1].followedAt)
        assertEquals(true, session.following[1].inFeed)
    }
}
