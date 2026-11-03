package com.aimdi.xtanative.networks.deepmarks

import java.security.SecureRandom
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A signed Nostr event, ready for Deepmarks to fan out to relays. */
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    /** The event as relays and the Deepmarks API take it, in NIP-01's field names and order. */
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("pubkey", pubkey)
        put("created_at", createdAt)
        put("kind", kind)
        put("tags", tagsJson(tags))
        put("content", content)
        put("sig", sig)
    }
}

/** What is wrong with a secret key the user pasted, in words the screen can pass on. */
class NostrKeyException(message: String) : Exception(message)

/**
 * The Nostr side of Deepmarks: the reader's keys and NIP-B0 web bookmarks,
 * signed on the device. The secret key never leaves it; Deepmarks only ever
 * receives the finished event.
 */
object Nostr {
    /** NIP-B0's kind for a web bookmark. */
    const val KIND_WEB_BOOKMARK = 39701

    private val HEX_KEY = Regex("[0-9a-f]{64}")
    private val random = SecureRandom()

    /**
     * A secret key pasted as `nsec1…` (NIP-19) or 64 hex characters, as lower-case
     * hex. Throws [NostrKeyException] saying what is wrong with anything else.
     */
    fun normaliseSecretKey(raw: String): String {
        val text = raw.trim().lowercase().ifEmpty { throw NostrKeyException("empty") }
        return when {
            HEX_KEY.matches(text) -> text
            !text.startsWith("nsec1") -> throw NostrKeyException("not an nsec or 64-character hex key")
            else -> hex(Bech32.secretKey(text))
        }
    }

    /** The x-only public key of [secretKeyHex], as Nostr names an account. */
    fun publicKey(secretKeyHex: String): String = hex(Schnorr.publicKey(secretKey(secretKeyHex)))

    /** NIP-01's event id: the SHA-256 of the canonical `[0, pubkey, created_at, kind, tags, content]`. */
    fun eventId(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
        val canonical = JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(pubkey), JsonPrimitive(createdAt), JsonPrimitive(kind), tagsJson(tags), JsonPrimitive(content)))
        return hex(sha256(canonical.toString().toByteArray(Charsets.UTF_8)))
    }

    /**
     * A bookmark's tags: `d` holds the URL (Deepmarks wants an http(s) one),
     * `title` and `description` only when they say something, and each of
     * [topics] a lower-cased `t`.
     */
    fun webBookmarkTags(url: String, title: String? = null, description: String? = null, topics: List<String> = emptyList()): List<List<String>> =
        listOf(listOf("d", url)) +
            listOfNotNull(said(title)?.let { listOf("title", it) }, said(description)?.let { listOf("description", it) }) +
            topics.mapNotNull(::said).map { listOf("t", it.lowercase()) }

    /**
     * [url] as a NIP-B0 web bookmark signed with [secretKeyHex] at [now].
     * [auxRandomHex] fixes BIP-340's 32 bytes of randomness, for tests;
     * otherwise each signature draws its own.
     */
    fun signWebBookmark(
        secretKeyHex: String,
        url: String,
        title: String? = null,
        description: String? = null,
        topics: List<String> = emptyList(),
        now: Instant = Instant.now(),
        auxRandomHex: String? = null,
    ): NostrEvent {
        val secret = secretKey(secretKeyHex)
        val pubkey = hex(Schnorr.publicKey(secret))
        val createdAt = now.epochSecond
        val tags = webBookmarkTags(url, title, description, topics)
        val id = eventId(pubkey, createdAt, KIND_WEB_BOOKMARK, tags, content = "")
        val aux = auxRandomHex?.let { unhex(it.padStart(64, '0')) } ?: ByteArray(32).also(random::nextBytes)
        val sig = hex(Schnorr.sign(secret, unhex(id), aux))
        return NostrEvent(id, pubkey, createdAt, KIND_WEB_BOOKMARK, tags, content = "", sig = sig)
    }

    /**
     * [hexKey] as a usable secret key. One outside the curve's range is a bad
     * key too, where XTA's signing threw an error nothing caught.
     */
    private fun secretKey(hexKey: String): ByteArray {
        val bytes = hexKey.lowercase().takeIf(HEX_KEY::matches)?.let(::unhex)
        return bytes?.takeIf(Schnorr::isSecretKey) ?: throw NostrKeyException("not a usable secret key")
    }

    private fun said(text: String?) = text?.trim()?.ifEmpty { null }
}

/**
 * NIP-19's bech32 keys, decoded as XTA's bech32 package decodes them: the
 * length, the separator, the characters and the checksum are all checked.
 */
internal object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val MAX_LENGTH = 90
    private const val CHECKSUM_LENGTH = 6
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    /** The 32-byte secret key a lower-case `nsec1…` holds. */
    fun secretKey(nsec: String): ByteArray {
        val (hrp, words) = decode(nsec)
        if (hrp != "nsec") throw NostrKeyException("not an nsec")
        return bytesOf(words).takeIf { it.size == 32 } ?: throw NostrKeyException("nsec does not hold a 32-byte key")
    }

    private fun decode(text: String): Pair<String, List<Int>> {
        val separator = text.lastIndexOf('1')
        val unreadable = when {
            text.length > MAX_LENGTH -> "too long"
            separator < 1 -> "no human-readable part"
            text.length - separator - 1 < CHECKSUM_LENGTH -> "checksum too short"
            text.substring(0, separator).any { it.code !in 33..126 } -> "bad human-readable part"
            else -> null
        }
        if (unreadable != null) throw NostrKeyException("could not decode the nsec ($unreadable)")
        val hrp = text.substring(0, separator)
        val data = text.substring(separator + 1).map { CHARSET.indexOf(it) }
        if (data.any { it < 0 }) throw NostrKeyException("could not decode the nsec (a character is undefined in bech32)")
        if (polymod(expand(hrp) + data) != 1) throw NostrKeyException("could not decode the nsec (checksum verification failed)")
        return hrp to data.dropLast(CHECKSUM_LENGTH)
    }

    private fun expand(hrp: String) = hrp.map { it.code shr 5 } + 0 + hrp.map { it.code and 31 }

    private fun polymod(values: List<Int>): Int = values.fold(1) { checksum, value ->
        val top = checksum ushr 25
        GENERATOR.indices.fold(((checksum and 0x1ffffff) shl 5) xor value) { sum, i ->
            if ((top ushr i) and 1 == 1) sum xor GENERATOR[i] else sum
        }
    }

    /** 5-bit words back to bytes, refusing padding that is a whole word or not zero. */
    private fun bytesOf(words: List<Int>): ByteArray {
        val bits = words.joinToString("") { it.toString(2).padStart(5, '0') }
        val whole = bits.length / 8 * 8
        val padding = bits.substring(whole)
        if (padding.length >= 5 || '1' in padding) throw NostrKeyException("invalid bech32 padding")
        return bits.substring(0, whole).chunked(8).map { it.toInt(2).toByte() }.toByteArray()
    }
}

private fun tagsJson(tags: List<List<String>>) = JsonArray(tags.map { tag -> JsonArray(tag.map(::JsonPrimitive)) })

/** [bytes] as lower-case hex. */
internal fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

/** Even-length hex as bytes. */
internal fun unhex(text: String): ByteArray = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
