package com.aimdi.xtanative.core.x.signing

import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

/**
 * The vectors were written by XTA's Dart signer (lib/client/x_client_transaction_id):
 * a page, its signing script, and a transaction id the Dart code derived from them.
 * An id carries the first 16 bytes of SHA-256 over the request, its time, the
 * keyword and the animation key, so matching them proves the Kotlin port derives
 * the same animation key from the same page.
 */
class TransactionSignerTest {
    private val vectors = Json.parseToJsonElement(
        javaClass.getResource("/signer_vectors.json")!!.readText(),
    ).jsonArray.map { it.jsonObject }

    @Test
    fun derivesTheSameKeyAsXtaFromEveryVector() {
        assertEquals(12, vectors.size)
        for (vector in vectors) {
            val html = vector["html"]!!.jsonPrimitive.content
            val js = vector["js"]!!.jsonPrimitive.content
            val path = vector["path"]!!.jsonPrimitive.content
            val id = vector["id"]!!.jsonPrimitive.content

            val page = Jsoup.parse(html)
            assertTrue(TransactionSigner.hasBootstrapData(page), "the page has the key and frames")
            val signer = TransactionSigner.derive(page, js)

            val decoded = Base64.getDecoder().decode(padded(id)).map { it.toInt() and 0xFF }
            val unmasked = decoded.drop(1).map { it xor decoded[0] }
            assertContentEquals(signer.keyBytes.toList(), unmasked.take(signer.keyBytes.size))
            val time = (0 until 4).sumOf { i -> unmasked[signer.keyBytes.size + i].toLong() shl (i * 8) }
            val hash = unmasked.drop(signer.keyBytes.size + 4).take(16)
            val expected = MessageDigest.getInstance("SHA-256")
                .digest("GET!$path!$time${TransactionSigner.DEFAULT_KEYWORD}${signer.animationKey}".toByteArray())
                .take(16).map { it.toInt() and 0xFF }
            assertEquals(expected, hash, "animation key ${signer.animationKey} for $js")
            assertEquals(3, unmasked.last())
        }
    }

    @Test
    fun anIdIsTheKeyTheTimeTheHashAndTheExtraNumberMasked() {
        val signer = TransactionSigner(IntArray(48) { it }, "test-animation-key")
        val id = signer.transactionId("GET", "/i/api/graphql/test/UserTweets", nowMillis = 1_800_000_000_000, random = Random(7))
        assertFalse('=' in id)
        val decoded = Base64.getDecoder().decode(padded(id)).map { it.toInt() and 0xFF }
        val unmasked = decoded.drop(1).map { it xor decoded[0] }
        assertEquals((0 until 48).toList(), unmasked.take(48))
        assertEquals(48 + 21, unmasked.size)
        assertEquals(3, unmasked.last())
    }

    @Test
    fun aPageWithoutTheKeyOrFramesIsNotABootstrapPage() {
        assertFalse(TransactionSigner.hasBootstrapData(Jsoup.parse("<html><body>sign in</body></html>")))
        val keyOnly = "<meta name=\"twitter-site-verification\" content=\"${Base64.getEncoder().encodeToString(ByteArray(48))}\">"
        assertFalse(TransactionSigner.hasBootstrapData(Jsoup.parse(keyOnly)))
    }

    @Test
    fun aScriptWithoutIndicesFails() {
        assertFailsWith<IllegalStateException> { TransactionSigner.indicesOf("console.log(1)") }
        assertEquals(1 to listOf(2, 3, 4), TransactionSigner.indicesOf("f(a[1],16);f(a[2],16);f(a[3],16);f(a[4],16);"))
    }

    @Test
    fun hexadecimalKeepsTheFraction() {
        assertEquals("2.C", floatToHex(2.75))
        assertEquals(".8", floatToHex(0.5))
        assertEquals("1", floatToHex(1.0))
        assertEquals(3L, dartRound(2.5))
        assertEquals(-3L, dartRound(-2.5))
        assertEquals(0.13, roundTo2(0.125))
    }

    private fun padded(id: String) = id + "=".repeat((4 - id.length % 4) % 4)
}
