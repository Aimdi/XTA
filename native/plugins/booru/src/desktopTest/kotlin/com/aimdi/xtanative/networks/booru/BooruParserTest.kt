package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruEngine.DANBOORU
import com.aimdi.xtanative.networks.booru.BooruEngine.E621
import com.aimdi.xtanative.networks.booru.BooruEngine.GELBOORU_V2
import com.aimdi.xtanative.networks.booru.BooruEngine.MOEBOORU
import com.aimdi.xtanative.networks.booru.BooruTagCategory.ARTIST
import com.aimdi.xtanative.networks.booru.BooruTagCategory.CHARACTER
import com.aimdi.xtanative.networks.booru.BooruTagCategory.COPYRIGHT
import com.aimdi.xtanative.networks.booru.BooruTagCategory.GENERAL
import com.aimdi.xtanative.networks.booru.BooruTagCategory.META
import com.aimdi.xtanative.networks.booru.BooruTagCategory.SPECIES
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every engine's post and tag answers, read the way XTA's booru_parse.dart reads them. */
class BooruParserTest {
    private fun posts(fixture: String, engine: BooruEngine, host: String) = BooruParser.posts(Fixtures.json(fixture), engine, host)

    private fun danbooru() = posts("danbooru_posts.json", DANBOORU, "https://danbooru.donmai.us")

    @Test
    fun aDanbooruPostCarriesItsFilesSizesRatingSourceAndTagKinds() {
        val post = danbooru().first()
        assertEquals("7104589", post.id)
        assertEquals(BooruRating.GENERAL, post.rating)
        assertEquals(47, post.score)
        assertEquals(2480 to 3508, post.width to post.height)
        assertEquals("https://cdn.donmai.us/original/0f/1e/0f1e.png", post.fileUrl)
        assertEquals("https://cdn.donmai.us/sample/0f/1e/sample-0f1e.jpg", post.sampleUrl)
        assertEquals("https://cdn.donmai.us/180x180/0f/1e/0f1e.jpg", post.previewUrl)
        assertEquals("png", post.fileExt)
        assertEquals("https://www.pixiv.net/artworks/115900000", post.source)
        assertEquals(Instant.parse("2024-02-10T13:14:23.112Z"), post.createdAt)
        assertEquals(listOf("1girl", "frieren", "highres", "kantoku", "long_hair", "smile", "sousou_no_frieren"), post.tags)
        assertEquals(
            mapOf(
                "kantoku" to ARTIST, "sousou_no_frieren" to COPYRIGHT, "frieren" to CHARACTER,
                "1girl" to GENERAL, "long_hair" to GENERAL, "smile" to GENERAL, "highres" to META,
            ),
            post.tagCategories,
        )
        assertEquals("https://danbooru.donmai.us/posts/7104589", post.hostPageUrl)
    }

    @Test
    fun aDanbooruPostCarriesItsFileFactsVotesAndFamily() {
        val post = danbooru().first()
        assertEquals(2_304_812L, post.fileSize)
        assertEquals("0f1e2d3c4b5a69788796a5b4c3d2e1f0", post.md5)
        assertEquals(51, post.favCount)
        assertEquals(49, post.upScore)
        assertEquals(2, post.downScore, "down votes count up, whatever sign the host sends")
        assertNull(post.parentId)
        assertTrue(post.hasChildren)
        assertTrue(post.hasFamily)
    }

    @Test
    fun aTileGetsTheSmallestStillVariantThatFillsItNeverTheOriginal() {
        val post = danbooru().first()
        assertEquals(listOf(127, 254, 509, 850), post.variants.map { it.width })
        assertEquals("https://cdn.donmai.us/180x180/0f/1e/0f1e.jpg", post.tileUrlFor(120))
        assertEquals("https://cdn.donmai.us/360x360/0f/1e/0f1e.jpg", post.tileUrlFor(200))
        assertEquals("https://cdn.donmai.us/720x720/0f/1e/0f1e.webp", post.tileUrlFor(400))
        assertEquals("https://cdn.donmai.us/sample/0f/1e/sample-0f1e.jpg", post.tileUrlFor(800))
        assertEquals(post.catalogUrl, post.tileUrlFor(3000), "wider than every variant: the catalogue picture")
    }

