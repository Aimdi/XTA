package com.aimdi.xtanative.store.backup

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The two ways XTA's backup writes a time. Rows keep the text as XTA wrote
 * it; these turn it into an instant and back.
 */
object XtaTime {
    private val sqlite = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val isoUtc = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    private val sqliteShape = Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")

    /** `yyyy-MM-dd HH:mm:ss` in [zone], as XTA writes when a followed account was followed. */
    fun sqlite(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = sqlite.format(instant.atZone(zone))

    /** Dart's `toIso8601String()` of a UTC time, `2026-08-04T09:30:00.000Z`, as XTA writes every other time. */
    fun iso(instant: Instant): String = isoUtc.format(instant)

    /**
     * Either form back to an instant. A time without a zone is read in
     * [zone], as Dart reads it on the device; null when it is no time at all.
     */
    fun parse(text: String?, zone: ZoneId = ZoneId.systemDefault()): Instant? {
        val iso = text?.trim()?.takeIf { it.isNotEmpty() }?.let { if (sqliteShape.containsMatchIn(it)) it.replaceFirst(' ', 'T') else it } ?: return null
        return runCatching { OffsetDateTime.parse(iso).toInstant() }
            .recoverCatching { Instant.parse(iso) }
            .recoverCatching { LocalDateTime.parse(iso).atZone(zone).toInstant() }
            .getOrNull()
    }
}
