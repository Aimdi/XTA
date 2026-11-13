package com.aimdi.xtanative.networks.ehviewer

import java.time.Instant
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's test/eh_parse_test.dart and test/eh_detail_test.dart, on the pages' shapes. */
class EhParserTest {
    private fun detail(): EhGalleryDetail = assertNotNull(EhParser.galleryDetail(fixture("detail.html"), gid = 1, token = "abc"))

    @Test
    fun aCompactListRowReadsItsGalleryAndTheNextPage() {
        val page = EhParser.galleryList(fixture("list_compact.html"))
        val gallery = page.items.single()
        assertEquals(4114065L, gallery.gid)
        assertEquals("e6e34036d1", gallery.token)
        assertEquals("Sample Title", gallery.title)
        assertEquals(EhCategory.DOUJINSHI, gallery.category)
        assertEquals(12, gallery.pageCount)
        assertEquals("https://ehgt.org/a.webp", gallery.thumbUrl)
        assertEquals("Uploader", gallery.uploader)
        assertEquals("https://e-hentai.org/?next=1", page.next)
    }

    @Test
    fun theSitesPostedTimeReadsAsUtcAsGdatasUnixTimeDoes() {
        val gallery = EhParser.galleryList(fixture("list_compact.html")).items.single()
        assertEquals(Instant.parse("2026-08-11T21:36:00Z"), gallery.postedAt)
    }

    @Test
    fun ratingStarsTagsAndLanguageComeOffTheRow() {
        val gallery = EhParser.galleryList(fixture("list_rows.html")).items.first()
        assertEquals(3.5, gallery.rating)
        assertEquals(listOf("language:english", "language:translated", "female:big breasts"), gallery.tags)
        assertEquals("english", gallery.tagLanguage)
        assertEquals(24, gallery.pageCount)
    }

    @Test
    fun aLazyThumbnailReadsItsDataSrcNotThePlaceholderAndOnePageCountsAsOne() {
        val page = EhParser.galleryList(fixture("list_rows.html"))
        val lazy = page.items[1]
        assertEquals("Lazy Row", lazy.title)
        assertEquals("https://ehgt.org/w/lazy.webp", lazy.thumbUrl)
        assertEquals(1, lazy.pageCount)
        assertNull(lazy.rating)
        assertNull(page.next, "the last page's next is a span, not a link")
    }

    @Test
    fun starSpriteOffsets() {
        assertEquals(5.0, EhParser.starRating("background-position:0px -1px"))
        assertEquals(1.0, EhParser.starRating("background-position:-64px -1px"))
        assertEquals(0.0, EhParser.starRating("background-position:-80px -21px"))
        assertNull(EhParser.starRating(""))
        assertNull(EhParser.starRating(null))
    }

    @Test
    fun aToplistReadsOnThroughItsArrowLink() {
        val page = EhParser.galleryList(fixture("toplist.html"))
        val top = page.items.single()
        assertEquals("Top One", top.title)
        assertEquals(5.0, top.rating)
        assertEquals("Ann", top.uploader)
        assertEquals(30, top.pageCount)
        assertEquals("https://e-hentai.org/toplist.php?tl=11&p=1", page.next)
    }

    @Test
    fun aNextLinkOffTheSiteIsNotFollowedSinceTheCookiesWouldGoWithIt() {
        val html = fixture("list_compact.html").replace("https://e-hentai.org/?next=1", "https://elsewhere.example/?next=1")
        assertNull(EhParser.galleryList(html).next)
    }

    @Test
    fun aGalleryPageReadsItsTitlesTagsAndPreviews() {
        val detail = assertNotNull(EhParser.galleryDetail(fixture("detail_previews.html"), gid = 9, token = "tok"))
        val gallery = detail.gallery
        assertEquals("English Title", gallery.title)
        assertEquals("日本語", gallery.titleJpn)
        assertEquals(EhCategory.MANGA, gallery.category)
        assertEquals("Alice", gallery.uploader)
        assertEquals(Instant.parse("2026-01-02T03:04:00Z"), gallery.postedAt)
        assertEquals(3, gallery.pageCount)
        assertEquals(4.5, gallery.rating)
        assertTrue(gallery.tags.containsAll(listOf("artist:bob", "female:solo")))
        assertEquals(listOf(1, 2), detail.previews.map { it.page })
        assertEquals("aaa111", detail.previews.first().pageToken)
        assertEquals("https://ehgt.org/s.webp", detail.previews.first().thumbUrl)
        assertEquals(0.0, detail.previews.first().thumbOffsetX)
        assertEquals(-200.0, detail.previews[1].thumbOffsetX)
        assertEquals(0, detail.previewSheetIndex)
        assertEquals(3, detail.previewSheetCount)
    }