    @Test
    fun aDanbooruAnimationPlaysItsWebmSampleAndItsTileStaysAStill() {
        val ugoira = danbooru()[2]
        assertEquals(BooruRating.SENSITIVE, ugoira.rating, "Danbooru's s is Sensitive")
        assertTrue(ugoira.isVideo)
        assertEquals("https://cdn.donmai.us/sample/aa/11/sample-aa11.webm", ugoira.videoUrl)
        assertEquals(ugoira.previewUrl, ugoira.catalogUrl)
        assertEquals("https://cdn.donmai.us/360x360/aa/11/aa11.jpg", ugoira.tileUrlFor(300))
        assertEquals(ugoira.catalogUrl, ugoira.tileUrlFor(600), "the webm sample is no tile")
        assertEquals("7104589", ugoira.parentId)
    }

    @Test
    fun aBannedPostAndAFlashPostHaveNoFileToShow() {
        val posts = danbooru().associateBy { it.id }
        assertFalse(posts.getValue("7104590").hasFile, "Danbooru withholds a banned post's file from guests")
        assertFalse(posts.getValue("1234").hasFile, "a Flash file is nothing a viewer opens")
        assertTrue(posts.getValue("7104589").hasFile)
    }

    @Test
    fun aMoebooruPostReadsSAsSafeWithItsUploaderAndFamily() {
        val (first, second) = posts("moebooru_posts.json", MOEBOORU, "https://yande.re")
        assertEquals(BooruRating.GENERAL, first.rating)
        assertEquals("mod", first.uploader)
        assertTrue(first.hasChildren)
        assertNull(first.parentId)
        assertEquals(99L, first.fileSize)
        assertEquals(Instant.ofEpochSecond(1_700_000_000), first.createdAt)
        assertEquals("https://yande.re/post/show/1171234", first.hostPageUrl)
        assertEquals(BooruRating.QUESTIONABLE, second.rating)
        assertEquals("1171234", second.parentId)
    }

    @Test
    fun aMoebooruChangeIsASequenceNumberNotATimeAsXtaReadIt() {
        val post = posts("moebooru_posts.json", MOEBOORU, "https://yande.re").first()
        assertNull(post.updatedAt, "yande.re's change is its change_seq")
        assertEquals(post.createdAt, post.feedDate)
    }

    @Test
    fun relativeFilesAreMadeAbsoluteOnTheirHost() {
        val post = posts("moebooru_posts.json", MOEBOORU, "https://yande.re")[1]
        assertEquals("https://yande.re/data/preview/ab.jpg", post.previewUrl)
        assertEquals("https://yande.re/data/sample/ab.jpg", post.catalogUrl)
        assertEquals("https://yande.re/data/image/ab.png", post.fileUrl)
    }

    @Test
    fun aGelbooruPostReadsItsRubyDateEscapedTagsStringFlagsAndEmptyParent() {
        val (first, second) = posts("gelbooru_posts.json", GELBOORU_V2, "https://gelbooru.com")
        assertEquals(listOf("girls'_frontline", "tom_&_jerry", "\"quote\""), first.tags)
        assertEquals(Instant.parse("2024-10-05T18:52:20Z"), first.createdAt)
        assertEquals(Instant.ofEpochSecond(1_728_154_340), first.updatedAt)
        assertEquals("uploader_1", first.uploader)
        assertEquals("def", first.md5)
        assertNull(first.parentId, "a parent of 0 is none")
        assertTrue(first.hasChildren, "Gelbooru sends booleans as strings")
        assertEquals(BooruRating.GENERAL, first.rating)
        assertEquals("https://gelbooru.com/index.php?page=post&s=view&id=8", first.hostPageUrl)
        assertEquals(BooruRating.SENSITIVE, second.rating, "newer Gelbooru sends the word")
        assertEquals(Instant.parse("2024-10-05T23:00:00Z"), second.createdAt)
        assertEquals("8", second.parentId)
        assertFalse(second.hasChildren)
    }

    @Test
    fun aGelbooruForkSendsItsPostsBareWithOnlyAChangeTime() {
        val post = posts("safebooru_posts.json", GELBOORU_V2, "https://safebooru.org").single()
        assertNull(post.createdAt, "a re-tag is not the upload")
        assertEquals(Instant.ofEpochSecond(1_700_000_000), post.updatedAt)
        assertEquals(post.updatedAt, post.feedDate, "feeds still order the post by it")
        assertEquals("9a1b", post.md5)
        assertNull(post.score)
        assertEquals(listOf("smile", "solo"), post.tags)
        assertEquals("https://safebooru.org/index.php?page=post&s=view&id=7", post.hostPageUrl)
    }

