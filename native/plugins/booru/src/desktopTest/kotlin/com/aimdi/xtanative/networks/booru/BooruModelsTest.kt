package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.booru.BooruEngine.DANBOORU
import com.aimdi.xtanative.networks.booru.BooruEngine.E621
import com.aimdi.xtanative.networks.booru.BooruEngine.GELBOORU_V2
import com.aimdi.xtanative.networks.booru.BooruEngine.MOEBOORU
import com.aimdi.xtanative.networks.booru.BooruRating.EXPLICIT
import com.aimdi.xtanative.networks.booru.BooruRating.GENERAL
import com.aimdi.xtanative.networks.booru.BooruRating.QUESTIONABLE
import com.aimdi.xtanative.networks.booru.BooruRating.SENSITIVE
import com.aimdi.xtanative.networks.booru.BooruTagCategory.ARTIST
import com.aimdi.xtanative.networks.booru.BooruTagCategory.META
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A post's pictures, its rating and its tag groups, as XTA's booru_models.dart and booru_tag_list.dart choose them. */
class BooruModelsTest {
    @Test
    fun aStillTilesItsSampleAndAVideoItsPreview() {
        val still = post(previewUrl = "https://cdn.example/p.jpg", sampleUrl = "https://cdn.example/l.jpg", fileUrl = "https://cdn.example/f.png", fileExt = "png")
        val video = post(previewUrl = "https://cdn.example/poster.jpg", sampleUrl = "https://cdn.example/clip.webm", fileUrl = "https://cdn.example/clip.webm", fileExt = "webm")
        assertEquals("https://cdn.example/l.jpg", still.catalogUrl)
        assertEquals("https://cdn.example/p.jpg", still.thumbnailUrl)
        assertEquals("https://cdn.example/l.jpg", still.displayUrl)
        assertEquals("https://cdn.example/f.png", still.originalUrl)
        assertEquals("https://cdn.example/poster.jpg", video.catalogUrl)
        assertTrue(video.isVideo)
        assertFalse(still.isVideo)
    }

    @Test
    fun withoutASampleTheOriginalStandsIn() {
        val post = post(previewUrl = "p", fileUrl = "f")
        assertEquals("f", post.displayUrl)
        assertEquals("f", post.catalogUrl)
        assertEquals("p", post.thumbnailUrl)
        assertEquals("", post(fileUrl = null).displayUrl)
        assertEquals(1.0, post(width = 0, height = 0).aspectRatio)
        assertEquals(0.75, post(width = 750, height = 1000).aspectRatio)
    }

    @Test
    fun aPostIsKnownByItsHostAndIdTogether() {
        assertEquals("https://danbooru.donmai.us:42", post("42", host = "https://danbooru.donmai.us").key)
        assertEquals("solo smile", post().tagLine)
        assertTrue(post(parentId = "4").hasFamily)
        assertFalse(post().hasFamily)
    }

    @Test
    fun aStoredCapReadsSAsSensitiveButEachEngineSendsItsOwnS() {
        assertEquals(SENSITIVE, BooruRating.parse("s"))
        assertEquals(GENERAL, BooruRating.parse("safe"))
        assertNull(BooruRating.parse(""))
        assertEquals(GENERAL, BooruRating.parseWire("s", MOEBOORU))
        assertEquals(GENERAL, BooruRating.parseWire("s", E621))
        assertEquals(GENERAL, BooruRating.parseWire("s", GELBOORU_V2))
        assertEquals(SENSITIVE, BooruRating.parseWire("sensitive", GELBOORU_V2))
        assertEquals(SENSITIVE, BooruRating.parseWire("s", DANBOORU))
        assertNull(BooruRating.parseWire("x", E621))
        assertTrue(EXPLICIT.exceeds(GENERAL))
        assertFalse(GENERAL.exceeds(EXPLICIT))
    }

    @Test
    fun aPostOverTheCapIsNotShownAndOneWithoutARatingIs() {
        assertFalse(BooruRatingRules.allows(post(rating = EXPLICIT), GENERAL))
        assertTrue(BooruRatingRules.allows(post(rating = EXPLICIT), EXPLICIT))
        assertTrue(BooruRatingRules.allows(post(rating = null), GENERAL))
    }

