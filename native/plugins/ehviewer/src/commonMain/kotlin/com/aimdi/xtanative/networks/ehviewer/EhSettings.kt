package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What XTA reads E-Hentai with: the account's pasted [cookies], whether to
 * read ExHentai, the [categories] the front page keeps to, and the reader's
 * [adultConsent].
 *
 * [adultConsent] is the reader's answer to the site's content warning ("This
 * gallery has been flagged as Offensive For Everyone"). Given, as XTA always
 * gives it, every read says so and flagged galleries open without the warning
 * page; withheld, a flagged gallery reads as [EhErrorKind.CONTENT_WARNING].
 */
data class EhSettings(
    val cookies: String = "",
    val useExhentai: Boolean = false,
    val categories: Set<EhCategory> = EhCategory.entries.toSet(),
    val adultConsent: Boolean = true,
) {
    val accountCookies: String get() = cookies.trim()

    /** Signed in: the account's cookies were pasted. */
    val hasCookies: Boolean get() = accountCookies.isNotEmpty()

    /** ExHentai only with an account: without one it answers nothing. */
    val host: String get() = if (useExhentai && hasCookies) EXHENTAI else EHENTAI

    /** The Cookie header of every page read: the account's cookies with XTA's `uconfig` and the consent. */
    val requestCookies: String get() = EhCookies.forRequest(accountCookies, adultConsent)

    /**
     * Headers for loading the image at [url]. The cookies go only to the
     * site's own hosts (originals and ExHentai's thumbnails need them); the
     * image servers that volunteers run never see the account. A loader that
     * follows `fullimg`'s redirect to an image server should not carry them on.
     */
    fun imageHeaders(url: String): Map<String, String> = buildMap {
        put("User-Agent", EhClient.USER_AGENT)
        put("Referer", "$host/")
        put("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        if (isSiteUrl(url)) put("Cookie", requestCookies)
    }

    companion object {
        const val EHENTAI = "https://e-hentai.org"
        const val EXHENTAI = "https://exhentai.org"

        private val siteDomains = listOf("e-hentai.org", "exhentai.org", "ehgt.org")

        /** Whether [url] is on one of the site's own hosts, the only ones its cookies go to. */
        fun isSiteUrl(url: String): Boolean {
            val host = url.toHttpUrlOrNull()?.host ?: return false
            return siteDomains.any { host == it || host.endsWith(".$it") }
        }

        /**
         * The categories XTA keeps as a JSON list of names; blank or unreadable
         * means every category.
         */
        fun categoriesFrom(stored: String?): Set<EhCategory> {
            if (stored.isNullOrBlank()) return EhCategory.entries.toSet()
            val names = J.parse(stored)
            if (names.raw !is JsonArray) return EhCategory.entries.toSet()
            return names.list.mapNotNull { EhCategory.tryParse(it.string) }.toSet()
        }
    }
}

/** How XTA's reads ask the site for its larger images and tiles, through the `uconfig` cookie. */
object EhCookies {
    /** The largest free resample (`uconfig` key `xr`). */
    const val IMAGE_SIZE = "2400"

    /** Large preview tiles (`uconfig` key `ts`). */
    const val PREVIEW_SIZE = "l"

    /**
     * [raw] cookies, each once in the order given, with `uconfig` asking for
     * [imageSize] resamples and [previewSize] tiles. With [consent] the
     * content warning is answered twice over: XTA's `nw_1` in `uconfig`, and
     * the site's own `nw=1` cookie, which its "Never Warn Me Again" link sets.
     */
    fun forRequest(raw: String, consent: Boolean = true, imageSize: String = IMAGE_SIZE, previewSize: String = PREVIEW_SIZE): String {
        val cookies = pairs(raw.split(';'), '=') { it.trim().unquoted() }
        cookies["uconfig"] = uconfig(cookies["uconfig"].orEmpty(), consent, imageSize, previewSize)
        if (consent) cookies["nw"] = "1"
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** [raw] `uconfig` flags with `xr` and `ts` set, and `nw` with [consent]; the other flags stay as they were. */
    fun uconfig(raw: String, consent: Boolean = true, imageSize: String = IMAGE_SIZE, previewSize: String = PREVIEW_SIZE): String {
        val flags = pairs(raw.split('-'), '_') { it }
        flags["xr"] = imageSize
        flags["ts"] = previewSize
        if (consent) flags["nw"] = "1"
        return flags.entries.joinToString("-") { "${it.key}_${it.value}" }
    }

    /** `key<separator>value` pieces, first-seen order, the last value of a key winning. */
    private fun pairs(pieces: List<String>, separator: Char, value: (String) -> String): LinkedHashMap<String, String> =
        pieces.map { it.trim() }.filter { it.isNotEmpty() }.fold(LinkedHashMap()) { found, piece ->
            val at = piece.indexOf(separator)
            if (at > 0) found[piece.substring(0, at).trim()] = value(piece.substring(at + 1))
            found
        }

    private fun String.unquoted(): String = if (length >= 2 && startsWith('"') && endsWith('"')) substring(1, length - 1) else this
}