    @Test
    fun rule34GuestPostsComposeTheirFilesFromDirectoryAndImage() {
        val posts = posts("rule34_posts.json", GELBOORU_V2, "https://rule34.xxx")
        assertEquals("https://rule34.xxx/images/1234/abc.png", posts[0].fileUrl)
        assertEquals("https://rule34.xxx/thumbnails/1234/thumbnail_abc.jpg", posts[0].previewUrl)
        assertEquals(BooruRating.EXPLICIT, posts[0].rating)
        assertEquals("https://rule34.xxx/images/1235/def.png", posts[1].fileUrl, "a numeric directory")
    }

    @Test
    fun aVideoWithoutAPreviewNeverStandsInForItsOwnThumbnail() {
        val posts = posts("rule34_posts.json", GELBOORU_V2, "https://rule34.xxx")
        assertTrue(posts[2].isVideo)
        assertEquals("https://rule34.xxx/thumbnails/1236/thumbnail_ghi.jpg", posts[2].thumbnailUrl)
        assertEquals("", posts[3].thumbnailUrl)
        assertEquals("", posts[3].catalogUrl)
    }

    @Test
    fun rule34PostsReadOnItsApiHostOpenOnThePublicSite() {
        val post = posts("rule34_posts.json", GELBOORU_V2, "https://api.rule34.xxx").first()
        assertEquals("https://rule34.xxx/index.php?page=post&s=view&id=11", post.hostPageUrl)
    }

    @Test
    fun anE621PostReadsItsNestedFilesTagGroupsVotesAndRelationships() {
        val fox = posts("e621_posts.json", E621, "https://e621.net").first()
        assertEquals(BooruRating.GENERAL, fox.rating, "e621's s is Safe")
        assertEquals(3, fox.score)
        assertEquals(4 to 1, fox.upScore to fox.downScore)
        assertEquals(listOf("someone", "fox", "smile", "backstory"), fox.tags)
        assertEquals(mapOf("someone" to ARTIST, "fox" to SPECIES, "smile" to GENERAL, "backstory" to META), fox.tagCategories)
        assertEquals(7, fox.favCount)
        assertEquals(300L, fox.fileSize)
        assertEquals("aa", fox.md5)
        assertTrue(fox.hasChildren, "a listed child counts")
        assertEquals("https://example.com/fox", fox.source)
        assertEquals("uploader", fox.uploader)
        assertEquals(200 to 300, fox.width to fox.height)
        assertEquals(Instant.parse("2026-08-11T23:31:24.361Z"), fox.createdAt)
        assertEquals("https://e621.net/posts/6617540", fox.hostPageUrl)
    }

    @Test
    fun aGifShowsItsOriginalNotItsStillSample() {
        val (_, gif, withheld) = posts("e621_posts.json", E621, "https://e621.net")
        assertEquals("https://static1.e621.net/data/cc/dd/ccdd.gif", gif.displayUrl)
        assertEquals("https://static1.e621.net/data/sample/cc/dd/ccdd.jpg", gif.catalogUrl)
        assertEquals("6617540", gif.parentId)
        assertFalse(withheld.hasFile, "e621 withholds this file")
    }

    @Test
    fun tagRowsOfEachEngineMapTheirNumbersToTheSameKinds() {
        fun rows(fixture: String, engine: BooruEngine) = BooruParser.tagSuggestions(BooruTagReply.Json(Fixtures.json(fixture)), engine)
            .map { Triple(it.name, it.category, it.postCount) }
        assertEquals(
            listOf(Triple("blue_eyes", GENERAL, 2_524_807), Triple("kantoku", ARTIST, 3401), Triple("frieren", CHARACTER, 16_526)),
            rows("danbooru_tags.json", DANBOORU),
        )
        assertEquals(listOf(Triple("fox", SPECIES, 12)), rows("e621_tags.json", E621))
        assertEquals(
            listOf(Triple("kantoku", ARTIST, 50), Triple("highres", META, 9_000_000), Triple("girls'_frontline", COPYRIGHT, 1200)),
            rows("gelbooru_tags.json", GELBOORU_V2),
            "Gelbooru names a kind by number or word, counts as numbers or strings, and escapes names",
        )
    }

