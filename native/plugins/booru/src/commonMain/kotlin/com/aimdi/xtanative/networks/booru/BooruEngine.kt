package com.aimdi.xtanative.networks.booru

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The public API family a booru speaks; each has its own endpoints and answers. [id] is how XTA stores it. */
enum class BooruEngine(val id: String) {
    DANBOORU("danbooru"),
    MOEBOORU("moebooru"),
    GELBOORU_V2("gelbooru_v2"),
    E621("e621"),
    ;

    companion object {
        /** The engine [raw] names, with the other spellings XTA accepts (`gelbooru`, `e926`); null for any other. */
        fun parse(raw: String?): BooruEngine? = when (raw.orEmpty().trim().lowercase()) {
            "danbooru" -> DANBOORU
            "moebooru" -> MOEBOORU
            "gelbooru", "gelbooru_v2", "gelbooru2" -> GELBOORU_V2
            "e621", "e926" -> E621
            else -> null
        }
    }
}

/** A booru offered by name: one of [BooruPresets], or a site the reader added. */
data class BooruPreset(val id: String, val name: String, val engine: BooruEngine, val host: String)

/**
 * The boorus XTA has built in, guest-friendly first. Gelbooru.com often wants an API key where
 * Safebooru does not; e926 is e621's safe mirror; Rule34 and Xbooru speak Gelbooru 0.2.
 */
object BooruPresets {
    val ALL: List<BooruPreset> = listOf(
        BooruPreset("danbooru", "Danbooru", BooruEngine.DANBOORU, "https://danbooru.donmai.us"),
        BooruPreset("aibooru", "AIBooru", BooruEngine.DANBOORU, "https://aibooru.online"),
        BooruPreset("yandere", "Yande.re", BooruEngine.MOEBOORU, "https://yande.re"),
        BooruPreset("konachan", "Konachan", BooruEngine.MOEBOORU, "https://konachan.com"),
        BooruPreset("sakugabooru", "Sakugabooru", BooruEngine.MOEBOORU, "https://www.sakugabooru.com"),
        BooruPreset("safebooru", "Safebooru", BooruEngine.GELBOORU_V2, "https://safebooru.org"),
        BooruPreset("gelbooru", "Gelbooru", BooruEngine.GELBOORU_V2, "https://gelbooru.com"),
        BooruPreset("rule34", "Rule34", BooruEngine.GELBOORU_V2, "https://rule34.xxx"),
        BooruPreset("tbib", "TBIB", BooruEngine.GELBOORU_V2, "https://tbib.org"),
        BooruPreset("xbooru", "Xbooru", BooruEngine.GELBOORU_V2, "https://xbooru.com"),
        BooruPreset("e926", "e926", BooruEngine.E621, "https://e926.net"),
        BooruPreset("e621", "e621", BooruEngine.E621, "https://e621.net"),
    )

    /** The site XTA reads when none is chosen. */
    val DEFAULT: BooruPreset get() = ALL.first()
}

/** How XTA writes, rewrites and recognises booru hosts. */
object BooruHosts {
    /**
     * [raw] the way XTA keeps a host: `https://` when no scheme is given, lower case, no default
     * port, and no query or trailing slash (`danbooru.donmai.us/` is `https://danbooru.donmai.us`).
     * Empty when [raw] names no host.
     */
    fun normalise(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val url = (if ("://" in trimmed) trimmed else "https://$trimmed").toHttpUrlOrNull() ?: return ""
        return origin(url, url.host) + url.encodedPath.trimEnd('/')
    }

    /** Where [host]'s API is asked: Rule34 serves it on `api.rule34.xxx`. */
    fun requestHost(host: String): String = swapHost(host, setOf("rule34.xxx", "www.rule34.xxx"), "api.rule34.xxx")

    /** Where [host]'s pages open: `api.rule34.xxx` has none of its own. */
    fun pageHost(host: String): String = swapHost(host, setOf("api.rule34.xxx"), "rule34.xxx")

    /**
     * The engine a host most likely speaks, from its name. An unknown clone is taken for Gelbooru
     * 0.2, which Rule34's forks speak; Paheal and Sankaku speak none XTA reads.
     */
    fun guessEngine(host: String): BooruEngine? {
        val name = normalise(host).toHttpUrlOrNull()?.host ?: return null
        return when {
            listOf("paheal", "sankaku").any { it in name } -> null
            listOf("e621", "e926").any { it in name } -> BooruEngine.E621
            listOf("yande.re", "konachan", "sakugabooru").any { it in name } -> BooruEngine.MOEBOORU
            listOf("donmai.us", "danbooru", "aibooru").any { it in name } -> BooruEngine.DANBOORU
            else -> BooruEngine.GELBOORU_V2
        }
    }

    /** The bare host name a site is shown by when it has no name of its own. */
    fun displayName(host: String): String = normalise(host).toHttpUrlOrNull()?.host.orEmpty()

    /** A tag as typed, in a host's spelling: trimmed, lower case, spaces as underscores; null when blank. */
    fun normaliseTag(raw: String): String? = raw.trim().lowercase().replace(WHITESPACE, "_").ifEmpty { null }

    private fun swapHost(host: String, from: Set<String>, to: String): String {
        val normalised = normalise(host)
        val url = normalised.toHttpUrlOrNull() ?: return normalised
        return if (url.host in from) origin(url, to) + url.encodedPath.trimEnd('/') else normalised
    }

    private fun origin(url: HttpUrl, host: String): String =
        HttpUrl.Builder().scheme(url.scheme).host(host).port(url.port).build().toString().removeSuffix("/")
}
