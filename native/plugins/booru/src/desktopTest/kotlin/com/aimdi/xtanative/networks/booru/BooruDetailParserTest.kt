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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a post's page loads on demand, read the way XTA's booru_detail_parse.dart reads it. */
class BooruDetailParserTest {
    private fun info(reply: BooruTagReply, engine: BooruEngine) =
        BooruDetailParser.tagInfo(reply, engine).mapValues { (_, info) -> info.category to info.postCount }

    @Test
    fun moebooruNamesItsTagsKindsInAMapWithoutCounts() {
        assertEquals(
            mapOf("kantoku" to (ARTIST to null), "clouds" to (GENERAL to null), "jpeg_artifacts" to (META to null), "some_circle" to (ARTIST to null)),
            info(BooruTagReply.Json(Fixtures.json("moebooru_post_tags.json")), MOEBOORU),
            "an unknown kind is left out",
        )
    }

    @Test
    fun theOtherEnginesGiveTagRowsWithKindsAndCounts() {
        assertEquals(
            mapOf("kantoku" to (ARTIST to 50), "highres" to (META to 9_000_000), "girls'_frontline" to (COPYRIGHT to 1200)),
            info(BooruTagReply.Json(Fixtures.json("gelbooru_tags.json")), GELBOORU_V2),
        )
        assertEquals(GENERAL to 2_524_807, info(BooruTagReply.Json(Fixtures.json("danbooru_tags.json")), DANBOORU)["blue_eyes"])
        assertEquals(mapOf("fox" to (SPECIES to 12)), info(BooruTagReply.Json(Fixtures.json("e621_tags.json")), E621))
        assertEquals(CHARACTER to 900, info(BooruTagReply.Xml(Fixtures.text("safebooru_tags.xml")), GELBOORU_V2)["girls'_frontline"])
        assertTrue(info(BooruTagReply.Json(J.parse("""{"tags":[]}""")), E621).isEmpty())
    }

    @Test
    fun aWikiPageIsPickedByItsExactTitle() {
        val body = BooruDetailParser.wiki(Fixtures.json("danbooru_wiki_pages.json"), "blue_eyes")
        assertEquals("Blue eyes\nCharacters with blue eyes. See blue_eyes rating:g.", body?.let(BooruText::plain))
        assertEquals("yes", BooruDetailParser.wiki(Fixtures.json("moebooru_wiki.json"), "blue_sky"), "titles holding the tag come first")
        assertEquals("text", BooruDetailParser.wiki(J.parse("""{"title":"a","body":" text "}"""), "a"))
        assertNull(BooruDetailParser.wiki(J.parse("""{"body":"x","is_deleted":true}"""), "a"))
        assertNull(BooruDetailParser.wiki(J.parse("[]"), "x"))
    }

    @Test
    fun commentsAreOldestFirstWithHiddenAndEmptyOnesLeftOut() {
        val danbooru = BooruDetailParser.comments(Fixtures.json("danbooru_comments.json"))
        assertEquals(listOf("first [b]bold[/b]", "second"), danbooru.map { it.body })
        assertEquals(listOf("a", "b"), danbooru.map { it.author })
        assertEquals(3, danbooru.first().score)
        assertEquals(Instant.parse("2024-01-01T05:00:00Z"), danbooru.first().createdAt)
        assertEquals("first bold", BooruText.plain(danbooru.first().body))
    }

    @Test
    fun e621AndMoebooruCommentsNameTheirAuthorsTheirOwnWay() {
        val e621 = BooruDetailParser.comments(Fixtures.json("e621_comments.json"))
        assertEquals(listOf("fan" to "Nice fox"), e621.map { it.author to it.body }, "a hidden comment is left out")
        val moebooru = BooruDetailParser.comments(Fixtures.json("moebooru_comments.json"))
        assertEquals(listOf("b" to "earlier", "c" to "moe"), moebooru.map { it.author to it.body }, "undated: by id")
        assertTrue(BooruDetailParser.comments(J.parse("""{"comments":[]}""")).isEmpty())
    }

    @Test
    fun relatedTagsReadInTheHostsOrder() {
        assertEquals(listOf("cat_ears", "animal_ears"), BooruDetailParser.relatedTags(Fixtures.json("danbooru_related_tag.json"), DANBOORU))
        assertEquals(listOf("fox", "canine", "mammal"), BooruDetailParser.relatedTags(Fixtures.json("e621_tags.json"), E621))
        assertTrue(BooruDetailParser.relatedTags(J.parse("""[{"name":"wolf","related_tags":"[]"}]"""), E621).isEmpty())
        assertTrue(BooruDetailParser.relatedTags(J.parse("not json"), DANBOORU).isEmpty())
        assertTrue(BooruDetailParser.relatedTags(Fixtures.json("danbooru_related_tag.json"), GELBOORU_V2).isEmpty())
    }

    @Test
    fun theArtistTheTagNamesIsPickedWithoutDeletedEntriesOrDeadLinks() {
        val artist = BooruDetailParser.artist(Fixtures.json("danbooru_artists.json"), "Kantoku")!!
        assertEquals("kantoku", artist.name)
        assertEquals(listOf("カントク", "kantoku_art"), artist.otherNames)
        assertEquals(listOf("https://www.pixiv.net/users/1", "https://x.com/kantoku"), artist.urls)
        assertNull(BooruDetailParser.artist(J.parse("""[{"name":"kantoku","is_deleted":true}]"""), "kantoku"))
        assertNull(BooruDetailParser.artist(J.parse("not json"), "kantoku"))
    }

    @Test
    fun poolsAndNotesLeaveOutWhatIsDeletedInactiveOrIncomplete() {
        assertEquals(listOf(BooruPool("7", "my_series", 12)), BooruDetailParser.pools(Fixtures.json("danbooru_pools.json")))
        assertTrue(BooruDetailParser.pools(J.parse("not json")).isEmpty())
        assertEquals(listOf(BooruNote(10, 20, 30, 40, "Hello & bye")), BooruDetailParser.notes(Fixtures.json("danbooru_notes.json")))
    }

    @Test
    fun dtextAndHtmlReadAsPlainText() {
        assertEquals(
            "Summary\nSee blue eyes and red_eyes, solo rating:g. Site bold x\n\nEnd & <ok>\nline",
            BooruText.plain(
                "h4. Summary\r\nSee [[blue_eyes|blue eyes]] and [[red_eyes]], {{solo rating:g}}. " +
                    "\"Site\":https://x.org [b]bold[/b] [expand=More]x[/expand]\n\n\n\nEnd &amp; &lt;ok&gt;<br>line",
            ),
        )
        assertEquals("&lt;", BooruText.plain("&amp;lt;"), "an escaped entity stays text")
    }
}
