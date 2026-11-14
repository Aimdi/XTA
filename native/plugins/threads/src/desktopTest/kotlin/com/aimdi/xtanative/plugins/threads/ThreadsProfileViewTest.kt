package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsLinkCard
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.store.backup.ProfileSubscriptionRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The profile screen's header and list choices: title, follow face, toggles, search, empty reasons and tab widths. */
class ThreadsProfileViewTest {
    private fun post(id: String, text: String = "post $id") = ThreadsPost(id = id, handle = "zuck", authorName = "Mark", text = text)

    private val picture = post("p").copy(media = listOf(ThreadsMedia(url = "https://cdn.example/p.jpg")))
    private val linked = post("l").copy(linkCard = ThreadsLinkCard("https://shop.example", title = "Black Friday deals"))
    private val plain = post("t", "Hello #threads friends")
    private val all = listOf(picture, linked, plain)

    @Test
    fun theTitleIsTheNameOnceReadElseTheHandle() {
        assertEquals("@zuck", ThreadsProfileView.title(null, "zuck"))
        assertEquals("Mark Zuckerberg", ThreadsProfileView.title(ThreadsProfile(pk = "1", id = "1", username = "zuck", fullName = "Mark Zuckerberg"), "zuck"))
        assertEquals("zuck", ThreadsProfileView.title(ThreadsProfile(pk = "1", id = "1", username = "zuck", fullName = " "), "zuck"))
    }

    @Test
    fun theBannerIsTheProfilesOwnPictureOrNone() {
        assertEquals("https://cdn.example/zuck.jpg", ThreadsProfileView.bannerUrl(ThreadsProfile(pk = "1", id = "1", username = "zuck", fullName = "Mark", profilePicUrl = "https://cdn.example/zuck.jpg")))
        assertNull(ThreadsProfileView.bannerUrl(ThreadsProfile(pk = "1", id = "1", username = "zuck", fullName = "Mark")))
    }

    @Test
    fun theFollowButtonWaitsForTheAccountsThenSaysWhetherTheDeviceFollows() {
        assertNull(ThreadsProfileView.followFace(null, "zuck"))
        assertEquals(ThreadsFollowFace.FOLLOW, ThreadsProfileView.followFace(emptyList(), "zuck"))
        assertEquals(ThreadsFollowFace.FOLLOWING, ThreadsProfileView.followFace(listOf(ProfileSubscriptionRow("zuck", "Mark")), "Zuck"))
    }

    @Test
    fun theTogglesNarrowToMediaOrLinksAndMediaHasNoMediaToggle() {
        assertEquals(all, ThreadsProfileView.shown(all, ThreadsFeedContent.ALL, ""))
        assertEquals(listOf(picture), ThreadsProfileView.shown(all, ThreadsFeedContent.MEDIA, ""))
        assertEquals(listOf(linked), ThreadsProfileView.shown(all, ThreadsFeedContent.LINKS, ""))
        assertEquals(listOf(ThreadsFeedContent.ALL, ThreadsFeedContent.LINKS), ThreadsProfileView.filters(mediaTab = true))
        assertEquals(ThreadsFeedContent.entries, ThreadsProfileView.filters(mediaTab = false))
        assertEquals(ThreadsFeedContent.ALL, ThreadsProfileView.chosen(ThreadsFeedContent.MEDIA, mediaTab = true))
        assertEquals(ThreadsFeedContent.LINKS, ThreadsProfileView.chosen(ThreadsFeedContent.LINKS, mediaTab = true))
    }

    @Test
    fun theSearchWantsEveryWordInAnyCaseInTheTextOrTheLinkCard() {
        assertEquals(listOf(plain), ThreadsProfileView.shown(all, ThreadsFeedContent.ALL, "  FRIENDS  #threads "))
        assertEquals(listOf(linked), ThreadsProfileView.shown(all, ThreadsFeedContent.ALL, "friday"))
        assertEquals(emptyList(), ThreadsProfileView.shown(all, ThreadsFeedContent.MEDIA, "friday"))
        assertEquals(all, ThreadsProfileView.shown(all, ThreadsFeedContent.ALL, "   "))
    }

    @Test
    fun anEmptyListSaysWhetherTheSearchOrTheToggleHidItsPosts() {
        assertEquals(ThreadsProfileEmpty.NOTHING, ThreadsProfileView.empty(emptyList(), ThreadsFeedContent.MEDIA, "x"))
        assertEquals(ThreadsProfileEmpty.NO_MATCH, ThreadsProfileView.empty(all, ThreadsFeedContent.MEDIA, "nothing"))
        assertEquals(ThreadsProfileEmpty.FILTERED, ThreadsProfileView.empty(listOf(plain), ThreadsFeedContent.MEDIA, ""))
    }

    @Test
    fun tabsShareTheWidthEquallyWhileTheyFitThenTakeWhatTheyNeedThenScroll() {
        assertEquals(listOf(100, 100, 100, 100), ThreadsProfileView.tabWidths(listOf(80, 90, 70, 100), 400))
        assertEquals(listOf(81, 191, 81, 81), ThreadsProfileView.tabWidths(listOf(50, 160, 50, 50), 434))
        assertEquals(listOf(200, 300), ThreadsProfileView.tabWidths(listOf(200, 300), 400))
        assertEquals(emptyList(), ThreadsProfileView.tabWidths(emptyList(), 400))
        assertEquals(listOf(133, 133, 134), ThreadsProfileView.tabWidths(listOf(10, 10, 10), 400))
    }

    @Test
    fun tabsCloseUpBeforeTheLastIsCutAtTheScreensEdge() {
        // Five tabs on a 390-wide phone: 403 with the room each likes, 323 without.
        val tight = listOf(55, 70, 61, 76, 61)
        assertEquals(listOf(68, 83, 74, 89, 76), ThreadsProfileView.tabWidths(tight, 390, comfort = 16), "their least widths and the room left shared out")
        assertEquals(390, ThreadsProfileView.tabWidths(tight, 390, comfort = 16).sum(), "the strip ends where the screen does")
        assertEquals(listOf(84, 84, 84, 84, 84), ThreadsProfileView.tabWidths(tight, 420, comfort = 8), "equal while each fits its share with its comfort")
        assertEquals(listOf(74, 89, 80, 95, 82), ThreadsProfileView.tabWidths(tight, 420, comfort = 16), "comfortable widths and the room left shared out")
        assertEquals(tight.map { it + 16 }, ThreadsProfileView.tabWidths(tight, 300, comfort = 16), "wider than the strip even closed up: comfortable, and it scrolls")
    }
}
