package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.networks.ehviewer.EhErrorKind
import com.aimdi.xtanative.networks.ehviewer.EhNamespace
import com.aimdi.xtanative.networks.ehviewer.EhSearchLanguage
import com.aimdi.xtanative.networks.ehviewer.EhToplistPeriod
import com.aimdi.xtanative.store.settings.EhReadingMode
import com.aimdi.xtanative.ui.plugins.PluginText
import java.util.Locale

/** XTA's words for what the EH screens name (eh_errors.dart, eh_ui.dart, eh_tags.dart, eh_reader_sheets.dart). */
internal object EhText {
    /** Why a read failed, as XTA says it: anything that is not the site's own answer reads as no connection. */
    fun error(error: Throwable?): PluginText = when (error?.let(EhErrorKind::of) ?: EhErrorKind.NETWORK) {
        EhErrorKind.NOT_CONFIGURED -> Strings.error_not_configured
        EhErrorKind.NETWORK -> Strings.error_network
        EhErrorKind.UNAUTHORIZED -> Strings.error_unauthorized
        EhErrorKind.RATE_LIMITED -> Strings.error_rate_limited
        EhErrorKind.NOT_FOUND -> Strings.error_not_found
        EhErrorKind.BANNED -> Strings.error_ban
        // XTA always answers the content warning, so a page behind one is just a page that did not read.
        EhErrorKind.BAD_RESPONSE, EhErrorKind.CONTENT_WARNING -> Strings.error_response
    }

    fun toplist(period: EhToplistPeriod): PluginText = when (period) {
        EhToplistPeriod.YESTERDAY -> Strings.toplist_yesterday
        EhToplistPeriod.MONTH -> Strings.toplist_month
        EhToplistPeriod.YEAR -> Strings.toplist_year
        EhToplistPeriod.ALL_TIME -> Strings.toplist_all
    }

    fun language(language: EhSearchLanguage): PluginText = when (language) {
        EhSearchLanguage.ANY -> Strings.language_any
        EhSearchLanguage.ENGLISH -> Strings.lang_english
        EhSearchLanguage.JAPANESE -> Strings.lang_japanese
        EhSearchLanguage.CHINESE -> Strings.lang_chinese
        EhSearchLanguage.KOREAN -> Strings.lang_korean
        EhSearchLanguage.SPANISH -> Strings.lang_spanish
        EhSearchLanguage.FRENCH -> Strings.lang_french
    }

    fun namespace(namespace: EhNamespace?): PluginText = when (namespace) {
        EhNamespace.LANGUAGE -> Strings.ns_language
        EhNamespace.PARODY -> Strings.ns_parody
        EhNamespace.CHARACTER -> Strings.ns_character
        EhNamespace.GROUP -> Strings.ns_group
        EhNamespace.ARTIST -> Strings.ns_artist
        EhNamespace.COSPLAYER -> Strings.ns_cosplayer
        EhNamespace.MALE -> Strings.ns_male
        EhNamespace.FEMALE -> Strings.ns_female
        EhNamespace.MIXED -> Strings.ns_mixed
        EhNamespace.OTHER -> Strings.ns_other
        EhNamespace.RECLASS -> Strings.ns_reclass
        EhNamespace.TEMP, null -> Strings.ns_temp
    }

    fun readingMode(mode: EhReadingMode): PluginText = when (mode) {
        EhReadingMode.LEFT_TO_RIGHT -> Strings.reader_mode_ltr
        EhReadingMode.RIGHT_TO_LEFT -> Strings.reader_mode_rtl
        EhReadingMode.VERTICAL -> Strings.reader_mode_vertical
    }
}

/** Numbers and names as the EH screens write them. */
internal object EhFormats {
    private val languageCodes = mapOf(
        "english" to "EN", "japanese" to "JA", "chinese" to "ZH", "korean" to "KO", "spanish" to "ES", "french" to "FR",
        "german" to "DE", "russian" to "RU", "portuguese" to "PT", "italian" to "IT", "thai" to "TH", "vietnamese" to "VI",
        "indonesian" to "ID", "polish" to "PL", "ukrainian" to "UK", "arabic" to "AR", "turkish" to "TR",
    )
    private val units = listOf("B", "kB", "MB", "GB")

    /** A short badge for a gallery's language: its ISO code, else its first letters. */
    fun languageCode(language: String): String = languageCodes[language.lowercase()] ?: language.take(2).uppercase()

    /** Bytes as XTA writes a size: whole bytes, else the unit with one decimal (`96.6 MB`). */
    fun size(bytes: Long): String {
        val (value, unit) = generateSequence(bytes.toDouble() to 0) { (value, unit) -> value / 1024 to unit + 1 }
            .first { (value, unit) -> value < 1024 || unit == units.lastIndex }
        return if (unit == 0) "$bytes ${units[0]}" else "%.1f %s".format(Locale.ROOT, value, units[unit])
    }

    /** A tile's rating, `4.5`, as the reader's language writes it. */
    fun shortRating(rating: Double, locale: Locale): String = "%.1f".format(locale, rating)

    /** The gallery page's rating, `4.62`. */
    fun rating(rating: Double, locale: Locale): String = "%.2f".format(locale, rating)

    /** Stars filled to [rating], in halves: each of the five is full, half or empty. */
    fun stars(rating: Double): List<EhStar> = (0 until 5).map { star ->
        val fill = rating - star
        when {
            fill >= 0.75 -> EhStar.FULL
            fill >= 0.25 -> EhStar.HALF
            else -> EhStar.EMPTY
        }
    }
}

internal enum class EhStar { FULL, HALF, EMPTY }
