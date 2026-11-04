package com.aimdi.xtanative.networks.booru

/** Whitespace as XTA's Dart splits on it, Unicode spaces (an ideographic space typed in Japanese) included. */
internal val WHITESPACE = Regex("(?U)\\s+")

/** How a search token is used: as is, excluded (`-tag`), or as one of an either group (`~a ~b`). */
enum class BooruTagOperator(val prefix: String) {
    NONE(""),
    EXCLUDE("-"),
    EITHER("~"),
    ;

    companion object {
        fun of(token: String): BooruTagOperator = when (token.firstOrNull()) {
            '-' -> EXCLUDE
            '~' -> EITHER
            else -> NONE
        }
    }
}

/**
 * One search token split into its operator, metatag name and value. Only the names in
 * [BooruQuery.METATAGS] read as metatags: elsewhere a colon is part of the tag (`re:zero`, `:d`).
 */
data class BooruQueryToken(val operator: BooruTagOperator, val metatag: String?, val value: String) {
    /** The slot a token competes for: `x`, `-x` and `~x` replace one another, and a search has one rating and one order. */
    val slot: String
        get() = when {
            metatag == null -> "tag:${value.lowercase()}"
            metatag == "rating" && operator == BooruTagOperator.NONE -> "rating"
            metatag == "order" || metatag == "sort" -> "order"
            else -> "${operator.prefix}$metatag:${value.lowercase()}"
        }

    companion object {
        fun parse(raw: String): BooruQueryToken {
            val operator = BooruTagOperator.of(raw)
            val body = raw.substring(operator.prefix.length)
            val colon = body.indexOf(':')
            val name = if (colon > 0) body.substring(0, colon).lowercase() else null
            return if (name == null || name !in BooruQuery.METATAGS) {
                BooruQueryToken(operator, null, body)
            } else {
                BooruQueryToken(operator, name, body.substring(colon + 1))
            }
        }
    }
}

/** A search typed so far: the [done] tokens, followed by whitespace, and the [rest] still being typed. */
data class BooruTypedQuery(val done: List<String>, val rest: String)

/** Building and reading booru searches, the way XTA's search field and feeds do. */
object BooruQuery {
    /** Metatag names the engines share ([BooruMetatags.of] says which each understands). */
    val METATAGS: Set<String> = setOf(
        "age", "date", "fav", "favcount", "filetype", "height", "id", "md5", "mpixels", "order", "ordfav",
        "parent", "pool", "rating", "ratio", "score", "sort", "source", "status", "tagcount", "user", "width",
    )

    private val LAST_SPACE = Regex("(?U)\\s(?=\\S*$)")
    private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]")

    /** The tokens of a typed search, split on whitespace. */
    fun tokens(raw: String): List<String> = raw.trim().split(WHITESPACE).filter { it.isNotEmpty() }

    fun text(tokens: Iterable<String>): String = tokens.joinToString(" ")

    /** [tags] followed by [added]: a repeat is skipped, and a token takes the place of the one it competes with ([BooruQueryToken.slot]). */
    fun append(tags: List<String>, added: Iterable<String>): List<String> = added.fold(tags, ::appendOne)

    /** A search's canonical, lower-case form, as saved searches and history keep it; null when nothing is left. */
    fun normalise(raw: String): String? = append(emptyList(), tokens(raw.lowercase())).takeIf { it.isNotEmpty() }?.let(::text)

    /** A history entry's identity: `a b` and `b a` are one search. */
    fun tagSetKey(query: String): String = text(tokens(query.lowercase()).toSet().sorted())

    /** Text typed into the tag field: each token followed by whitespace is done; what follows the last whitespace is still being typed. */
    fun splitInput(text: String): BooruTypedQuery {
        val space = LAST_SPACE.find(text) ?: return BooruTypedQuery(emptyList(), text)
        return BooruTypedQuery(tokens(text.substring(0, space.range.first)), text.substring(space.range.last + 1))
    }

    /** What to ask a host to complete for [input], without its operator; null while it is under two letters or names a metatag. */
    fun suggestionPrefix(input: String): String? {
        val token = BooruQueryToken.parse(input.trim())
        return if (token.metatag != null || token.value.length < 2) null else token.value.lowercase()
    }

    /**
     * Where in [text] a word starting with [typed] begins: at the start, or after a character that
     * is neither letter nor digit (`hatsune_miku`, `fate/zero`); null when none does. For bolding the match.
     */
    fun matchStart(text: String, typed: String): Int? {
        val lower = text.lowercase()
        // Lower-casing can change the length (`İ`), and then the positions would not hold.
        if (typed.isEmpty() || lower.length != text.length) return null
        return generateSequence(lower.indexOf(typed).takeIf { it >= 0 }) { start -> lower.indexOf(typed, start + 1).takeIf { it >= 0 } }
            .firstOrNull { start -> start == 0 || WORD_BREAK.matches(lower.substring(start - 1, start)) }
    }

    /** [suggestion] with the operator typed in [input] carried over (`-blu` gives `-blue_eyes`). */
    fun tokenFor(input: String, suggestion: String): String = BooruTagOperator.of(input.trim()).prefix + suggestion

    /** [input] with [operator] put in front, or taken off when it is already there. */
    fun toggleOperator(input: String, operator: BooruTagOperator): String {
        val current = BooruTagOperator.of(input)
        val body = input.substring(current.prefix.length)
        return if (current == operator) body else operator.prefix + body
    }

    /** The tags [posts] share most, those [query] already names left out; a tag on one post only is not shared. */
    fun relatedTags(posts: List<BooruPost>, query: List<String>, limit: Int = 12): List<String> {
        val taken = namedTags(query)
        return posts.flatMap { it.tags }.filterNot { it.lowercase() in taken }
            .groupingBy { it }.eachCount().filterValues { it > 1 }.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key }
    }

    /** The host's related [tags], those [query] already names, included or excluded, left out. */
    fun unsearchedTags(tags: List<String>, query: List<String>, limit: Int = 12): List<String> {
        val taken = namedTags(query)
        return tags.filterNot { it.lowercase() in taken }.take(limit)
    }

    /** The tags [query] asks to include: no operator, no metatag. */
    fun plainTags(query: List<String>): List<String> = query.map(BooruQueryToken::parse)
        .filter { it.operator == BooruTagOperator.NONE && it.metatag == null }.map { it.value }

    /** Highest score first, in [engine]'s words. */
    fun scoreOrderMetatag(engine: BooruEngine): String = if (engine == BooruEngine.GELBOORU_V2) "sort:score" else "order:score"

    /** Gelbooru writes "either" as `{a ~ b}`, so a leading `~` only works elsewhere. */
    fun supportsEither(engine: BooruEngine): Boolean = engine != BooruEngine.GELBOORU_V2

    /** The search that reads pool [poolId] in its own order. */
    fun poolQuery(poolId: String): String = "ordpool:$poolId"

    /** The posts sharing [post]'s parent, the parent included where the host lists it. */
    fun familyQuery(post: BooruPost): String = "parent:${post.parentId ?: post.id}"

    private fun appendOne(tags: List<String>, raw: String): List<String> {
        val token = raw.trim()
        if (token.isEmpty() || token in tags) return tags
        val parsed = BooruQueryToken.parse(token)
        if (parsed.value.isEmpty()) return tags
        return tags.filter { BooruQueryToken.parse(it).slot != parsed.slot } + token
    }

    private fun namedTags(query: List<String>): Set<String> = query.map { BooruQueryToken.parse(it).value.lowercase() }.toSet()
}
