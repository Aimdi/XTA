package com.aimdi.xtanative.networks.booru

/**
 * The reader's login and API key for one booru, and the [host] they were entered for. They are
 * only ever sent to that host: a key typed for one site never reaches the next one chosen.
 */
data class BooruCredentials(val host: String, val login: String, val apiKey: String) {
    /** Whether these were entered for [host]. */
    fun belongTo(host: String): Boolean {
        val own = BooruHosts.normalise(this.host)
        return own.isNotEmpty() && own == BooruHosts.normalise(host)
    }

    override fun toString(): String = "BooruCredentials(host=$host, login=***, apiKey=***)"
}

/**
 * What the reader chose for the booru reader, as the client reads it before each request: the
 * site ([engine] and [host]; Danbooru when none is chosen), the [credentials], the rating cap and
 * the blacklist. Where these are kept is the app's business; nothing here is stored.
 */
data class BooruSettings(
    val engine: BooruEngine = BooruPresets.DEFAULT.engine,
    val host: String = "",
    val credentials: BooruCredentials? = null,
    /** The highest rating shown; null for the site's default ([BooruRatingRules.defaultCap]). */
    val maxRating: BooruRating? = null,
    /** Blacklist entries as typed; an entry of several tags hides the posts that have them all. */
    val muted: Set<String> = emptySet(),
) {
    /** The site requests go to, with the credentials only if they were entered for it. */
    val site: BooruSite get() = siteAt(BooruHosts.normalise(host).ifEmpty { BooruPresets.DEFAULT.host }, engine)

    /** The rating cap in force. */
    val ratingCap: BooruRating get() = maxRating ?: BooruRatingRules.defaultCap(site.host)

    /** The blacklist, normalised and de-duplicated. */
    val blacklist: Set<String> get() = BooruFilter.blacklist(muted)

    /** What a feed's posts depend on: the site, the cap and the blacklist. A feed loaded under another key is stale. */
    val feedKey: String get() = (listOf(engine.id, site.host, ratingCap.code) + blacklist.sorted()).joinToString("\n")

    /** [host] spoken to as [engine], with the credentials only if they were entered for it. */
    fun siteAt(host: String, engine: BooruEngine): BooruSite {
        val own = credentials?.takeIf { it.belongTo(host) }
        return BooruSite(engine, host, own?.login?.trim().orEmpty(), own?.apiKey?.trim().orEmpty())
    }
}
