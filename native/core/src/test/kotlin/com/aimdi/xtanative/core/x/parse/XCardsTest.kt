package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.core.x.model.XLiveLinks
import com.aimdi.xtanative.core.x.model.XPost
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a post carries below its text: XTA's poll_test.dart, unified_card_test.dart
 * and broadcasts_test.dart, on the recordings they use.
 */
class XCardsTest {
    private fun fixture(name: String) = J.parse(javaClass.getResource("/x/$name")!!.readText())

    /** A card as X sends one: its binding values a list of `{key, value}`. */
    private fun card(name: String, values: Map<String, String>) = J.parse(
        """{"name":"$name","binding_values":[${values.entries.joinToString(",") { (key, value) -> """{"key":"$key","value":{"string_value":"$value"}}""" }}]}""",
    )

    private fun poll(name: String, values: Map<String, String>) = XCards.card(card(name, values), emptyList()) as XCard.Poll?

    // ---- polls ----

    @Test
    fun aPollsSharesAddUpAndItsLeaderHasTheMostVotes() {
        val poll = poll("poll2choice_text_only", mapOf("choice1_label" to "Yes", "choice1_count" to "30", "choice2_label" to "No", "choice2_count" to "10", "end_datetime_utc" to "2026-01-01T12:00:00Z"))!!
        assertEquals(40L, poll.total)
        assertEquals(listOf("Yes", "No"), poll.choices.map { it.label })
        assertEquals(0.75, poll.share(poll.choices.first()), 0.001)
        assertEquals(30L, poll.leadingVotes)
        assertTrue(poll.leads(poll.choices.first()))
        assertEquals(Instant.parse("2026-01-01T12:00:00Z"), poll.endsAt)
    }

    @Test
    fun aPollNobodyVotedInLeadsNothingAndDividesByNothing() {
        val poll = poll("poll2choice_image", mapOf("choice1_label" to "Yes", "choice1_count" to "0", "choice2_label" to "No", "choice2_count" to "0"))!!
        assertEquals(0L, poll.total)
        assertTrue(poll.choices.all { poll.share(it) == 0.0 && !poll.leads(it) })
    }

    @Test
    fun fourChoicesAreAllRead() {
        val poll = poll("poll4choice_text_only", (1..4).flatMap { listOf("choice${it}_label" to "Option $it", "choice${it}_count" to "$it") }.toMap())!!
        assertEquals(4, poll.choices.size)
        assertEquals(10L, poll.total)
    }

    @Test
    fun aPollThatDoesNotFitGivesUpRatherThanFailingTheTimeline() {
        assertNull(poll("poll2choice_text_only", mapOf("choice1_label" to "Yes", "choice1_count" to "3")), "a missing choice")
        assertNull(XCards.card(J.parse("""{"name":"poll2choice_text_only","binding_values":"nope"}"""), emptyList()))
        val counts = poll("poll2choice_text_only", mapOf("choice1_label" to "Yes", "choice1_count" to "lots", "choice2_label" to "No", "choice2_count" to "4", "end_datetime_utc" to "soon"))!!
        assertEquals(0L, counts.choices.first().votes, "an unreadable count is no votes")
        assertEquals(4L, counts.total)
        assertNull(counts.endsAt)
    }

    @Test
    fun aPollWithImagesReadsHowManyChoicesItHasAndEachPictureAtBothSizes() {
        val post = XParser.post(fixture("tweet_result_image_poll.json"))!!
        val poll = post.card as XCard.Poll
        val values = XCards.bindings(fixture("tweet_result_image_poll.json")["card"]["legacy"])
        fun url(choice: Int, size: String) = values.getValue("choice${choice}_image$size")["image_value"]["url"].string
        assertEquals(listOf("Option A", "Option B", "Option C"), poll.choices.map { it.label })
        assertEquals((1..3).map { url(it, "") }, poll.choices.map { it.imageUrl }, "the small size, which is all a bar shows")
        assertEquals((1..3).map { url(it, "_original") }, poll.choices.map { it.fullImageUrl })
        assertEquals(listOf(false, false, true), poll.choices.map(poll::leads))
    }

    @Test
    fun aPollWithImagesWithoutAUsableCountIsNoPoll() {
        val labels = mapOf("choice1_label" to "A", "choice2_label" to "B")
        listOf(null, "0", "three").forEach { count ->
            assertNull(poll("1906814671912599552:poll_choice_images", labels + listOfNotNull(count?.let { "choice_count" to it })), "$count")
        }
        val onlySmall = poll("1906814671912599552:poll_choice_images", labels + ("choice_count" to "2"))!!
        assertTrue(onlySmall.choices.all { it.imageUrl == null && it.fullImageUrl == null }, "a choice without an image keeps its place")
    }

    // ---- website cards X builds itself ----

