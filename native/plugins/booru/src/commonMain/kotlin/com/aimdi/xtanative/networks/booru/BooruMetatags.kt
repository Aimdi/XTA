package com.aimdi.xtanative.networks.booru

/**
 * The metatags each engine's search understands, and what to offer while one is typed, without
 * asking the host. Danbooru's and Gelbooru's follow the lists Boorusama keeps; Moebooru's and
 * e621's follow the sites' cheat sheets, as XTA's do.
 */
object BooruMetatags {
    /** The metatags [engine] understands, of those XTA reads as metatags ([BooruQuery.METATAGS]). */
    fun of(engine: BooruEngine): List<String> = when (engine) {
        BooruEngine.DANBOORU -> listOf(
            "age", "date", "fav", "favcount", "filetype", "height", "id", "md5", "mpixels", "order", "ordfav",
            "parent", "pool", "rating", "ratio", "score", "source", "status", "tagcount", "user", "width",
        )
        BooruEngine.MOEBOORU -> listOf(
            "date", "height", "id", "md5", "mpixels", "order", "parent", "pool", "rating", "score", "source", "user", "width",
        )
        BooruEngine.GELBOORU_V2 -> listOf("height", "id", "md5", "parent", "rating", "score", "sort", "user", "width")
        BooruEngine.E621 -> listOf(
            "date", "fav", "favcount", "height", "id", "md5", "mpixels", "order", "parent", "pool", "rating", "ratio",
            "score", "source", "status", "tagcount", "user", "width",
        )
    }

    /** The values worth offering after `name:` on [engine]; none where any value goes (a user, a number). Ratings stop at [cap]. */
    fun values(engine: BooruEngine, name: String, host: String, cap: BooruRating): List<String> = when {
        name == "rating" -> BooruRating.entries.filterNot { it.exceeds(cap) }
            .mapNotNull { BooruRatingRules.metatag(engine, it, host)?.removePrefix("rating:") }
        engine == BooruEngine.DANBOORU && name == "order" -> listOf(
            "score", "favcount", "rank", "id", "id_desc", "change", "comment", "note", "mpixels", "landscape", "portrait",
            "filesize", "random",
        )
        engine == BooruEngine.MOEBOORU && name == "order" -> listOf("score", "id", "id_desc", "mpixels", "landscape", "portrait", "vote")
        engine == BooruEngine.E621 && name == "order" -> listOf(
            "score", "favcount", "id", "tagcount", "comment_count", "mpixels", "filesize", "landscape", "portrait", "random",
        )
        engine == BooruEngine.GELBOORU_V2 && name == "sort" -> listOf("score", "id", "updated", "width", "height", "random")
        engine == BooruEngine.DANBOORU && name == "filetype" -> listOf("jpg", "png", "gif", "webp", "mp4", "webm", "zip")
        engine == BooruEngine.DANBOORU && name == "status" -> listOf("pending", "flagged", "deleted", "banned", "active", "any")
        engine == BooruEngine.E621 && name == "status" -> listOf("pending", "flagged", "deleted", "active", "any")
        (engine == BooruEngine.DANBOORU || engine == BooruEngine.E621) && name == "parent" -> listOf("none", "any")
        else -> emptyList()
    }

    /**
     * What to offer while [input] is typed: from two letters on, the metatags whose names it
     * starts, each with its colon to be finished in the field; after a colon, the known values of
     * that metatag, as the whole `name:value`.
     */
    fun suggestions(engine: BooruEngine, input: String, host: String, cap: BooruRating): List<BooruTagSuggestion> {
        val typed = input.trim()
        val body = typed.substring(BooruTagOperator.of(typed).prefix.length).lowercase()
        val names = of(engine)
        val colon = body.indexOf(':')
        if (colon < 0) {
            return if (body.length < 2) emptyList() else names.filter { it.startsWith(body) }.map { BooruTagSuggestion("$it:") }
        }
        val name = body.substring(0, colon)
        val value = body.substring(colon + 1)
        if (name !in names) return emptyList()
        return values(engine, name, host, cap).filter { it.startsWith(value) && it != value }.map { BooruTagSuggestion("$name:$it") }
    }

    /** Whether [suggestion] names a metatag still to be given its value in the field. */
    fun suggestsName(suggestion: BooruTagSuggestion): Boolean = suggestion.name.endsWith(":")
}

/**
 * Whether posts answer a search the way the hosts' own searches read it, checked on the device:
 * for the reader's blacklist, and for the tags of a search past a host's tag limit that the host
 * was not sent ([BooruTagLimit]).
 */
