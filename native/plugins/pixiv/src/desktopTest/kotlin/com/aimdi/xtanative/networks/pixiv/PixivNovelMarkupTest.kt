package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.pixiv.PixivNovelBlock.Heading
import com.aimdi.xtanative.networks.pixiv.PixivNovelBlock.Illust
import com.aimdi.xtanative.networks.pixiv.PixivNovelBlock.PageBreak
import com.aimdi.xtanative.networks.pixiv.PixivNovelBlock.Paragraph
import com.aimdi.xtanative.networks.pixiv.PixivNovelBlock.Upload
import com.aimdi.xtanative.networks.pixiv.PixivNovelSpan.Link
import com.aimdi.xtanative.networks.pixiv.PixivNovelSpan.PageJump
import com.aimdi.xtanative.networks.pixiv.PixivNovelSpan.Plain
import com.aimdi.xtanative.networks.pixiv.PixivNovelSpan.Ruby
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PixivNovelMarkupTest {
    private fun parse(text: String) = PixivNovelMarkup.parse(text)

    private fun plain(text: String) = Paragraph(listOf(Plain(text)))

    @Test
    fun everyLineIsAParagraphAndBlankLinesAreKept() {
        assertEquals(listOf(plain("First"), Paragraph(emptyList()), plain("Second")), parse("First\r\n\r\nSecond"))
    }

    @Test
    fun pageBreaksAreNumberedFromPageTwo() {
        val blocks = parse("One\n[newpage]\nTwo\n[newpage]\nThree")
        assertEquals(listOf(plain("One"), PageBreak(2), plain("Two"), PageBreak(3), plain("Three")), blocks)
        assertEquals(mapOf(1 to 0, 2 to 1, 3 to 3), PixivNovelMarkup.pageStarts(blocks))
    }

    @Test
    fun aChapterIsAHeadingRubyInsideItIncluded() {
        assertEquals(
            listOf(Heading(listOf(Plain("第一章 "), Ruby("旅立", "たびだ"), Plain("ち")))),
            parse("[chapter:第一章 [[rb:旅立 > たびだ]]ち]"),
        )
    }

    @Test
    fun rubyTakesEitherArrowAndKeepsTheTextAroundIt() {
        assertEquals(
            listOf(Paragraph(listOf(Plain("彼は"), Ruby("漢字", "かんじ"), Plain("と"), Ruby("未来", "あした"), Plain("を書いた")))),
            parse("彼は[[rb:漢字 > かんじ]]と[[rb:未来＞あした]]を書いた"),
        )
    }

    @Test
    fun jumpuriLinksPixivAndOutsideAddressesAlikeTheLabelKept() {
        assertEquals(
            listOf(
                Paragraph(
                    listOf(
                        Plain("See "),
                        Link("my page", "https://www.pixiv.net/users/42"),
                        Plain(" or "),
                        Link("elsewhere", "https://example.com/a?b=1"),
                    ),
                ),
            ),
            parse("See [[jumpuri:my page > https://www.pixiv.net/users/42]] or [[jumpuri:elsewhere＞https://example.com/a?b=1]]"),
        )
    }

    @Test
    fun aLinkWithoutALabelShowsItsAddressAndOneThatIsNoWebAddressStaysText() {
        assertEquals(listOf(Paragraph(listOf(Link("https://example.com", "https://example.com")))), parse("[[jumpuri: > https://example.com]]"))
        assertEquals(listOf(plain("[[jumpuri:bad > javascript:alert(1)]]")), parse("[[jumpuri:bad > javascript:alert(1)]]"))
    }

    @Test
    fun jumpIsAPageJump() {
        assertEquals(listOf(Paragraph(listOf(Plain("Back to "), PageJump(1), Plain(".")))), parse("Back to [jump:1]."))
        assertEquals("[jump:4]", PageJump(4).source)
    }

    @Test
    fun picturesAreBlocksOfTheirOwnSplittingALine() {
        assertEquals(
            listOf(plain("Look "), Illust(12345, 3, "12345-3"), plain(" here"), Illust(777, 1, "777"), Upload("9001")),
            parse("Look [pixivimage:12345-3] here\n[pixivimage:777]\n[uploadedimage:9001]"),
        )
    }

    @Test
    fun spaceBesideABlockTagIsNotALineOfItsOwn() {
        assertEquals(listOf(PageBreak(2)), parse("  [newpage]  "))
    }

    @Test
    fun unknownTagsAndMalformedMarkupStayTextWithoutThrowing() {
        for (text in listOf(
            "[unknown:thing]", "[chapter:never closed", "[[rb:no arrow]]", "[[rb: > only ruby]]", "[[rb:base > ]]", "[jump:zero]",
            "[jump:0]", "[pixivimage:abc]", "[pixivimage:12-x]", "[uploadedimage:]", "[[jumpuri:half", "[[[[]]]]][[", "]]][[[",
        )) {
            assertEquals(listOf(plain(text)), parse(text), text)
        }
    }

    @Test
    fun anEmptyTextIsOneBlankLine() {
        assertEquals(listOf(Paragraph(emptyList())), parse(""))
    }

    @Test
    fun thePlainTextDropsTheMarkup() {
        val blocks = parse("[chapter:Start]\n[[rb:漢字>かんじ]] and [[jumpuri:a site>https://example.com]] [jump:2]\n[pixivimage:5]\n[newpage]\nEnd")
        assertEquals("Start\n漢字(かんじ) and a site (https://example.com) \n\nEnd", PixivNovelMarkup.plainText(blocks))
    }

    @Test
    fun theNovelObjectIsCutAtItsOwnClosingBraceWhateverTheTextHolds() {
        val novel = """{"id":"12","text":"He said \"}{\" and left. \\ A {brace} and a \\\"quote\\\"","seriesNavigation":{"nextNovel":{"id":13,"viewable":true}}}"""
        val html = """<html><head><script> Object.defineProperty(window, "pixiv", {value: {novel: $novel, isOwnWork: false}}); var other = {"a": 1};</script></head></html>"""

        assertEquals(novel, PixivNovelPage.objectSources(html).first())
        assertEquals(J.parse(novel).raw, PixivNovelPage.json(html))
    }

    @Test
    fun aNovelLabelThatOpensNoObjectIsPassedOver() {
        val html = """<script>var label = "novel: chapter"; window.pixiv = {novel: {"id": "5", "text": "x"}};</script>"""
        assertEquals("5", J(PixivNovelPage.json(html))["id"].string)
    }

    @Test
    fun aPageWithoutAnObjectAnUnclosedOneOrBrokenJsonHasNone() {
        assertNull(PixivNovelPage.json("<html>no novel here</html>"))
        assertNull(PixivNovelPage.json("""novel: {"id": 1, "text": "never closed"""))
        assertNull(PixivNovelPage.json("novel: {id: 1}"), "strict JSON, as the page writes it")
        assertNull(PixivNovelPage.json(""))
    }

    @Test
    fun aNovelsPageCarriesItsTextTheChaptersBesideItAndItsPictures() {
        val content = PixivNovelContent.fromHtml(Fixtures.text("novel_webview.html"))!!
        val blocks = PixivNovelMarkup.parse(content.text)

        assertEquals(41L to "Letters", content.id to content.title)
        assertEquals(plain("He said \"}{\" and left. \\ A {brace}"), blocks.first())
        assertEquals(PixivNovelNeighbour(40, viewable = true, title = "Before", order = 1), content.previous)
        assertEquals(PixivNovelNeighbour(42, viewable = false, title = "", order = 3), content.next)
        assertEquals("https://i.pximg.net/m/5_p1.jpg", content.illust(blocks.filterIsInstance<Illust>().single())?.url)
        val upload = content.upload(blocks.filterIsInstance<Upload>().single())!!
        assertEquals("https://i.pximg.net/novel/9_1200.jpg" to "https://i.pximg.net/novel/9.png", upload.url to upload.saveUrl)
        assertEquals(setOf("9"), content.uploads.keys, "pictures without an address are left out")
        assertEquals(setOf("5-2"), content.illusts.keys)
    }

    @Test
    fun aFirstPageMayBeFiledUnderTheBareWorkId() {
        val content = PixivNovelContent.parse(J.parse("""{"text": "", "illusts": {"5": {"illust": {"images": {"original": "https://i.pximg.net/o/5.png"}}}}}"""))!!
        assertEquals("https://i.pximg.net/o/5.png", content.illust(Illust(5, 1, "5-1"))?.url)
        assertNull(content.illust(Illust(5, 2, "5-2")))
    }

    @Test
    fun missingOrReshapedPartsAreEmptyAndNoTextIsNoContent() {
        val content = PixivNovelContent.parse(J.parse("""{"text": "Only text", "seriesNavigation": "none", "images": ["not", "a", "map"], "illusts": null}"""))!!
        assertEquals(Triple(0L, null, null), Triple(content.id, content.previous, content.next))
        assertEquals(emptyMap(), content.uploads)
        assertEquals(emptyMap(), content.illusts)
        assertNull(PixivNovelContent.parse(J.parse("""{"title": "No text"}""")))
        assertNull(PixivNovelContent.parse(J(null)))
        assertNull(PixivNovelContent.parse(J.parse("[1, 2]")))
    }
}