    @Test
    fun aCapPastSensitiveNeedsTheReaderToConfirmTheyAreAnAdult() {
        assertTrue(BooruRatingRules.needsAdultConsent(GENERAL, QUESTIONABLE))
        assertTrue(BooruRatingRules.needsAdultConsent(SENSITIVE, EXPLICIT))
        assertFalse(BooruRatingRules.needsAdultConsent(QUESTIONABLE, EXPLICIT), "already confirmed")
        assertFalse(BooruRatingRules.needsAdultConsent(GENERAL, SENSITIVE))
        assertEquals(setOf(QUESTIONABLE, EXPLICIT), BooruRatingRules.BLURRED)
    }

    @Test
    fun mostlyExplicitHostsStartWithAnExplicitCap() {
        assertTrue(BooruRatingRules.isMostlyExplicit("https://rule34.xxx"))
        assertTrue(BooruRatingRules.isMostlyExplicit("https://gelbooru.com"))
        assertFalse(BooruRatingRules.isMostlyExplicit("https://danbooru.donmai.us"))
        assertFalse(BooruRatingRules.isMostlyExplicit("https://safebooru.org"))
        assertEquals(EXPLICIT, BooruRatingRules.defaultCap("e621.net"))
        assertEquals(GENERAL, BooruRatingRules.defaultCap("https://e926.net"))
        assertEquals(GENERAL, BooruSettings().ratingCap)
        assertEquals(EXPLICIT, BooruSettings(BooruEngine.GELBOORU_V2, "rule34.xxx").ratingCap)
        assertEquals(SENSITIVE, BooruSettings(BooruEngine.GELBOORU_V2, "rule34.xxx", maxRating = SENSITIVE).ratingCap)
    }

    @Test
    fun aPageKeepsWhatTheBlacklistHidAndHandsOutAsEveryNetworksPage() {
        val (shown, hidden) = post("1") to post("2", tags = listOf("spoilers"))
        val page = BooruPostPage(listOf(shown), next = "2", allowed = listOf(hidden, shown))
        assertEquals(listOf(hidden), page.hidden)
        assertEquals(Page(listOf(shown), "2"), page.toPage())
        assertTrue(BooruPostPage(listOf(shown), next = null).hidden.isEmpty())
    }

    @Test
    fun kindsOrderTheGroupsUnknownTagsCountAsGeneralAndNamesSortAToZ() {
        val info = mapOf(
            "kantoku" to BooruTagInfo(ARTIST),
            "solo" to BooruTagInfo(BooruTagCategory.GENERAL),
            "highres" to BooruTagInfo(META),
        )
        val groups = BooruTagGroups.of(listOf("solo", "kantoku", "highres", "mystery"), info)
        assertEquals(listOf(ARTIST, BooruTagCategory.GENERAL, META), groups.map { it.category })
        assertEquals(listOf("mystery", "solo"), groups[1].tags)
        assertEquals(BooruTagGroup(null, listOf("a", "b")), BooruTagGroups.of(listOf("b", "a"), emptyMap()).single())
        assertTrue(BooruTagGroups.of(emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun byUseTheMostUsedComeFirstAndUncountedTagsLast() {
        val info = mapOf("a" to BooruTagInfo(postCount = 5), "b" to BooruTagInfo(postCount = 900), "c" to BooruTagInfo())
        assertEquals(listOf("b", "a", "c"), BooruTagGroups.of(listOf("c", "a", "b"), info, BooruTagSort.COUNT).single().tags)
    }

    @Test
    fun theArtistForMoreFromSkipsPlaceholderArtistTags() {
        val info = mapOf("unknown_artist" to BooruTagInfo(ARTIST), "kantoku" to BooruTagInfo(ARTIST))
        assertEquals("kantoku", BooruTagGroups.artistTag(listOf("unknown_artist", "kantoku"), info))
        assertNull(BooruTagGroups.artistTag(listOf("unknown_artist"), info))
    }
}
