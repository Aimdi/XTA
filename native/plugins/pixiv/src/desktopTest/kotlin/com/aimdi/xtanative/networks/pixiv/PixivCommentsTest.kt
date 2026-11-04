package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.pixiv.PixivCommentPart.Emoji
import com.aimdi.xtanative.networks.pixiv.PixivCommentPart.Text
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PixivCommentsTest {
    private fun emoji(name: String) = Emoji(name, PixivCommentText.emojiIds.getValue(name))

    @Test
    fun aPageReadsItsCommentsTheTotalAndTheNextPage() {
        val page = PixivCommentPage.parse(Fixtures.json("comments.json"))
        assertEquals(listOf(501L, 502L, 503L), page.comments.items.map { it.id }, "a comment without an id is dropped")
        assertEquals(41L, page.total)
        assertEquals("https://app-api.pixiv.net/v3/illust/comments?illust_id=120&offset=30", page.comments.next)

        val (full, sticker, reply) = page.comments.items
        assertEquals("Lovely colours (heart)", full.text)
        assertEquals(Instant.parse("2026-07-01T03:30:00Z"), full.date)
        assertEquals(Triple(42L, "Mika", "https://i.pximg.net/user-profile/img/42.jpg"), full.user?.let { Triple(it.id, it.name, it.avatarUrl) })
        assertTrue(full.hasReplies)
        assertNull(full.stampUrl)
        assertNull(full.replyTo)
        assertEquals("" to "https://s.pximg.net/common/images/stamp/generated-stamps/301_s.jpg", sticker.text to sticker.stampUrl)
        assertEquals("@rin_art", sticker.authorName, "the account when the name is empty")
        assertNull(reply.user, "a withdrawn author")
        assertNull(reply.authorName)
        assertEquals("Rin", reply.replyTo?.name)
        assertTrue(reply.linksOutsidePixiv)
        assertFalse(full.linksOutsidePixiv)
    }

    @Test
    fun aReshapedCommentSurvivesAndOneWithoutAnIdIsDropped() {
        val odd = PixivComment.parse(J.parse("""{"id": "12", "comment": 5, "date": "yesterday", "user": "Mika", "parent_comment": [], "has_replies": "yes", "stamp": {"stamp_url": "  "}}"""))!!
        assertEquals(listOf<Any?>(12L, "", null, null, null, false, null), listOf(odd.id, odd.text, odd.date, odd.user, odd.replyTo, odd.hasReplies, odd.stampUrl))
        assertNull(PixivComment.parse(J.parse("""{"comment": "no id"}""")))
        assertNull(PixivComment.parse(J.parse("\"not a map\"")))
    }

    @Test
    fun anEmptyOrReshapedPageIsSimplyEmpty() {
        for (json in listOf("null", "\"error\"", "{}", """{"comments": "none", "next_url": 4}""")) {
            val page = PixivCommentPage.parse(J.parse(json))
            assertTrue(page.comments.items.isEmpty(), json)
            assertEquals(null to null, page.comments.next to page.total, json)
        }
    }

    @Test
    fun pixivLinksAndPlainTextPassTheSpamGuard() {
        for (text in listOf(
            "No links here (heart)",
            "See https://www.pixiv.net/artworks/120 and https://pixiv.me/mika",
            "Booth: https://mika.booth.pm/items/1 fanbox https://www.fanbox.cc/@mika",
            "Mirror https://i.pximg.net/img-original/1.png. Thanks",
            "Article: https://www.pixivision.net/en/a/1",
            "pixiv.netで見ました",
            "すごいwwww.最高",
            "草www.かわいい",
            "awww. so cute",
            "https://www.pixiv.net/artworks/1で見ました。ありがとう",
            "Found it on https://www.pixiv.net. Lovely",
            "https://pixiv.netで見つけた",
            "pixiv.net/artworks/1 is the source",
            "www.pixiv.net で見た",
            "and/or 60km/h Ver.2/3 1.5/2",
        )) {
            assertFalse(PixivCommentText.linksOutside(text), text)
        }
    }

    @Test
    fun aLinkAnywhereElseIsCaughtHoweverItIsWritten() {
        for (text in listOf(
            "Free coins https://bit.ly/abc",
            "visit www.example.com now",
            "見てwww.example.jp",
            "HTTP://EVIL.example/x",
            "https://pixiv.net.example.com/fake",
            "https://notpixiv.net/a",
            "https://例え.jp/x",
            "Nice! https://www.pixiv.net/artworks/1 and also http://spam.example",
            "Free https://www.pixiv.net@evil.example/x",
            "Free https://pixiv.net%2F@evil.example/x",
            "Free www.pixiv.net@evil.example",
            "https://pixiv.net。evil.example/x",
            "https://pixiv.net%2Eevil.example",
            "https://pixiv.net.みんな/x",
            "Free bit.ly/abc",
            "evil.example/x",
            "pixiv.net@evil.example/x",
        )) {
            assertTrue(PixivCommentText.linksOutside(text), text)
        }
    }

    @Test
    fun eachKindOfWorkAsksItsOwnEndpoints() {
        val illust = PixivCommentTarget.illust(120)
        val novel = PixivCommentTarget.novel(9)
        assertEquals(Triple("/v3/illust/comments", "illust_id", "/v2/illust/comment/replies"), illust.work.let { Triple(it.listPath, it.idField, it.repliesPath) })
        assertEquals(Triple("/v3/novel/comments", "novel_id", "/v2/novel/comment/replies"), novel.work.let { Triple(it.listPath, it.idField, it.repliesPath) })
        assertEquals(PixivCommentTarget.illust(120), illust)
        assertFalse(PixivCommentTarget.novel(120) == illust)
    }

    @Test
    fun emojiAreNumberedTheWayPixivServesTheirPictures() {
        assertEquals(38, PixivCommentText.emojiIds.size)
        assertEquals(
            mapOf("normal" to 101, "cry" to 108, "normal2" to 201, "panic2" to 209, "love3" to 310, "sleep4" to 408, "heart" to 501, "star" to 503),
            listOf("normal", "cry", "normal2", "panic2", "love3", "sleep4", "heart", "star").associateWith(PixivCommentText.emojiIds::getValue),
        )
        assertEquals("(heart)" to "https://s.pximg.net/common/images/emoji/501.png", emoji("heart").let { it.code to it.url })
    }

    @Test
    fun emojiCodesSplitTheTextAndAnythingElseStaysText() {
        assertEquals(listOf(Text("Great "), emoji("normal"), Text(" work "), emoji("heart")), PixivCommentText.parts("Great (normal) work (heart)"))
        assertEquals(listOf(Text("so (unknown) much (Heart)")), PixivCommentText.parts("so (unknown) much (Heart)"))
        assertEquals(listOf(emoji("heart"), emoji("heart"), emoji("star")), PixivCommentText.parts("(heart)(heart)(star)"))
        assertEquals(listOf(Text("Nice pose (the second one) ("), emoji("heart"), Text(")")), PixivCommentText.parts("Nice pose (the second one) ((heart))"))
        assertEquals(listOf(Text("a ( heart ) b (heart")), PixivCommentText.parts("a ( heart ) b (heart"))
        assertTrue(PixivCommentText.parts("").isEmpty())
        assertEquals(listOf(emoji("love3")), PixivComment(1, "(love3)").parts)
    }

    @Test
    fun anArtworksCommentsAreReadWithTheToken() = runBlocking {
        val http = FakePixivHttp { answer(Fixtures.text("comments.json")) }
        val page = PixivCommentsApi(pixiv(http)).comments(PixivCommentTarget.illust(120))
        val read = http.asked.single()
        assertEquals("GET /v3/illust/comments?illust_id=120", "${read.method} ${read.url.encodedPath}?${read.url.encodedQuery}")
        assertEquals("Bearer access-1", read.headers["Authorization"])
        assertEquals(3, page.comments.items.size)
    }

    @Test
    fun novelCommentsRepliesAndLaterPagesAskWherePixivSays() = runBlocking {
        val http = FakePixivHttp { answer("""{"comments": []}""") }
        val api = PixivCommentsApi(pixiv(http))
        api.comments(PixivCommentTarget.novel(55))
        api.replies(PixivCommentTarget.illust(120), 1)
        api.replies(PixivCommentTarget.novel(55), 8)
        api.comments(PixivCommentTarget.illust(120), next = "https://app-api.pixiv.net/v3/illust/comments?illust_id=120&offset=30")
        api.replies(PixivCommentTarget.illust(120), 1, next = "https://app-api.pixiv.net/v2/illust/comment/replies?comment_id=1&offset=30")

        assertEquals(
            listOf(
                "GET /v3/novel/comments?novel_id=55",
                "GET /v2/illust/comment/replies?comment_id=1",
                "GET /v2/novel/comment/replies?comment_id=8",
                "GET /v3/illust/comments?illust_id=120&offset=30",
                "GET /v2/illust/comment/replies?comment_id=1&offset=30",
            ),
            http.asked.map { "${it.method} ${it.url.encodedPath}?${it.url.encodedQuery}" },
        )
    }
}
