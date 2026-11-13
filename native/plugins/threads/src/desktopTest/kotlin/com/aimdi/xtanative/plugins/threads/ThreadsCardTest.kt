package com.aimdi.xtanative.plugins.threads

import androidx.compose.ui.graphics.Color
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsParser
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPoll
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsPostFacts
import com.aimdi.xtanative.networks.threads.ThreadsPostFactsReader
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment.Kind
import com.aimdi.xtanative.store.settings.MediaQuality
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The post card's reading of what a guest is shown beyond words and media
 * (the keys CI's shape probe saw: spoilers, the community of a topic, shares,
 * the AI label, pins, a check before sharing, inline media), a poll read
 * whichever shape it comes in and harmless when absent, and the card's rules.
 */
class ThreadsCardTest {
    private fun meta(info: String = "", extra: String = "", caption: String = "Hello world"): ThreadsPost? = ThreadsParser.post(
        J.parse(
            """{"pk":"1","code":"C1","taken_at":1761900000,"user":{"username":"zuck","full_name":"Mark"},
               "caption":{"text":"$caption"},"text_post_app_info":{$info}${if (extra.isEmpty()) "" else ",$extra"}}""",
        ),
    )

    @Test
    fun aSpoilerMetaMarkedIsKeptEvenWhenEveryPieceIsPlainText() {
        val post = meta(
            info = """"text_fragments":{"fragments":[{"fragment_type":"plaintext","plaintext":"Hello "},
                {"fragment_type":"plaintext","plaintext":"world","styling_info":{"is_spoiler":true}}]}""",
        )!!
        assertTrue(post.hasSpoiler)
        assertEquals(listOf(false, true), post.fragments.map { it.spoiler })
        assertFalse(meta()!!.hasSpoiler)
    }

    @Test
    fun theFactsAGuestIsShownAreReadAndNoneWhenAbsent() {
        val post = meta(
            info = """"tag_header":{"display_name":"Books","community_emoji":"📚","community_favicon_url":"https://x.cdn/f.png"},
                "reshare_count":7,"pinned_post_info":{"is_pinned_to_profile":true,"is_pinned_to_parent_post":false}""",
            extra = """"gen_ai_detection_method":{"detection_method":"SELF_DISCLOSURE_FLOW"},"sharing_friction_info":{"should_have_sharing_friction":true}""",
        )!!
        val facts = post.facts
        assertEquals("📚", facts.topicEmoji)
        assertEquals("https://x.cdn/f.png", facts.topicIconUrl)
        assertEquals(7L, facts.shareCount)
        assertTrue(facts.aiLabel)
        assertTrue(facts.pinnedToProfile)
        assertFalse(facts.pinnedToParent)
        assertTrue(facts.sharingFriction)
        assertEquals(ThreadsPostFacts.NONE, meta()!!.facts)
    }

    @Test
    fun noAiLabelIsShownForNoneOrAShapeNotKnown() {
        assertFalse(ThreadsPostFactsReader.aiLabel(J.parse("""{"detection_method":"NONE"}""")))
        assertFalse(ThreadsPostFactsReader.aiLabel(J.parse("""{"something":1}""")))
        assertFalse(ThreadsPostFactsReader.aiLabel(J.parse("null")))
        assertTrue(ThreadsPostFactsReader.aiLabel(J.parse("\"C2PA\"")))
        assertFalse(meta(extra = """"sharing_friction_info":{"should_have_sharing_friction":false,"bloks_app_url":null}""")!!.facts.sharingFriction)
    }

    @Test
    fun aPollIsReadFromAListOfOptionsOrFromLetteredOnesWithTheirShare() {
        val listed = meta(info = """"poll":{"options":[{"text":"Yes","vote_count":3},{"text":"No","vote_count":1}],"expiration_timestamp":1761990000}""")!!
        assertEquals(ThreadsPoll(listOf(ThreadsPoll.Option("Yes", 3), ThreadsPoll.Option("No", 1)), Instant.ofEpochSecond(1761990000)), listed.facts.poll)
        val lettered = meta(
            info = """"poll_attachment":{"option_a":"Tea","option_b":"Coffee","option_a_votes_percentage":25.0,"option_b_votes_percentage":75.0,
                "total_votes":200,"expiration_timestamp":"2025-11-01T10:00:00+0000"}""",
        )!!
        val poll = assertNotNull(lettered.facts.poll)
        assertEquals(listOf(50L, 150L), poll.options.map { it.votes })
        assertEquals(Instant.parse("2025-11-01T10:00:00Z"), poll.endsAt)
    }

