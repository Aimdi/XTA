package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivAuthUser
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixivPeopleTest {
    private val user = PixivUser(7, "Seven", "seven", avatarUrl = "https://i.pximg.net/a.png")

    @Test
    fun aFollowIsKeptAsXtasGroupSubscriptionAndComesBack() {
        val followed = PixivFollows.followed(Settings(), user, "2026-01-02T03:04:05Z")
        assertTrue(PixivFollows.isFollowed(followed, 7))
        assertEquals(listOf(PixivFollow(7, "seven", "Seven", "https://i.pximg.net/a.png", "2026-01-02T03:04:05Z")), PixivFollows.of(followed))
        assertTrue(followed[PixivPrefs.groupSubscriptions].contains("\"screen_name\":\"seven\""))
    }

    @Test
    fun followingAgainUpdatesTheRowAndKeepsWhenItBegan() {
        val first = PixivFollows.followed(Settings(), user, "2026-01-01T00:00:00Z")
        val again = PixivFollows.followed(first, user.copy(name = "Seven Renamed"), "2026-02-01T00:00:00Z")
        val follow = PixivFollows.of(again).single()
        assertEquals("Seven Renamed", follow.name)
        assertEquals("2026-01-01T00:00:00Z", follow.createdAt)
    }

    @Test
    fun anUnfollowLeavesTheOthersAndTheirOwnFields() {
        val stored = Settings().with(PixivPrefs.groupSubscriptions, """[{"id":"1","screen_name":"a","name":"A","extra":true},{"id":"2","screen_name":"b","name":"B"}]""")
        val left = PixivFollows.unfollowed(stored, 2)
        assertFalse(PixivFollows.isFollowed(left, 2))
        assertTrue(left[PixivPrefs.groupSubscriptions].contains("\"extra\":true"))
    }

    @Test
    fun aSignInIsRememberedOnlyForTheAccountInUse() {
        val inUse = Settings().with(PixivPrefs.userId, 7L).with(PixivPrefs.refreshToken, "token-7")
        val kept = PixivAccounts.remembered(inUse, PixivAuthUser(7, "Seven", "seven", isPremium = true))
        assertEquals(listOf(PixivAccount(7, "Seven", "seven", "token-7", isPremium = true)), PixivAccounts.of(kept))
        assertEquals(inUse, PixivAccounts.remembered(inUse, PixivAuthUser(8, "Eight", "eight")))
    }

    @Test
    fun beforeASwitchTheAccountInUseKeepsItsNewestToken() {
        val stored = PixivAccounts.remembered(Settings().with(PixivPrefs.userId, 7L).with(PixivPrefs.refreshToken, "old"), PixivAuthUser(7, "Seven", "seven"))
        val rotated = stored.with(PixivPrefs.refreshToken, "new")
        assertEquals("new", PixivAccounts.of(PixivAccounts.keepingActive(rotated)).single().refreshToken)
    }

    @Test
    fun anAccountNeverConfirmedJoinsByItsId() {
        val pasted = Settings().with(PixivPrefs.userId, 9L).with(PixivPrefs.refreshToken, "pasted")
        assertEquals(listOf(9L), PixivAccounts.of(PixivAccounts.keepingActive(pasted)).map { it.userId })
        assertTrue(PixivAccounts.of(PixivAccounts.without(PixivAccounts.keepingActive(pasted), 9)).isEmpty())
    }

    @Test
    fun aTokenNeverPrintsWithItsAccount() {
        assertFalse(PixivAccount(1, "n", "a", "secret").toString().contains("secret"))
    }
}