object BooruFilter {
    private val RANGE = Regex("^(-?\\d+)?\\.\\.(-?\\d+)?$")
    private val COMPARISON = Regex("^(<=|>=|<|>)?(-?\\d+)$")

    /** Blacklist entries, normalised ([BooruQuery.normalise]) and de-duplicated; an entry may hold several tokens. */
    fun blacklist(entries: Iterable<String>): Set<String> = entries.mapNotNull(BooruQuery::normalise).toSet()

    /** Whether an entry of [blacklist] hides [post]: one whose tokens all match it ([matches]). */
    fun muted(post: BooruPost, blacklist: Set<String>): Boolean = blacklist.any { matches(post, BooruQuery.tokens(it)) }

    /**
     * Whether [post] answers every one of [tokens]: `-tag` matches a post without it, `~a ~b` one
     * with either, `rating:x` reads the post's engine's letters, and `score:<0`, `user:name`,
     * `filetype:gif` and the like compare what the post says. A metatag the post cannot answer
     * (its status, an uploader it does not name) matches nothing, excluded or not. No tokens match nothing.
     */
    fun matches(post: BooruPost, tokens: List<String>): Boolean {
        val parsed = tokens.map(BooruQueryToken::parse)
        if (parsed.isEmpty()) return false
        val tags = post.tags.map { it.lowercase() }.toSet()
        val (either, rest) = parsed.partition { it.operator == BooruTagOperator.EITHER }
        return rest.all { tokenMatches(post, tags, it) } && (either.isEmpty() || either.any { tokenMatches(post, tags, it) })
    }

    private fun tokenMatches(post: BooruPost, tags: Set<String>, token: BooruQueryToken): Boolean {
        val present = when (val name = token.metatag) {
            null -> token.value.lowercase() in tags
            "rating" -> BooruRating.parseWire(token.value, post.engine)?.let { it == post.rating } ?: false
            else -> metatagMatches(post, name, token.value)
        } ?: return false
        return if (token.operator == BooruTagOperator.EXCLUDE) !present else present
    }

    /** Whether [post] answers `name:value`; null when the post does not carry what it asks about, or the value cannot be read. */
    private fun metatagMatches(post: BooruPost, name: String, value: String): Boolean? = when (name) {
        "score" -> numberMatches(post.score?.toLong(), value)
        "id" -> numberMatches(post.id.toLongOrNull(), value)
        "width" -> numberMatches(post.width.toLong(), value)
        "height" -> numberMatches(post.height.toLong(), value)
        "favcount" -> numberMatches(post.favCount?.toLong(), value)
        "tagcount" -> numberMatches(post.tags.size.toLong(), value)
        "user" -> textMatches(post.uploader, value)
        "filetype" -> textMatches(post.fileExt, value)
        "md5" -> textMatches(post.md5, value)
        "parent" -> presenceMatches(post.parentId, value) ?: textMatches(post.parentId, value)
        "source" -> presenceMatches(post.source, value) ?: textMatches(post.source, value, prefix = true)
        else -> null
    }

    /** `5`, `>5`, `>=5`, `<5`, `<=5`, `1..9`, `..9` or `1..`. */
    private fun numberMatches(number: Long?, value: String): Boolean? {
        if (number == null) return null
        RANGE.matchEntire(value)?.let { range ->
            val low = range.groupValues[1].toLongOrNull()
            val high = range.groupValues[2].toLongOrNull()
            return if (low == null && high == null) null else (low == null || number >= low) && (high == null || number <= high)
        }
        val comparison = COMPARISON.matchEntire(value) ?: return null
        val bound = comparison.groupValues[2].toLongOrNull() ?: return null
        return when (comparison.groupValues[1]) {
            "<" -> number < bound
            "<=" -> number <= bound
            ">" -> number > bound
            ">=" -> number >= bound
            else -> number == bound
        }
    }

    /** `none` and `any` ask whether the post has the field at all. */
    private fun presenceMatches(field: String?, value: String): Boolean? = when (value.lowercase()) {
        "none" -> field.isNullOrEmpty()
        "any" -> !field.isNullOrEmpty()
        else -> null
    }

    /** Case-insensitive, `*` standing for any text; with [prefix] the value only has to start the field, as a source search reads it. */
    private fun textMatches(field: String?, value: String, prefix: Boolean = false): Boolean? {
        if (field.isNullOrEmpty() || value.isEmpty()) return null
        val pattern = value.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex("^$pattern${if (prefix) "" else "\\z"}", RegexOption.IGNORE_CASE).containsMatchIn(field)
    }
}
