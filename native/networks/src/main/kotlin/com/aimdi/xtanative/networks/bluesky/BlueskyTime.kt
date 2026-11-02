package com.aimdi.xtanative.networks.bluesky

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Times read and written as XTA's Dart does (`DateTime.tryParse`,
 * `toIso8601String`), so a search cursor carries exactly the time XTA's would.
 */
internal object BlueskyTime {
    /** Dart's own grammar: a date, then optionally a time down to microseconds and a zone. */
    private val grammar = Regex(
        """([+-]?\d{4,6})-?(\d\d)-?(\d\d)""" +
            """(?:[ T](\d\d)(?::?(\d\d)(?::?(\d\d)(?:[.,](\d+))?)?)?""" +
            """( ?[zZ]| ?([-+])(\d\d)(?::?(\d\d))?)?)?""",
    )

    private val toSeconds = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC)

    /** [text] as an instant, null when it is no date. Without a zone it is the device's local time, as in Dart. */
    fun parse(text: String?): Instant? {
        val parts = text?.let(grammar::matchEntire)?.groupValues ?: return null
        return runCatching {
            val local = LocalDateTime.of(
                parts[1].toInt(), parts[2].toInt(), parts[3].toInt(),
                parts[4].toIntOrZero(), parts[5].toIntOrZero(), parts[6].toIntOrZero(),
                parts[7].take(6).padEnd(6, '0').toInt() * 1_000,
            )
            when {
                parts[8].isEmpty() -> local.atZone(ZoneId.systemDefault()).toInstant()
                parts[9].isEmpty() -> local.toInstant(ZoneOffset.UTC)
                else -> {
                    val sign = if (parts[9] == "-") -1 else 1
                    local.toInstant(ZoneOffset.ofHoursMinutes(sign * parts[10].toInt(), sign * parts[11].toIntOrZero()))
                }
            }
        }.getOrNull()
    }

    /** [instant] in UTC with milliseconds, and microseconds when it has them: `2026-08-01T10:00:00.000Z`. */
    fun iso(instant: Instant): String {
        val micros = instant.nano / 1_000
        val sub = micros % 1_000
        val fraction = (micros / 1_000).pad3() + if (sub == 0) "" else sub.pad3()
        return "${toSeconds.format(instant)}.${fraction}Z"
    }

    private fun String.toIntOrZero(): Int = toIntOrNull() ?: 0

    private fun Int.pad3(): String = toString().padStart(3, '0')
}