    @Test
    fun aCarouselReadsItsPageItsTitleAndEveryPictureInOrder() {
        val link = XCards.card(fixture("card_image_carousel_website.json"), emptyList()) as XCard.Link
        assertTrue(link.url.startsWith("https://www.uefa.com/news-media/news/"))
        assertTrue(link.title.contains("Tap to read"))
        assertEquals("uefa.com", link.domain)
        assertEquals(listOf("https://pbs.twimg.com/media/HOfIvHmXAAAHq_8.jpg", "https://pbs.twimg.com/media/HOfIpfhXIAAgE5C.jpg"), link.media.map { it.url })
        assertEquals(1080, link.media.first().width)
    }

    @Test
    fun aCarouselWithoutItsPageTitleOrPicturesIsNoCard() {
        val unified = XCards.unified(XCards.bindings(fixture("card_image_carousel_website.json")))
        fun without(vararg path: String): J {
            val raw = unified.raw.toString()
            val broken = when (path.first()) {
                "destination_objects" -> raw.replace("\"destination_objects\"", "\"gone\"")
                "media_entities" -> raw.replace("\"media_entities\"", "\"gone\"")
                else -> raw.replace("\"title\"", "\"gone\"")
            }
            return J.parse("""{"name":"unified_card","binding_values":[{"key":"unified_card","value":{"string_value":${kotlinx.serialization.json.JsonPrimitive(broken)}}}]}""")
        }
        assertNull(XCards.card(without("destination_objects"), emptyList()))
        assertNull(XCards.card(without("media_entities"), emptyList()))
        assertNull(XCards.card(without("title"), emptyList()))
        assertNull(XCards.card(J.parse("""{"name":"unified_card","binding_values":[{"key":"unified_card","value":{"string_value":"{not json"}}]}"""), emptyList()))
    }

    @Test
    fun aGrokShareReadsTheQuestionTheAnswerWithoutMarkupAndGrok() {
        val share = XCards.grokShare(fixture("card_grok_share.json"))!!
        assertEquals("https://x.com/i/grok/share/96b0a07447744eb79d03cbcaaf76e19f", share.url)
        assertEquals("Is this real?", share.question)
        assertTrue(share.answer.startsWith("Yes, the clip is real footage"))
        assertTrue("grok:render" !in share.answer)
        assertEquals("grok", share.grokScreenName)
        assertTrue(share.grokImageUrl!!.startsWith("https://pbs.twimg.com/profile_images/"))
        assertNull(XCards.card(fixture("card_grok_share.json"), emptyList()), "it is no link preview")
        assertNull(XCards.grokShare(fixture("card_image_carousel_website.json")))
    }

    @Test
    fun aGrokShareWithoutAQuestionOrAPageIsNone() {
        val raw = fixture("card_grok_share.json")
        fun edited(change: (String) -> String): J {
            val unified = XCards.unified(XCards.bindings(raw)).raw.toString()
            return J.parse("""{"name":"unified_card","binding_values":[{"key":"unified_card","value":{"string_value":${kotlinx.serialization.json.JsonPrimitive(change(unified))}}}]}""")
        }
        assertNull(XCards.grokShare(edited { it.replace("\"USER\"", "\"NOBODY\"") }))
        assertNull(XCards.grokShare(edited { it.replace("\"destination_objects\"", "\"gone\"") }))
        val unanswered = XCards.grokShare(edited { it.replace("\"AGENT\"", "\"NOBODY\"") })!!
        assertEquals("Is this real?" to "", unanswered.question to unanswered.answer, "the question stays while there is no answer yet")
        val nameless = XCards.grokShare(edited { it.replace("\"grok_user\"", "\"gone\"") })!!
        assertEquals("grok" to null, nameless.grokScreenName to nameless.grokImageUrl)
    }

    @Test
    fun aPreviewKeepsXsNameForItsLayout() {
        val values = mapOf("title" to "T", "card_url" to "https://example.com/a")
        assertEquals(listOf("summary", "summary_large_image", "player"), listOf("summary", "summary_large_image", "player").map { (XCards.card(card(it, values), emptyList()) as XCard.Link).kind })
        val promo = XCards.card(card("promo_website", mapOf("website_url" to "https://example.com/buy", "title" to "Buy")), emptyList()) as XCard.Link
        assertNull(promo.kind, "a card X builds itself draws as X sends it")
    }

    @Test
    fun aPromotedWebsiteAndALiveEventAreLinkPreviews() {
        val promo = XCards.card(card("promo_website", mapOf("website_url" to "https://example.com/buy", "title" to "Buy", "vanity_url" to "example.com")), emptyList()) as XCard.Link
        assertEquals(Triple("https://example.com/buy", "Buy", "example.com"), Triple(promo.url, promo.title, promo.domain))
        val event = XCards.card(card("745291183405076480:live_event", mapOf("card_url" to "https://x.com/i/events/1", "event_title" to "Final", "event_subtitle" to "Live now")), emptyList()) as XCard.Link
        assertEquals(Triple("https://x.com/i/events/1", "Final", "Live now"), Triple(event.url, event.title, event.description))
    }

