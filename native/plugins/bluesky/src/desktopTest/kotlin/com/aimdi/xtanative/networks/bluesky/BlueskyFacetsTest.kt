package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlueskyFacetsTest {
    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8).size

    @Test
    fun linkMentionAndTagFeaturesKeepTheirByteRangesInOrder() {
        val facets = BlueskyFacets.of(
            J.parse(
                """{"facets":[
                {"index":{"byteStart":30,"byteEnd":42},"features":[{"${'$'}type":"app.bsky.richtext.facet#link","uri":"https://bsky.app"}]},
                {"index":{"byteStart":0,"byteEnd":11},"features":[{"${'$'}type":"app.bsky.richtext.facet#mention","did":"did:plc:alice"}]},
                {"index":{"byteStart":20,"byteEnd":28},"features":[{"${'$'}type":"app.bsky.richtext.facet#tag","tag":"bluesky"}]}
                ]}""",
            ),
        )
        assertEquals(
            listOf(
                BlueskyFacet(0, 11, BlueskyFacet.Kind.MENTION, "did:plc:alice"),
                BlueskyFacet(20, 28, BlueskyFacet.Kind.TAG, "bluesky"),
                BlueskyFacet(30, 42, BlueskyFacet.Kind.LINK, "https://bsky.app"),
            ),
            facets,
        )
    }

    @Test
    fun aFacetIsItsFirstFeatureThatNamesSomethingAndBrokenOnesAreSkipped() {
        val facets = BlueskyFacets.of(
            J.parse(
                """{"facets":[
                {"index":{"byteStart":0,"byteEnd":4},"features":[{"${'$'}type":"app.bsky.richtext.facet#link","uri":"  "},{"${'$'}type":"app.bsky.richtext.facet#tag","tag":" art "}]},
                {"index":{"byteStart":5,"byteEnd":5},"features":[{"${'$'}type":"app.bsky.richtext.facet#tag","tag":"empty"}]},
                {"index":{"byteStart":9},"features":[{"${'$'}type":"app.bsky.richtext.facet#tag","tag":"open"}]},
                {"index":{"byteStart":"6","byteEnd":8.0},"features":[{"${'$'}type":"app.bsky.richtext.facet#unknown"},{"${'$'}type":"app.bsky.richtext.facet#mention","did":"did:plc:b"}]},
                "not a facet"
                ]}""",
            ),
        )
        assertEquals(listOf(BlueskyFacet(0, 4, BlueskyFacet.Kind.TAG, "art"), BlueskyFacet(6, 8, BlueskyFacet.Kind.MENTION, "did:plc:b")), facets)
        assertTrue(BlueskyFacets.of(J.parse("""{"facets":"nope"}""")).isEmpty())
    }

    @Test
    fun asciiTextSplitsAroundALink() {
        val runs = BlueskyFacets.runs("see https://bsky.app now", listOf(BlueskyFacet(4, 20, BlueskyFacet.Kind.LINK, "https://bsky.app")))
        assertEquals(listOf("see ", "https://bsky.app", " now"), runs.map { it.text })
        assertEquals(listOf(null, BlueskyFacet.Kind.LINK, null), runs.map { it.facet?.kind })
    }

    @Test
    fun anEmojiBeforeAFacetStillMapsItsByteOffsets() {
        val runs = BlueskyFacets.runs("👋 hi", listOf(BlueskyFacet(5, 7, BlueskyFacet.Kind.TAG, "hi")))
        assertEquals(listOf(BlueskyTextRun("👋 "), BlueskyTextRun("hi", BlueskyFacet(5, 7, BlueskyFacet.Kind.TAG, "hi"))), runs)
    }

    @Test
    fun leadingWhitespaceAndUnicodeKeepTheExactMentionSpan() {
        val prefix = "  👋 Grüße "
        val linked = "@alice.test"
        val body = "$prefix$linked\n  "
        val runs = BlueskyFacets.runs(body, listOf(BlueskyFacet(bytes(prefix), bytes(prefix + linked), BlueskyFacet.Kind.MENTION, "did:plc:abc")))
        assertEquals(body, runs.joinToString("") { it.text })
        assertEquals(linked, runs.single { it.facet != null }.text)
    }

    @Test
    fun overlappingEmptyAndOutOfRangeFacetsLeaveTheTextWhole() {
        val text = "one two three"
        val runs = BlueskyFacets.runs(
            text,
            listOf(
                BlueskyFacet(0, 3, BlueskyFacet.Kind.TAG, "one"),
                BlueskyFacet(2, 7, BlueskyFacet.Kind.TAG, "overlap"),
                BlueskyFacet(8, 8, BlueskyFacet.Kind.TAG, "empty"),
                BlueskyFacet(8, 400, BlueskyFacet.Kind.TAG, "past the end"),
            ),
        )
        assertEquals(text, runs.joinToString("") { it.text })
        assertEquals(listOf("one", "three"), runs.filter { it.facet != null }.map { it.text })
        assertEquals(listOf(BlueskyTextRun(text)), BlueskyFacets.runs(text, emptyList()))
        assertTrue(BlueskyFacets.runs("", listOf(BlueskyFacet(0, 1, BlueskyFacet.Kind.TAG, "x"))).isEmpty())
    }

    @Test
    fun aByteOffsetInsideACharacterLandsOnThatCharacter() {
        val indices = BlueskyFacets.utf16Indices("aé😀")
        assertEquals(listOf(0, 1, 1, 2, 2, 2, 2, 4), indices.toList(), "1 + 2 + 4 bytes, and one past the end")
    }

    @Test
    fun bioLinksKeepUtf8OffsetsAndBalancedPunctuation() {
        val bio = "🌌 See (https://example.org/a_(b)). Then https://other.example/path!"
        val links = BlueskyFacets.profileLinks(bio)
        assertEquals(listOf("https://example.org/a_(b)", "https://other.example/path"), links.map { it.value })
        val encoded = bio.toByteArray(Charsets.UTF_8)
        links.forEach { link -> assertEquals(link.value, String(encoded.copyOfRange(link.byteStart, link.byteEnd), Charsets.UTF_8)) }
        assertTrue(links.all { it.kind == BlueskyFacet.Kind.LINK })
    }

    @Test
    fun whatIsNoLinkIsNotOne() {
        assertTrue(BlueskyFacets.profileLinks("javascript:alert(1) ftp://example.org https:// https://user:pass@host").isEmpty())
        assertTrue(BlueskyFacets.profileLinks("xhttps://glued.example").isEmpty(), "a link starts at a word boundary")
        assertEquals(listOf("https://nbsp.example"), BlueskyFacets.profileLinks("go https://nbsp.example now").map { it.value }, "a no-break space ends a link")
    }
}