    @Test
    fun aPollWhoseVotesCannotBeToldOrWithOneAnswerIsNotShown() {
        assertNull(meta(info = """"poll_attachment":{"option_a":"Tea","option_b":"Coffee","option_a_votes_percentage":0.5,"option_b_votes_percentage":0.5}""")!!.facts.poll)
        assertNull(meta(info = """"poll":{"options":[{"text":"Only","vote_count":3}]}""")!!.facts.poll)
        assertNull(meta(info = """"poll":"soon"""")!!.facts.poll)
        assertNull(meta()!!.facts.poll)
    }

    @Test
    fun inlineMediaStandsInForAPostWithoutItsOwn() {
        val post = meta(info = """"linked_inline_media":{"image_versions2":{"candidates":[{"url":"https://x.cdn/i.jpg","width":600,"height":400}]}}""")!!
        assertEquals(listOf("https://x.cdn/i.jpg"), post.media.map { it.url })
    }

    @Test
    fun aKeptPostKeepsItsFactsAndSpoilersAndOneWithoutKeepsNothingMore() {
        val poll = ThreadsPoll(listOf(ThreadsPoll.Option("Yes", 3), ThreadsPoll.Option("No", 1)), Instant.ofEpochSecond(1761990000))
        val post = ThreadsPost(
            id = "1", handle = "zuck", authorName = "Mark", text = "Hello world",
            fragments = listOf(ThreadsTextFragment(Kind.TEXT, "Hello "), ThreadsTextFragment(Kind.TEXT, "world", spoiler = true)),
            facts = ThreadsPostFacts(topicEmoji = "📚", shareCount = 7, aiLabel = true, pinnedToParent = true, sharingFriction = true, poll = poll),
        )
        assertEquals(post, ThreadsPostJson.list(ThreadsPostJson.listText(listOf(post))).single())
        val plain = post.copy(fragments = emptyList(), facts = ThreadsPostFacts.NONE)
        assertEquals("null", ThreadsPostJson.json(plain)["facts"].toString())
        assertEquals(plain, ThreadsPostJson.list(ThreadsPostJson.listText(listOf(plain))).single())
    }

    @Test
    fun aLongCaptionIsCutShortInAList() {
        assertFalse(ThreadsCardRules.isLong("short"))
        assertTrue(ThreadsCardRules.isLong("a".repeat(601)))
        assertTrue(ThreadsCardRules.isLong((1..13).joinToString("\n")))
    }

    @Test
    fun aCoveredSpoilerKeepsItsPlaceIsSpokenAsTheWordAndOpensNothingElse() {
        val runs = listOf(
            ThreadsTextFragment(Kind.MENTION, "@meta", "meta"),
            ThreadsTextFragment(Kind.TEXT, " says "),
            ThreadsTextFragment(Kind.TEXT, "the end", spoiler = true),
        )
        val text = ThreadsCardRules.covered(runs, Color.Gray, Color.Blue) {}
        assertEquals("@meta says the end", text.text)
        assertEquals(1, text.getLinkAnnotations(0, text.length).size)
        val post = ThreadsPost(id = "1", handle = "a", authorName = "A", text = "@meta says the end", fragments = runs)
        assertEquals("@meta says Spoiler", ThreadsCardRules.spoken(post, "Spoiler"))
    }

    @Test
    fun theCardIsOneDescriptionOfWhoWhenWhatAndMetasLabels() {
        val post = ThreadsPost(id = "1", handle = "zuck", authorName = "Mark", text = "Hi", topicTag = "Books")
        assertEquals("Alice reposted. Mark, @zuck, Nov 1. Books. Hi. AI info", ThreadsCardRules.summary(post, "Nov 1", "Alice reposted", "Spoiler", listOf("AI info")))
    }

    @Test
    fun aPollIsDrawnAsTheAppsPollCardEndedOnceItsTimeIsPast() {
        val poll = ThreadsPoll(listOf(ThreadsPoll.Option("Yes", 3), ThreadsPoll.Option("No", 1)), Instant.ofEpochSecond(100))
        val card = ThreadsCardRules.card(poll, Instant.ofEpochSecond(200))
        assertTrue(card.final)
        assertEquals(4L, card.total)
        assertFalse(ThreadsCardRules.card(poll, Instant.ofEpochSecond(50)).final)
    }

    @Test
    fun theSharedRowHoldsASpoilerBackAndSaysAPinnedPostIsPinned() {
        val post = ThreadsPost(
            id = "1", handle = "zuck", authorName = "Mark", text = "Hello world", publishedAt = Instant.ofEpochSecond(1),
            fragments = listOf(ThreadsTextFragment(Kind.TEXT, "Hello "), ThreadsTextFragment(Kind.TEXT, "world", spoiler = true)),
            facts = ThreadsPostFacts(pinnedToProfile = true),
        )
        val shown = ThreadsShared.post(post, MediaQuality.LARGE, "Spoiler")
        assertEquals("Spoiler", shown.warning)
        assertTrue(shown.coversText)
        assertTrue(shown.pinned)
        val plain = ThreadsShared.post(post.copy(fragments = emptyList()), MediaQuality.LARGE, "Spoiler")
        assertNull(plain.warning)
        assertFalse(plain.coversText)
    }

    @Test
    fun theSharedRowCoversMediaMetaCoveredWhereTheReaderHidesItAndNeverAPostWithoutMedia() {
        val media = listOf(ThreadsMedia("https://cdn/a.jpg", 1.0, null))
        val post = ThreadsPost(id = "1", handle = "zuck", authorName = "Mark", text = "Hi", publishedAt = Instant.ofEpochSecond(1), media = media, mediaCovered = true)
        val shown = ThreadsShared.post(post, MediaQuality.LARGE, "Spoiler", covered = "Covered")
        assertEquals("Covered", shown.warning)
        assertFalse(shown.coversText)
        assertEquals("Covered", shown.mediaWarning())
        assertNull(ThreadsShared.post(post, MediaQuality.LARGE, "Spoiler", covered = null).warning, "the reader shows sensitive media")
        assertNull(ThreadsShared.post(post.copy(media = emptyList()), MediaQuality.LARGE, "Spoiler", covered = "Covered").warning)
        assertEquals("Covered", ThreadsShared.post(post.copy(media = emptyList(), quoted = post), MediaQuality.LARGE, null, "Covered").quoted?.warning)
    }
}