    @Test
    fun bindingValuesAreReadKeyedOrListed() {
        val keyed = J.parse("""{"binding_values":{"title":{"string_value":"T"}}}""")
        assertEquals("T", XCards.bindings(keyed).getValue("title")["string_value"].string)
        assertEquals("T", XCards.bindings(card("summary", mapOf("title" to "T"))).getValue("title")["string_value"].string)
    }

    // ---- live video and Spaces ----

    private fun post(urls: List<String> = emptyList(), card: String? = null, text: String = "live", media: String? = null): XPost {
        val entities = urls.joinToString(",") { """{"url":"https://t.co/b","expanded_url":"$it","display_url":"x.com/i/broadcasts/1…","indices":[0,5]}""" }
        return XParser.post(
            J.parse(
                """{"rest_id":"1","core":{"user_results":{"result":{"rest_id":"u1","core":{"screen_name":"someone","name":"S"}}}},
                   ${card?.let { "\"card\":{\"legacy\":$it}," }.orEmpty()}
                   "legacy":{"id_str":"1","full_text":"$text","entities":{"urls":[$entities]}${media?.let { ",\"extended_entities\":{\"media\":[$it]}" }.orEmpty()}}}""",
            ),
        )!!
    }

    @Test
    fun aBroadcastIsFoundByItsLinkItsCardOrItsText() {
        assertEquals("1abc", post(urls = listOf("https://x.com/i/broadcasts/1abc")).live?.broadcastId)
        assertTrue(post(card = """{"name":"745291183405076480:broadcast"}""").live != null, "the card's name is enough")
        val bound = post(card = """{"binding_values":[{"key":"broadcast_url","value":{"string_value":"https://x.com/i/broadcasts/1card"}},{"key":"broadcast_thumbnail","value":{"image_value":{"url":"https://pbs.example/thumb.jpg"}}}]}""")
        assertEquals("1card", bound.live?.broadcastId)
        assertEquals("https://x.com/i/broadcasts/1card", bound.live?.watchUrl)
        assertEquals("https://pbs.example/thumb.jpg", bound.live?.imageUrl)
        assertEquals("1txt", post(text = "live at https://x.com/i/broadcast/1txt").live?.broadcastId, "a link in the text its entities left out")
        val video = post(media = """{"type":"video","media_url_https":"https://pbs.example/1.jpg","expanded_url":"https://x.com/i/broadcasts/1vod","display_url":"x.com/i/broadcasts/1vod"}""")
        assertEquals("1vod", video.live?.broadcastId, "a video whose own link is a broadcast")
        assertNull(post().live, "an ordinary post is not live")
    }

    @Test
    fun aSpaceIsFoundByItsLinkOrItsCard() {
        val linked = post(urls = listOf("https://x.com/i/spaces/1room")).live!!
        assertTrue(linked.isSpace)
        assertNull(linked.broadcastId)
        assertEquals("https://x.com/i/spaces/1room", linked.watchUrl)
        val carded = post(
            card = """{"name":"326813241626781184:audiospace","binding_values":[{"key":"id","value":{"string_value":"1cardspace"}},
                {"key":"thumbnail_image","value":{"image_value":{"url":"https://pbs.example/space.jpg"}}}]}""",
        ).live!!
        assertEquals("1cardspace", carded.spaceId)
        assertEquals("https://pbs.example/space.jpg", carded.imageUrl)
    }

    @Test
    fun theCardsMediaKeyAndShapeAreKeptForThePlayer() {
        val live = post(
            card = """{"name":"745291183405076480:broadcast","binding_values":[{"key":"broadcast_media_key","value":{"string_value":"28_fromcard"}},
                {"key":"broadcast_url","value":{"string_value":"https://x.com/i/broadcasts/1card"}},
                {"key":"broadcast_width","value":{"string_value":"1"}},{"key":"broadcast_height","value":{"string_value":"1"}}]}""",
        ).live!!
        assertEquals("28_fromcard", live.mediaKey)
        assertEquals("1card", live.broadcastId)
        assertTrue(live.canResolve)
        assertEquals(16f / 9f, live.aspectRatio, "a square thumbnail around a landscape stream is drawn 16:9")
    }

    @Test
    fun aLiveLinkTellsASpaceFromABroadcast() {
        val space = XLive.fromUrl("https://x.com/i/spaces/1room")
        assertTrue(space.isSpace && space.canResolve)
        assertEquals("https://x.com/i/spaces/1room", space.watchUrl)
        val broadcast = XLive.fromUrl("https://x.com/i/broadcasts/1abc")
        assertEquals("1abc" to false, broadcast.broadcastId to broadcast.isSpace)
        assertTrue(!XLive.fromUrl("https://x.com/someone/status/1").canResolve)
        assertEquals("1pscp", XLiveLinks.broadcastId("https://www.pscp.tv/w/1pscp"))
        assertNull(XLiveLinks.broadcastId("x.com/i/broadcasts/1ab…"), "a display address X cut short is no link")
        assertEquals("1abc", XLiveLinks.broadcastId("x.com/i/broadcasts/1abc)."), "without the punctuation after it")
    }
}
