package com.aimdi.xtanative.networks.rss

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Dates as feeds write them: RFC 822 (`Tue, 04 Aug 2026 23:03:08 GMT`), ISO
 * 8601 (`2026-08-04T23:03:08Z`), or a bare `2026-08-04`. Out-of-range parts
 * roll over (31 February is 3 March), as Dart's DateTime does in XTA.
 */
object RssDates {
    private val rfc822 = Regex(
        """(?:\w{3},\s+)?(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2})(?::(\d{2}))?\s+([A-Z]+|[+-]\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    /** Dart's DateTime.parse grammar, which XTA reads ISO dates with. */
    private val iso = Regex(
        """^([+-]?\d{4,6})-?(\d\d)-?(\d\d)(?:[ T](\d\d)(?::?(\d\d)(?::?(\d\d)(?:[.,](\d+))?)?)?( ?[zZ]| ?([-+])(\d\d)(?::?(\d\d))?)?)?$""",
    )

    private val loose = Regex("""^(\d{4})[-/](\d{1,2})[-/](\d{1,2})""")

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** RFC 822's named zones, in hours from UTC; any other name is read as UTC. */
    private val zones = mapOf(
        "ut" to 0, "utc" to 0, "gmt" to 0, "z" to 0,
        "est" to -5, "edt" to -4, "cst" to -6, "cdt" to -5,
        "mst" to -7, "mdt" to -6, "pst" to -8, "pdt" to -7,
    )

    /** [raw] as an instant, trying RFC 822, then ISO 8601, then a bare date; null when none reads. */
    fun parse(raw: String?): Instant? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return rfc822(value) ?: iso8601(value) ?: looseDate(value)
    }

    /**
     * [text] read as Dart's DateTime.tryParse reads it. A time without a zone
     * is UTC here; XTA read it in the phone's own zone.
     */
    fun iso8601(text: String): Instant? {
        val m = iso.matchEntire(text) ?: return null
        val g = m.groupValues
        val micros = g[7].takeIf { it.isNotEmpty() }?.padEnd(6, '0')?.take(6)?.toLong() ?: 0
        val offsetMinutes = when {
            g[9].isEmpty() -> 0
            else -> (if (g[9] == "-") -1 else 1) * (g[10].toInt() * 60 + (g[11].toIntOrNull() ?: 0))
        }
        val local = rolled(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0)
            ?.plusNanos(micros * 1000)
        return local?.minusMinutes(offsetMinutes.toLong())?.toInstant(ZoneOffset.UTC)
    }

    /** RFC 822 with its zone applied: XTA read every such date as if it were UTC. */
    private fun rfc822(text: String): Instant? {
        val m = rfc822.find(text) ?: return null
        val g = m.groupValues
        val month = months.indexOf(g[2].lowercase()).takeIf { it >= 0 } ?: return null
        val local = rolled(g[3].toInt(), month + 1, g[1].toInt(), g[4].toInt(), g[5].toInt(), g[6].toIntOrNull() ?: 0)
        return local?.minusMinutes(zoneMinutes(g[7]).toLong())?.toInstant(ZoneOffset.UTC)
    }

    private fun zoneMinutes(zone: String): Int = when (zone.first()) {
        '+', '-' -> (if (zone.first() == '-') -1 else 1) * (zone.substring(1, 3).toInt() * 60 + zone.substring(3, 5).toInt())
        else -> (zones[zone.lowercase()] ?: 0) * 60
    }

    private fun looseDate(text: String): Instant? {
        val g = loose.find(text)?.groupValues ?: return null
        return rolled(g[1].toInt(), g[2].toInt(), g[3].toInt(), 0, 0, 0)?.toInstant(ZoneOffset.UTC)
    }

    /** A local date-time whose parts may be out of range, rolled over as Dart rolls them. */
    private fun rolled(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): LocalDateTime? = runCatching {
        LocalDateTime.of(year, 1, 1, 0, 0)
            .plusMonths(month - 1L)
            .plusDays(day - 1L)
            .plusHours(hour.toLong())
            .plusMinutes(minute.toLong())
            .plusSeconds(second.toLong())
    }.getOrNull()
}
