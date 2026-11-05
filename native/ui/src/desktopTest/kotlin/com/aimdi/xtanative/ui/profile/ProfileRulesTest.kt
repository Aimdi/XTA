package com.aimdi.xtanative.ui.profile

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.x.XAnswerException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XUserUnavailableException
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.services.links.XProfileTab
import com.aimdi.xtanative.store.follows.RememberedFollows
import com.aimdi.xtanative.store.settings.ProfileTab as StoredTab
import com.aimdi.xtanative.ui.media.MediaGridItem
import com.aimdi.xtanative.ui.media.MediaGridItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileRulesTest {
    private fun user(id: String, protected: Boolean = false, follows: Boolean? = null) =
        XUser(id, id, id.uppercase(), protected = protected, followedByReader = follows)

    private fun post(id: String, media: List<XMedia> = emptyList(), live: XLive? = null, reposted: XPost? = null) =
        XPost(id, user("maya"), null, "text", media = media, live = live, reposted = reposted)

    private fun photo(n: Int) = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/p$n.jpg", 4, 3)

    private val video = XMedia(XMedia.Kind.VIDEO, "https://pbs.twimg.com/v.jpg", 16, 9)
    private val gif = XMedia(XMedia.Kind.GIF, "https://pbs.twimg.com/g.jpg", 1, 1)

    @Test
    fun aProfileOpensOnTheTabTheReaderChoseInXtasOrder() {
        assertEquals(listOf("POSTS", "REPLIES", "MEDIA", "SAVED"), ProfileTab.entries.map { it.name })
        assertEquals(ProfileTab.REPLIES, ProfileTab.opening(StoredTab.POSTS_AND_REPLIES))
        assertEquals(ProfileTab.SAVED, ProfileTab.opening(StoredTab.SAVED))
    }

    @Test
    fun aLinksTabOpensAndSurvivesTheRoute() {
        assertEquals(ProfileTab.MEDIA, ProfileTab.of(XProfileTab.MEDIA))
        assertEquals(ProfileTab.REPLIES, ProfileTab.of(XProfileTab.POSTS_AND_REPLIES))
        assertNull(ProfileTab.of(null))
        ProfileTab.entries.forEach { assertEquals(it, ProfileTab.ofRoute(it.routeName)) }
        assertNull(ProfileTab.ofRoute(null))
        assertNull(ProfileTab.ofRoute("highlights"))
    }

    @Test
    fun thePostsFilterTellsRepostsFromPostsAndAQuoteIsAPost() {
        val own = XThread("1", listOf(post("1")))
        val quote = XThread("2", listOf(post("2").copy(quoted = post("20"))))
        val repost = XThread("3", listOf(post("3", reposted = post("30"))))
        val all = listOf(own, quote, repost)
        assertEquals(all, all.filter(PostsFilter.ALL::accepts))
        assertEquals(listOf(own, quote), all.filter(PostsFilter.POSTS::accepts))
        assertEquals(listOf(repost), all.filter(PostsFilter.REPOSTS::accepts))
    }

    @Test
    fun theMediaGridHasATilePerPictureAndOneForABroadcastWhichKeepsItsVideoThumb() {
        val threads = listOf(
            XThread("1", listOf(post("1", media = listOf(photo(1), photo(2))))),
            XThread("2", listOf(post("2", media = listOf(gif)))),
            XThread("3", listOf(post("3", media = listOf(photo(3), video), live = XLive(broadcastId = "1abc")))),
            XThread("4", listOf(post("4", live = XLive(spaceId = "1room", imageUrl = "https://card/space.jpg")))),
        )
        val tiles = MediaGridItems.of(threads)
        assertEquals(listOf("1/0", "1/1", "2/0", "3/0", "4/0"), tiles.map { it.key })
        val kinds = listOf(MediaGridItem.Kind.PHOTO, MediaGridItem.Kind.PHOTO, MediaGridItem.Kind.GIF, MediaGridItem.Kind.BROADCAST, MediaGridItem.Kind.BROADCAST)
        assertEquals(kinds, tiles.map { it.kind })
        assertEquals("https://pbs.twimg.com/v.jpg", tiles[3].thumbnailUrl, "a live post with photos must not turn into photos")
        assertEquals("https://card/space.jpg", tiles[4].thumbnailUrl)
        assertEquals(listOf("2/0"), tiles.filter(MediaFilter.VIDEOS::accepts).map { it.key }, "a GIF plays like a video")
        assertEquals(listOf("3/0", "4/0"), tiles.filter(MediaFilter.BROADCASTS::accepts).map { it.key })
        assertEquals(listOf("1/0", "1/1"), tiles.filter(MediaFilter.PHOTOS::accepts).map { it.key })
        assertTrue(MediaFilter.BROADCASTS.readsPosts)
        assertEquals(12, MediaFilter.BROADCASTS.lookahead)
        assertEquals(4, MediaFilter.ALL.lookahead)

        val seen = HashSet<String>()
        assertEquals(5, MediaGridItems.unseen(tiles, seen).size)
        assertEquals(emptyList(), MediaGridItems.unseen(tiles.take(2), seen), "pages overlap at their edges")
    }

    @Test
    fun whatXSaidOfAProfileIsThePageAndAnythingElseIsRetried() {
        assertEquals(ProfileFailure.SUSPENDED, ProfileFailure.of(XUserUnavailableException("maya", "Suspended")))
        assertEquals(ProfileFailure.NOT_FOUND, ProfileFailure.of(XUserUnavailableException("maya", null)))
        assertNull(ProfileFailure.of(XUserUnavailableException("maya", "SomethingNew")))
        assertEquals(ProfileFailure.PRIVATE, ProfileFailure.of(XAnswerException(22, "protected")))
        assertEquals(ProfileFailure.NOT_FOUND, ProfileFailure.of(XAnswerException(50, "not found")))
        assertNull(ProfileFailure.of(XAnswerException(88, "rate limit")))
        assertNull(ProfileFailure.of(XRateLimitedException()))
    }

    @Test
    fun theHeaderAsksForSizesXServesAndWritesTheSiteShort() {
        assertEquals("https://pbs.twimg.com/profile_banners/1/2/1080x360", ProfileHeader.bannerUrl("https://pbs.twimg.com/profile_banners/1/2"))
        assertEquals("https://pbs.twimg.com/profile_banners/1/2/600x200", ProfileHeader.bannerUrl("https://pbs.twimg.com/profile_banners/1/2/600x200"))
        assertEquals("https://pbs.twimg.com/a_400x400.jpg", ProfileHeader.fullAvatarUrl("https://pbs.twimg.com/a_normal.jpg"))
        assertEquals("example.com/me", ProfileHeader.websiteLabel("https://www.example.com/me/"))
        assertEquals("from:@maya ", ProfileHeader.searchQuery(user("maya")))
    }

    @Test
    fun aProtectedAccountsPostsAreGatedUnlessItFollowsOrIsTheReadersOwn() {
        assertTrue(ProfileHeader.postsGated(user("maya", protected = true), listOf("someone")))
        assertFalse(ProfileHeader.postsGated(user("maya", protected = true, follows = true), listOf("someone")))
        assertFalse(ProfileHeader.postsGated(user("maya", protected = true), listOf("@MAYA")), "X reports an account as not following itself")
        assertFalse(ProfileHeader.postsGated(user("maya"), emptyList()))
    }

    @Test
    fun aQuietAccountCapIsOneOfXtasChoices() {
        assertEquals(listOf(null, 1, 2, 3, 5, 10), QuietAccount.CHOICES)
        assertEquals(5, QuietAccount.choiceOf(5))
        assertNull(QuietAccount.choiceOf(4), "anything else reads as off")
        assertNull(QuietAccount.choiceOf(null))
    }

    @Test
    fun aPersonSentAgainIsListedOnceAndARepeatedOrZeroCursorEndsTheList() {
        val paging = PeoplePaging()
        assertEquals(PeoplePage(listOf(user("a"), user("b")), "c1"), paging.page(listOf(user("a"), user("b")), "c1", null))
        assertEquals(PeoplePage(listOf(user("c")), null), paging.page(listOf(user("b"), user("c")), "c1", "c1"))
        assertEquals(PeoplePage(emptyList(), null), paging.page(listOf(user("a")), "c2", "c1"), "nobody new")
        assertNull(PeoplePaging().page(listOf(user("z")), "0", null).next)
        paging.reset()
        assertEquals(1, paging.page(listOf(user("a")), "c", null).people.size)
    }

    @Test
    fun followedByNamesWhoFollowsMostRecentFirstFromTheListsAlreadyRead() {
        fun followed(id: String) = FollowedAccount(id, id, id.uppercase(), null, false, true, 0)
        val following = listOf(followed("a"), followed("b"), followed("c"), followed("d"), followed("p"))
        val remembered = mapOf(
            "a" to RememberedFollows(0, KnownFollows.of(listOf(user("x"), user("p")))),
            "b" to RememberedFollows(0, KnownFollows.of(listOf(user("p")))),
            "c" to RememberedFollows(0, KnownFollows.of(listOf(user("y")))),
        )
        val followedBy = FollowedBy.of("p", remembered, following)
        assertEquals(listOf("b", "a"), followedBy.followers.map { it.id }, "b followed most recently")
        assertEquals(3, followedBy.checked)
        assertEquals(4, followedBy.total, "the profile itself is not counted")
        assertEquals(FollowedBy.Label.Names(listOf("B", "A")), followedBy.label)
        assertNull(FollowedBy().label)

        val many = FollowedBy(listOf(followed("a"), followed("b"), followed("c"), followed("d")))
        assertEquals(FollowedBy.Label.Many("A", "B", 2), many.label)
    }

    @Test
    fun aProfilesTimelinesAreKeptUnderXtasKeysNeverMixed() {
        assertEquals("profile:42:profile:noreplies", ProfileCache.key("42", ProfileCache.Timeline.POSTS))
        assertEquals("profile:42:reposts:noreplies", ProfileCache.key("42", ProfileCache.Timeline.REPOSTS))
        assertEquals("profile:42:profile:replies", ProfileCache.key("42", ProfileCache.Timeline.REPLIES))
        assertEquals(15 * 60 * 1000L, ProfileCache.MAX_AGE_MS)
    }

    @Test
    fun aFollowingListIsKeptAsWhoItFollowsNewestFirst() {
        val kept = KnownFollows.of(listOf(user("p"), user(""), user("q")))
        assertEquals(listOf("p", "q"), kept.map { it.id })
    }
}
