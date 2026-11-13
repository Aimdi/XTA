package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.networks.threads.ThreadsTextFragment.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The profile a guest page's own user object tells, over what its meta tags guessed. */
class ThreadsProfileOwnerTest {
    private val page = fixture("profile_owner_page.html")

    @Test
    fun theOwnersObjectGivesTheExactFollowersTheBadgeTheLargestPictureAndTheBioAsMetaSplitIt() {
        val profile = ThreadsProfileOwner.enrich(ThreadsPages.guestProfile(page, "zuck"), page, "zuck")!!
        assertEquals(5_812_345L, profile.followerCount, "the figure Meta keeps, not the meta tags' 5.7M")
        assertEquals(1_330L, profile.mediaCount, "the meta tags' count stays where the object has none")
        assertNull(profile.followingCount, "a guest is still not told whom a profile follows")
        assertTrue(profile.isVerified)
        assertEquals("https://scontent.cdninstagram.com/v/zuck_1080.jpg", profile.pictureUrl)
        assertEquals("Mostly MMA takes with @dana #mma", profile.biography, "the bio, not the description's boilerplate")
        assertEquals(listOf(Kind.TEXT, Kind.MENTION, Kind.TEXT, Kind.TAG), profile.facts.bioFragments.map { it.kind })
        assertEquals("dana", profile.facts.bioFragments[1].target)
        assertFalse(profile.facts.roundedFollowers)
        assertTrue(profile.facts.roundedPosts)
    }

    @Test
    fun theLinksUnderTheBioAreUnwrappedOnceEachAndOneWithoutAnAddressIsSkipped() {
        val links = ThreadsProfileOwner.owner(page, "@Zuck")!!.facts.links
        assertEquals(
            listOf(ThreadsBioLink("https://about.meta.com/", "Meta"), ThreadsBioLink("https://www.threads.com/@zuck"), ThreadsBioLink("https://example.org/blog")),
            links,
        )
    }

    @Test
    fun aPageWithoutTheObjectKeepsTheMetaCardAndMarksItsCountsRounded() {
        val old = fixture("profile_page.html")
        val profile = ThreadsProfileOwner.enrich(ThreadsPages.guestProfile(old, "zuck"), old, "zuck")!!
        assertEquals(5_700_000L, profile.followerCount)
        assertTrue(profile.facts.roundedFollowers)
        assertTrue(profile.facts.bioFragments.isEmpty())
        assertNull(ThreadsProfileOwner.owner(old, "someone"))
    }

    @Test
    fun aBioWhoseSplitNoLongerSpellsItIsReadByPatterns() {
        val reshaped = page.replace("\"plaintext\":\"Mostly MMA takes with \"", "\"plaintext\":\"Other words \"")
        assertTrue(ThreadsProfileOwner.owner(reshaped, "zuck")!!.facts.bioFragments.isEmpty())
    }

    @Test
    fun anObjectMissingEveryCountTellsNoneRatherThanZero() {
        val bare = page.replace("\"follower_count\":5812345,", "")
        val owner = ThreadsProfileOwner.owner(bare, "zuck")!!
        assertNull(owner.followerCount)
        assertNull(owner.mediaCount)
        val merged = ThreadsProfileOwner.enrich(ThreadsPages.guestProfile(bare, "zuck"), bare, "zuck")!!
        assertEquals(5_700_000L, merged.followerCount)
        assertTrue(merged.facts.roundedFollowers)
    }

    @Test
    fun theGuestCardReadsTheOwnersObjectFromTheSamePage() = runBlocking {
        val profile = direct(FakeMeta { ok(page) }).guestProfile("zuck")
        assertEquals(5_812_345L, profile.followerCount)
        assertTrue(profile.isVerified)
    }
}
