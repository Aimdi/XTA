package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** How Booru's screens write numbers, dates and tags, in the reader's [Locale] (booru_labels.dart, plugin_tag_chip.dart). */
internal object BooruFormats {
    /** Kinds of tag that say what the picture is of, listed first in a label. */
    private val NAMED_KINDS = listOf(BooruTagCategory.ARTIST, BooruTagCategory.CHARACTER, BooruTagCategory.COPYRIGHT, BooruTagCategory.SPECIES)

    /** A short count's units, smallest first: it is written in the largest it reaches. */
    private val UNITS = listOf(1_000L to "K", 1_000_000L to "M", 1_000_000_000L to "B")

    /** How a tag reads: boorus join words with underscores, people read spaces. */
    fun tagName(tag: String): String = tag.replace('_', ' ')

    /**
     * Up to [count] of [post]'s tags for its label read aloud: the artist,
     * characters and series first, then the rest in the host's order. Meta tags
     * say nothing about the picture.
     */
    fun labelTags(post: BooruPost, count: Int = 5): List<String> {
        val kind = { tag: String -> post.tagCategories[tag] ?: BooruTagCategory.GENERAL }
        val named = NAMED_KINDS.flatMap { category -> post.tags.filter { kind(it) == category } }
        val rest = post.tags.filter { kind(it) !in NAMED_KINDS && kind(it) != BooruTagCategory.META }
        return (named + rest).take(count)
    }

    /**
     * 5.45M, 36.3K, 50: short enough to sit beside a tag, to three figures, in
     * [locale]'s digits. K, M and B in every language, as the app writes counts:
     * Android has no java.text compact format.
     */
    fun compactCount(count: Long, locale: Locale): String {
        val rounded = if (count < 1000) count else BigDecimal(count).round(MathContext(3)).toLong()
        val (size, suffix) = UNITS.lastOrNull { rounded >= it.first } ?: return number(count, locale)
        val figures = BigDecimal(rounded).divide(BigDecimal(size))
        return DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)).format(figures) + suffix
    }

    /** A suggestion's post count: short counts stay exact. */
    fun suggestionCount(count: Long, locale: Locale): String =
        if (count < 1_000_000) NumberFormat.getIntegerInstance(locale).format(count) else compactCount(count, locale)

    fun number(value: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

    /** When a post went up, date and time, as the facts under it say it. */
    fun dateTime(instant: Instant, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone))

    fun date(instant: Instant, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String = day(instant.atZone(zone).toLocalDate(), locale)

    fun day(date: LocalDate, locale: Locale): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    /** A file's size as XTA writes it: bytes whole, anything larger with one decimal (kB, MB, GB, of 1024). */
    fun size(bytes: Long): String {
        val units = listOf("B", "kB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "$bytes ${units[0]}" else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }

    /** A file's type and size, as one fact: `PNG · 1.2 MB`; null when the host named neither. */
    fun file(post: BooruPost): String? =
        listOfNotNull(post.fileExt?.uppercase()?.ifEmpty { null }, post.fileSize?.let(::size)).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    /** `https://www.pixiv.net/users/1` reads as `pixiv.net`. */
    fun siteName(url: String): String = (runCatching { java.net.URI(url).host }.getOrNull() ?: url).removePrefix("www.")
}
