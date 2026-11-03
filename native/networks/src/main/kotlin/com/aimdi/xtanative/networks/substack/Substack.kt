package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.networks.NetworkRequestException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Substack, read without an account, as XTA's Substack plugin reads it: each
 * publication's public JSON, its RSS feed when the JSON fails, and the pages a
 * publication that left Substack for its own domain or for Beehiiv still
 * serves. Nothing is subscribed to, liked, commented on or restacked.
 */
object Substack {
    /** The [NetworkRequestException.network] every Substack failure carries. */
    const val NETWORK = "substack"

    internal fun failure(code: Int, message: String) = NetworkRequestException(NETWORK, code, message)

    internal fun rateLimited(host: String, waitMillis: Long) =
        failure(429, "Rate limited by $host; ask again in ${(waitMillis + 999) / 1000} s")

    internal fun notAPublication() = failure(404, "This is not a Substack publication")
}

/**
 * Whether this is a Substack host asking for fewer requests. It ends every
 * fallback chain: no other host or format is asked after one.
 */
val Throwable.isSubstackRateLimit: Boolean
    get() = this is NetworkRequestException && network == Substack.NETWORK && code == 429

/** Text as XTA's Dart reads it: `\s` there takes in Unicode spaces too, such as the no-break space. */
internal object SubstackText {
    private const val SPACE = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

    val spaces = Regex("[$SPACE]+")
    val words = Regex("[^$SPACE]+")

    fun collapse(text: String): String = text.replace(spaces, " ")

    fun terms(query: String): List<String> = query.trim().lowercase().split(spaces).filter { it.isNotEmpty() }

    fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private val compactOffset = Regex("([+-]\\d\\d)(\\d\\d)$")

    /**
     * An ISO-8601 time, as Dart's `DateTime.tryParse` takes it: with an offset
     * or `Z`, or without one as local time, or a bare date at its midnight.
     */
    fun instant(raw: String?): Instant? {
        val text = raw?.trim()?.replace(' ', 'T')?.replace(compactOffset, "$1:$2") ?: return null
        if (text.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
    }
}