    @Test
    fun theSameNumberIsAnotherKindOnAnotherEngine() {
        assertEquals(META, BooruTagCategory.fromWire(5, DANBOORU))
        assertEquals(SPECIES, BooruTagCategory.fromWire(5, E621))
        assertEquals(ARTIST, BooruTagCategory.fromWire(5, MOEBOORU), "a Moebooru circle is a group of artists")
        assertEquals(META, BooruTagCategory.fromWire(7, E621))
        assertEquals(META, BooruTagCategory.fromWire(6, MOEBOORU))
        assertNull(BooruTagCategory.fromWire(6, DANBOORU))
        assertEquals(META, BooruTagCategory.named("faults"))
        assertEquals(GENERAL, BooruTagCategory.named("tag"))
    }

    @Test
    fun aGelbooruForkAnswersTagsInXml() {
        val rows = BooruParser.tagSuggestions(BooruTagReply.Xml(Fixtures.text("safebooru_tags.xml")), GELBOORU_V2)
        assertEquals(listOf("blue_eyes", "girls'_frontline"), rows.map { it.name })
        assertEquals(listOf(81_234, 900), rows.map { it.postCount })
        assertEquals(listOf(GENERAL, CHARACTER), rows.map { it.category })
    }

    @Test
    fun autocompleteRowsKeepTheOtherNameEachTagWasFoundThroughAndLeaveOutWhatIsNoTag() {
        val rows = BooruParser.autocomplete(Fixtures.json("danbooru_autocomplete.json"))!!
        assertEquals(listOf("hatsune_miku", "blue_eyes", "hatsune_miku", "kantai_collection", "same"), rows.map { it.name })
        assertEquals(listOf(null, "blue_eyse", "初音ミク", "kancolle", null), rows.map { it.alias })
        assertEquals(CHARACTER, rows.first().category)
        assertEquals(148_527, rows.first().postCount)
        assertEquals(COPYRIGHT, rows[3].category)
    }

    @Test
    fun anAutocompleteAnswerThatIsNotAListIsNoAnswer() {
        assertNull(BooruParser.autocomplete(J.parse("""{"error":"nope"}""")))
        assertEquals(emptyList(), BooruParser.autocomplete(J.parse("[]")))
    }

    @Test
    fun aMissingOrReshapedFieldIsNullOrEmptyNeverACrash() {
        val reshaped = """[{"id":"x1","score":"lots","image_width":"wide","tags":7,"media_asset":{"variants":"none"}},{"no":"id"},3,null]"""
        val post = BooruParser.posts(J.parse(reshaped), DANBOORU, "https://danbooru.donmai.us").single()
        assertEquals("x1", post.id)
        assertNull(post.score)
        assertEquals(0, post.width)
        assertTrue(post.tags.isEmpty())
        assertTrue(post.variants.isEmpty())
        assertTrue(BooruParser.posts(J.parse("""{"posts":"none"}"""), E621, "https://e621.net").isEmpty())
        assertTrue(BooruParser.posts(J.parse("""{"@attributes":{"count":0}}"""), GELBOORU_V2, "https://gelbooru.com").isEmpty())
        assertTrue(BooruParser.tagSuggestions(BooruTagReply.Xml("<html>busy</html>"), GELBOORU_V2).isEmpty())
    }

    @Test
    fun datesReadAsXtaReadsThem() {
        assertEquals(Instant.parse("2024-10-05T18:52:20Z"), BooruParser.gelbooruDate("Sat Oct 05 13:52:20 -0500 2024"))
        assertNull(BooruParser.gelbooruDate("someday"))
        assertEquals(Instant.parse("2026-08-11T23:31:24.361Z"), BooruDates.parse("2026-08-11T19:31:24.361-04:00"))
        assertEquals(Instant.parse("2024-01-02T03:04:05Z"), BooruDates.parse("2024-01-02 03:04:05Z"))
        assertEquals(Instant.parse("2024-01-02T03:04:05.123456Z"), BooruDates.parse("2024-01-02T03:04:05.1234567+00:00"))
        assertNull(BooruDates.parse("2024-13-45"))
        assertNull(BooruDates.parse("someday"))
    }
}
