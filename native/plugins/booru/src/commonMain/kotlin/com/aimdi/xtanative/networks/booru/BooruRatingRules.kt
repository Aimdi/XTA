package com.aimdi.xtanative.networks.booru

/**
 * XTA's rules for adult content. The reader sets a rating cap; posts over it are never shown,
 * hosts are asked to leave them out in their own words, and moving the cap past Sensitive needs
 * the reader to confirm being an adult who wants such posts.
 */
object BooruRatingRules {
    /** The ratings whose thumbnails are blurred when the reader turns blurring on. */
    val BLURRED: Set<BooruRating> = setOf(BooruRating.QUESTIONABLE, BooruRating.EXPLICIT)

    /** Whether moving the cap from [current] to [next] lets Questionable or Explicit posts in for the first time. */
    fun needsAdultConsent(current: BooruRating, next: BooruRating): Boolean =
        next.exceeds(BooruRating.SENSITIVE) && !current.exceeds(BooruRating.SENSITIVE)

    /**
     * Hosts whose catalogue is almost all explicit: a General cap there leaves an empty feed that
     * looks broken, so their cap starts at Explicit, and the adult confirmation names them.
     */
    fun isMostlyExplicit(host: String): Boolean {
        val name = BooruHosts.displayName(host).lowercase()
        return listOf("rule34", "xbooru", "gelbooru.com", "e621").any { it in name }
    }

    /** The cap a site starts with when the reader has set none. */
    fun defaultCap(host: String): BooruRating = if (isMostlyExplicit(host)) BooruRating.EXPLICIT else BooruRating.GENERAL

    /** Whether [post] may be shown under [cap]; a post that names no rating may. */
    fun allows(post: BooruPost, cap: BooruRating): Boolean = post.rating?.exceeds(cap) != true

    /**
     * The metatag [engine] understands for [rating]; null where it has none. Gelbooru itself spells
     * ratings out; its older forks, Moebooru and e621 know only safe, questionable and explicit.
     */
    fun metatag(engine: BooruEngine, rating: BooruRating, host: String): String? {
        val value = when {
            engine == BooruEngine.DANBOORU -> rating.code
            engine == BooruEngine.GELBOORU_V2 && "gelbooru.com" in host -> rating.word
            rating == BooruRating.SENSITIVE -> null
            engine == BooruEngine.GELBOORU_V2 -> if (rating == BooruRating.GENERAL) "safe" else rating.word
            else -> if (rating == BooruRating.GENERAL) "s" else rating.code
        }
        return value?.let { "rating:$it" }
    }

    /**
     * [tags] with [cap] said to the host in its own words, unless the search names a rating
     * itself. Gelbooru forks know only safe, questionable and explicit, so between General and
     * Explicit only "not explicit" is said the same on every fork; posts over the cap that the host
     * still sends are left out on the device.
     */
    fun withCap(tags: List<String>, engine: BooruEngine, host: String, cap: BooruRating): List<String> {
        if (tags.any { it.lowercase().startsWith("rating:") }) return tags
        return tags + when (engine) {
            BooruEngine.GELBOORU_V2 -> when (cap) {
                BooruRating.GENERAL -> listOfNotNull(metatag(engine, cap, host))
                BooruRating.SENSITIVE, BooruRating.QUESTIONABLE -> listOf("-rating:explicit")
                BooruRating.EXPLICIT -> emptyList()
            }
            // Moebooru and e621: `s` is Safe. Danbooru: `g` is General, `s` Sensitive.
            BooruEngine.MOEBOORU, BooruEngine.E621 -> lettered(cap, general = "s")
            BooruEngine.DANBOORU -> lettered(cap, general = "g")
        }
    }

    private fun lettered(cap: BooruRating, general: String): List<String> = when (cap) {
        BooruRating.GENERAL -> listOf("rating:$general")
        BooruRating.SENSITIVE -> listOf("-rating:q", "-rating:e")
        BooruRating.QUESTIONABLE -> listOf("-rating:e")
        BooruRating.EXPLICIT -> emptyList()
    }
}