    @Test
    fun theCoverIsReadOffGd1ItselfNotTheFirstTileAfterIt() {
        assertEquals("https://ehgt.org/c.webp", EhParser.galleryDetail(fixture("detail_previews.html"), 9, "tok")?.gallery?.thumbUrl)
    }

    @Test
    fun aCommentsLinksAreNeitherTilesNorSheets() {
        val detail = assertNotNull(EhParser.galleryDetail(fixture("detail_previews.html"), 9, "tok"))
        assertEquals(listOf(1, 2), detail.previews.map { it.page }, "a link to another gallery's page 9 is no tile")
        assertEquals(3, detail.previewSheetCount, "a link to another gallery's sheet 12 is no sheet of this one")
        assertEquals("Carol", detail.comments.single().author)
        assertEquals("See this one and its page 9", detail.comments.single().body)
    }

    @Test
    fun tagsKeepTheirSpacesAndTheirNamespaces() {
        assertEquals(
            listOf("language:english", "language:translated", "female:big breasts", "female:twin tails", "parody:original"),
            detail().gallery.tags,
        )
    }

    @Test
    fun dashedTagsAreWeak() {
        assertEquals(setOf("female:twin tails", "parody:original"), detail().weakTags)
    }

    @Test
    fun aGallerysTagsGroupInTheSitesOrder() {
        assertEquals(listOf(EhNamespace.LANGUAGE, EhNamespace.PARODY, EhNamespace.FEMALE), detail().tagGroups.map { it.namespace })
    }

    @Test
    fun factsFromTheDetailTable() {
        val detail = detail()
        assertEquals("English", detail.language)
        assertTrue(detail.translated)
        assertEquals((96.57 * 1024 * 1024).roundToLong(), detail.fileSizeBytes)
        assertEquals(1234, detail.favoritedCount)
        assertEquals(321, detail.ratingCount)
        assertEquals(4.62, detail.gallery.rating)
        assertEquals(24, detail.gallery.pageCount)
        assertEquals(Instant.parse("2024-08-11T21:36:00Z"), detail.gallery.postedAt)
    }

    @Test
    fun aBareTagCellFallsBackToItsId() {
        assertEquals("artist:big bob", EhParser.detailTags("""<div id="td_artist:big_bob"></div>""").single().raw)
    }

    @Test
    fun sizesAndFavoriteCountsReadAsTheSiteWritesThem() {
        assertEquals(512L * 1024, EhParser.fileSize("512 KiB"))
        assertEquals((1.5 * 1024 * 1024 * 1024).roundToLong(), EhParser.fileSize("1.5 GiB"))
        assertNull(EhParser.fileSize("?"))
        assertEquals(0, EhParser.favorited("Never"))
        assertEquals(1, EhParser.favorited("Once"))
        assertEquals(87, EhParser.favorited("87 times"))
    }

    @Test
    fun tilesReadTheirSheetOffsetAndSizeFromTheStyle() {
        val tiles = EhParser.previewSheet(galleryHtml(sheet = 1, thumbs = true))
        assertEquals(listOf(5, 6, 7, 8), tiles.map { it.page })
        assertEquals("https://ehgt.org/sheet1.webp", tiles[1].thumbUrl)
        assertEquals(-200.0, tiles[1].thumbOffsetX)
        assertEquals(200.0, tiles[1].thumbWidth)
        assertEquals(283.0, tiles[1].thumbHeight)
    }

    @Test
    fun aLoneThumbnailWithoutAnOffsetStillHasItsImage() {
        val tiles = EhParser.previewSheet(
            """<a href="https://e-hentai.org/s/aa/9-1"><div style="width:200px;height:290px;""" +
                """background:transparent url(https://ehgt.org/one.webp) no-repeat"></div></a>""" +
                """<a href="https://e-hentai.org/s/bb/9-2"><img alt="2" src="https://ehgt.org/two.jpg" /></a>""",
        )
        assertEquals(listOf("https://ehgt.org/one.webp", "https://ehgt.org/two.jpg"), tiles.map { it.thumbUrl })
        assertNull(tiles.first().thumbOffsetX)
        assertEquals(290.0, tiles.first().thumbHeight)
    }

    @Test
    fun anImagePageReadsItsImageNeighboursCounterAndReloadKey() {
        val page = assertNotNull(EhParser.imagePage(fixture("image_page.html"), page = 2))
        assertEquals("https://cdn.example/page.webp", page.imageUrl)
        assertNull(page.originalImageUrl)
        assertEquals("https://cdn.example/page.webp", page.displayUrl(signedIn = false))
        assertTrue(page.nextPageUrl!!.contains("1-3"))
        assertTrue(page.prevPageUrl!!.contains("1-1"))
        assertEquals(EhPageLink("ccc", 1, 3), page.nextPage)
        assertEquals(EhPageLink("aaa", 1, 1), page.prevPage)
        assertEquals("41173-496295", page.reloadKey)
        assertEquals(24, page.pageCount)
    }

