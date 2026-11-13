package com.aimdi.xtanative.networks.deepmarks

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Keys and bookmark events, as XTA's deepmarks_nostr_test checks them, and
 * against events XTA's own Dart code signed (deepmarks/signed_bookmarks.json:
 * nostr_event.dart run with a fixed time and fixed randomness).
 */
class NostrTest {
    // The key pair from the NIP-19 specification, so decoding and derivation are checked against something outside XTA.
    private val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"
    private val secretHex = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"
    private val publicHex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
    private val july25 = Instant.parse("2026-07-25T00:00:00Z")

    private fun verifies(event: NostrEvent) = Schnorr.verify(unhex(event.pubkey), unhex(event.id), unhex(event.sig))

    @Test
    fun theNip19NsecVectorDecodes() {
        assertEquals(secretHex, Nostr.normaliseSecretKey(nsec))
        assertEquals(secretHex, Nostr.normaliseSecretKey(nsec.uppercase()))
    }

    @Test
    fun bareHexIsAcceptedInEitherCaseWithSurroundingSpace() {
        assertEquals(secretHex, Nostr.normaliseSecretKey("  ${secretHex.uppercase()}  "))
    }

    @Test
    fun whatIsNotAKeyIsRefusedWithAReason() {
        val notKeys = listOf(
            "",
            "hunter2",
            "npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z",
            "nsec1nonsense",
            nsec.dropLast(1) + "6",
            secretHex.drop(1),
            "g" + secretHex.drop(1),
        )
        for (input in notKeys) {
            val error = assertFailsWith<NostrKeyException>(input) { Nostr.normaliseSecretKey(input) }
            assertTrue(!error.message.isNullOrBlank(), input)
        }
    }

    @Test
    fun theNip19VectorsPublicKeyIsDerived() {
        assertEquals(publicHex, Nostr.publicKey(secretHex))
    }

    @Test
    fun aKeyOutsideTheCurvesRangeIsABadKeyWhereXtasSigningThrewAnError() {
        val zero = "0".repeat(64)
        val order = "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141"
        for (key in listOf(zero, order)) {
            assertEquals(key, Nostr.normaliseSecretKey(key), "it reads as a key")
            assertFailsWith<NostrKeyException> { Nostr.publicKey(key) }
            assertFailsWith<NostrKeyException> { Nostr.signWebBookmark(key, "https://example.com/a") }
        }
    }

    @Test
    fun theEventIdIsTheSha256OfTheCanonicalNip01Serialisation() {
        val tags = listOf(listOf("d", "https://example.com/article"), listOf("title", "An Article"))
        val canonical = """[0,"$publicHex",1700000000,39701,[["d","https://example.com/article"],["title","An Article"]],""]"""
        assertEquals(hex(sha256(canonical.toByteArray())), Nostr.eventId(publicHex, 1_700_000_000, Nostr.KIND_WEB_BOOKMARK, tags, ""))
    }

    @Test
    fun theUrlGoesInTheDTagAsNipB0Requires() {
        assertEquals(listOf("d", "https://example.com/a"), Nostr.webBookmarkTags("https://example.com/a").first())
    }

    @Test
    fun titleDescriptionAndLowerCasedTopicsAreAdded() {
        val tags = Nostr.webBookmarkTags("https://example.com/a", "  An Article  ", "A summary", listOf("Reading", "  ", "Bitcoin"))
        val expected = listOf(
            listOf("d", "https://example.com/a"),
            listOf("title", "An Article"),
            listOf("description", "A summary"),
            listOf("t", "reading"),
            listOf("t", "bitcoin"),
        )
        assertEquals(expected, tags)
    }

    @Test
    fun emptyOptionalTagsAreLeftOutRatherThanSentBlank() {
        assertEquals(listOf(listOf("d", "https://example.com/a")), Nostr.webBookmarkTags("https://example.com/a", "   ", ""))
    }

    @Test
    fun aSignedBookmarksSignatureVerifiesAgainstItsOwnId() {
        val event = Nostr.signWebBookmark(secretHex, "https://example.com/article", "An Article", now = july25)
        assertEquals(Nostr.KIND_WEB_BOOKMARK, event.kind)
        assertEquals(publicHex, event.pubkey)
        assertEquals(july25.epochSecond, event.createdAt)
        assertTrue(verifies(event))
    }

    @Test
    fun theIdMatchesTheEventItIsSentWithSoTheServerAcceptsIt() {
        val event = Nostr.signWebBookmark(secretHex, "https://example.com/article", now = july25)
        assertEquals(event.id, Nostr.eventId(event.pubkey, event.createdAt, event.kind, event.tags, event.content))
    }

    @Test
    fun anEventSerialisesToTheShapeTheApiDocuments() {
        val json = J(Nostr.signWebBookmark(secretHex, "https://example.com/a").toJson())
        assertEquals(listOf("id", "pubkey", "created_at", "kind", "tags", "content", "sig"), json.keys.toList())
        assertEquals(39701, json["kind"].int)
        assertEquals("", json["content"].string)
        assertEquals(listOf("d", "https://example.com/a"), json["tags"][0].list.map { it.string })
    }

    @Test
    fun twoSavesOfTheSameUrlAreSeparatelyValidEvents() {
        val first = Nostr.signWebBookmark(secretHex, "https://example.com/a", now = july25)
        val second = Nostr.signWebBookmark(secretHex, "https://example.com/a", now = july25)
        assertTrue(verifies(first) && verifies(second))
        assertNotEquals(first.sig, second.sig, "each signature draws its own randomness")
    }

    @Test
    fun eventsAreSignedByteForByteAsXtaSignsThem() {
        val fixture = J.parse(javaClass.getResource("/deepmarks/signed_bookmarks.json")!!.readText())
        assertEquals(secretHex, Nostr.normaliseSecretKey(fixture["nsec"].string!!))
        assertEquals(fixture["publicKey"].string, Nostr.publicKey(secretHex))
        val cases = fixture["cases"].list
        assertEquals(3, cases.size)
        for ((index, case) in cases.withIndex()) {
            val event = Nostr.signWebBookmark(
                secretKeyHex = case["secretKey"].string!!,
                url = case["url"].string!!,
                title = case["title"].string,
                description = case["description"].string,
                topics = case["topics"].list.mapNotNull { it.string },
                now = Instant.ofEpochMilli(case["nowMillis"].long!!),
                auxRandomHex = case["aux"].string,
            )
            assertEquals(case["event"].string, event.toJson().toString(), "case $index")
            assertTrue(verifies(event), "case $index")
        }
    }
}