    @Test
    fun fullimgPhpIsTheOriginalAndOnlyASignedInReaderUsesIt() {
        val page = assertNotNull(EhParser.imagePage(fixture("image_page_fullimg.html"), page = 2))
        assertEquals("https://hath.example/h/abc/keystamp=1;xres=1280/page.jpg", page.imageUrl)
        assertEquals("https://e-hentai.org/fullimg.php?gid=9&page=2&key=abc", page.originalImageUrl)
        assertEquals(page.originalImageUrl, page.displayUrl(signedIn = true))
        assertEquals(page.imageUrl, page.displayUrl(signedIn = false))
        assertEquals(listOf(page.originalImageUrl, page.imageUrl), page.imageUrls(signedIn = true), "the resample stands in when the original fails")
        assertEquals(listOf(page.imageUrl), page.imageUrls(signedIn = false))
    }

    @Test
    fun theCurrentFullimgPathIsTheOriginalToo() {
        val page = assertNotNull(EhParser.imagePage(fixture("image_page_fullimg_path.html"), page = 1))
        assertEquals("https://e-hentai.org/fullimg/4116360/1/g8l82w4amxz/0001.png", page.originalImageUrl)
        assertEquals(page.originalImageUrl, page.displayUrl(signedIn = true))
    }

    @Test
    fun aPageWithoutAnImageIsNoImagePage() {
        assertNull(EhParser.imagePage("<p>Gallery not found.</p>", page = 1))
    }

    @Test
    fun theReloadKeyComesFromTheNlCall() {
        assertEquals("41173-496295", EhParser.reloadKey("""onerror="nl('41173-496295')""""))
        assertNull(EhParser.reloadKey("<img id=\"img\" />"))
    }

    @Test
    fun aPageLinkReadsTokenGidAndPage() {
        assertEquals(EhPageLink("d9987f2de6", 4113416, 2), EhPageLink.parse("https://e-hentai.org/s/d9987f2de6/4113416-2"))
        assertEquals(EhPageLink("x1", 5, 7), EhPageLink.parse("https://exhentai.org/s/x1/5-7"))
        assertNull(EhPageLink.parse(null))
        assertNull(EhPageLink.parse("https://e-hentai.org/g/1/abc/"))
    }

    @Test
    fun commentsReadAuthorBodyScoreAndTheUploadersMark() {
        val comments = EhParser.comments(fixture("comments.html"))
        assertEquals(2, comments.size)
        assertEquals("Alice", comments[0].author)
        assertEquals("13 August 2026, 16:44", comments[0].posted)
        assertTrue(comments[0].uploader)
        assertEquals("Hello\nworld", comments[0].body)
        assertNull(comments[0].score)
        assertEquals("Bob", comments[1].author)
        assertEquals("+30", comments[1].score)
        assertFalse(comments[1].uploader)
    }

    @Test
    fun gdataMapsTheApisGalleriesAndLeavesOutOnesItCouldNotFind() {
        val gallery = EhParser.gdata(fixture("gdata.json")).single()
        assertEquals(1L, gallery.gid)
        assertEquals("abc", gallery.token)
        assertEquals("日", gallery.titleJpn)
        assertEquals(EhCategory.WESTERN, gallery.category)
        assertEquals(5, gallery.pageCount)
        assertEquals(3.2, gallery.rating)
        assertEquals(Instant.ofEpochSecond(1_700_000_000), gallery.postedAt)
        assertEquals(listOf("language:english"), gallery.tags)
        assertTrue(EhParser.gdata("not json").isEmpty())
    }

    @Test
    fun suggestionsReadTheTagMapPreferMasterTagsAndDropRepeats() {
        assertEquals(listOf("female:big breasts", "parody:blue archive"), EhParser.tagSuggestions(fixture("tagsuggest.json")).map { it.raw })
        assertTrue(EhParser.tagSuggestions("""{"tags":[]}""").isEmpty())
        assertTrue(EhParser.tagSuggestions("nonsense").isEmpty())
    }

    @Test
    fun aBanReadsInTheSitesOwnWordingWhichXtasCheckMissed() {
        assertTrue(EhParser.isBanned(fixture("banned.html")), "Your IP address has been temporarily banned")
        assertTrue(EhParser.isBanned("Your IP has been temporarily banned"), "XTA's wording")
        assertTrue(EhParser.isBanned("This IP address has been banned"), "XTA's other wording")
        assertFalse(EhParser.isBanned(fixture("list_compact.html")))
    }

    @Test
    fun theContentWarningIsKnownForWhatItIs() {
        assertTrue(EhParser.isContentWarning(fixture("content_warning.html")))
        assertFalse(EhParser.isContentWarning(fixture("detail.html")))
        assertNull(EhParser.galleryDetail(fixture("content_warning.html"), 9, "tok"))
    }
}
