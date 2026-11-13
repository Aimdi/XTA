package com.aimdi.xtanative.networks.booru

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where a request goes and who it is made as. [login] and [apiKey] read: Danbooru, e621 and
 * Gelbooru answer more (longer searches, restricted posts) to a reader who gives them; nothing is
 * ever written with them.
 */
data class BooruSite(val engine: BooruEngine, val host: String, val login: String = "", val apiKey: String = "") {
    /** The credentials as [engine] expects them named. Every engine needs both halves, so half a login is never sent. */
    val auth: Map<String, String>
        get() = if (login.isEmpty() || apiKey.isEmpty()) {
            emptyMap()
        } else {
            mapOf((if (engine == BooruEngine.GELBOORU_V2) "user_id" else "login") to login, keyParameter to apiKey)
        }

    private val keyParameter: String get() = if (engine == BooruEngine.MOEBOORU) "password_hash" else "api_key"

    /** [path] on the site's API host, under any path the site itself has, with [query] and then the credentials. */
    fun endpoint(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val base = requireNotNull(BooruHosts.requestHost(host).toHttpUrlOrNull()) { "Not a booru host: $host" }
        return base.newBuilder()
            .encodedPath(base.encodedPath.trimEnd('/') + path)
            .query(null)
            .apply { (query + auth).forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
    }

    /** Gelbooru's API: one script told what to do by its parameters. */
    fun dapi(kind: String, query: Map<String, String> = emptyMap()): HttpUrl =
        endpoint("/index.php", linkedMapOf("page" to "dapi", "s" to kind, "q" to "index", "json" to "1") + query)

    override fun toString(): String = "BooruSite(engine=$engine, host=$host${if (auth.isEmpty()) "" else ", with credentials"})"
}

/** The URLs XTA reads each engine through. Pure, so every engine's shape is checked without a network. */
object BooruEndpoints {
    /** The query parameters that carry credentials, on any engine. */
    val CREDENTIAL_PARAMETERS: Set<String> = setOf("login", "user_id", "api_key", "password_hash")

    private val CREDENTIAL_VALUES = Regex("([?&](?:${CREDENTIAL_PARAMETERS.joinToString("|")})=)[^&\\s]*")

    /** [text] with every credential's value masked, so an error that names its request URL can be shown or logged. */
    fun redact(text: String): String = CREDENTIAL_VALUES.replace(text) { "${it.groupValues[1]}***" }

    /** A page of posts for [tags]: Danbooru, e621 and Moebooru count pages from 1, Gelbooru's `pid` from 0. */
    fun posts(site: BooruSite, tags: List<String>, page: Int, limit: Int): HttpUrl {
        val query = buildMap {
            put("limit", "$limit")
            BooruQuery.text(tags).takeIf { it.isNotEmpty() }?.let { put("tags", it) }
        }
        return when (site.engine) {
            BooruEngine.DANBOORU, BooruEngine.E621 -> site.endpoint("/posts.json", query + ("page" to "$page"))
            BooruEngine.MOEBOORU -> site.endpoint("/post.json", query + ("page" to "$page"))
            BooruEngine.GELBOORU_V2 -> site.dapi("post", query + ("pid" to "${page - 1}"))
        }
    }

    /** Tags whose names start with [prefix], most used first: each engine has its own wildcard. */
    fun tagPrefix(site: BooruSite, prefix: String, limit: Int): HttpUrl = when (site.engine) {
        BooruEngine.DANBOORU, BooruEngine.E621 -> site.endpoint(
            "/tags.json",
            linkedMapOf("search[name_matches]" to "$prefix*", "search[order]" to "count", "limit" to "$limit"),
        )
        BooruEngine.MOEBOORU -> site.endpoint("/tag.json", linkedMapOf("name" to "$prefix*", "order" to "count", "limit" to "$limit"))
        BooruEngine.GELBOORU_V2 -> site.dapi("tag", linkedMapOf("limit" to "$limit", "name_pattern" to "$prefix%"))
    }

    /**
     * The site's own completion of [query]: tags by any word of their name, by alias, by a name in
     * another language and past a typo, most used first. Danbooru keeps one; null elsewhere.
     */
    fun autocomplete(site: BooruSite, query: String, limit: Int): HttpUrl? = when (site.engine) {
        BooruEngine.DANBOORU -> site.endpoint(
            "/autocomplete.json",
            linkedMapOf("search[query]" to query, "search[type]" to "tag_query", "limit" to "$limit"),
        )
        else -> null
    }

    /** Kinds and post counts of [tags] in one request; null without tags, or where the host looks tags up one at a time (Moebooru). */
    fun tagList(site: BooruSite, tags: List<String>): HttpUrl? {
        if (tags.isEmpty()) return null
        val limit = "${tags.size}"
        return when (site.engine) {
            BooruEngine.DANBOORU -> site.endpoint(
                "/tags.json",
                linkedMapOf("search[name_comma]" to tags.joinToString(","), "only" to "name,category,post_count", "limit" to limit),
            )
            BooruEngine.E621 -> site.endpoint("/tags.json", linkedMapOf("search[name]" to tags.joinToString(","), "limit" to limit))
            BooruEngine.GELBOORU_V2 -> site.dapi("tag", linkedMapOf("names" to tags.joinToString(" "), "limit" to limit))
            BooruEngine.MOEBOORU -> null
        }
    }

    /**
     * Kinds and counts of post [postId]'s [tags] in one request. Moebooru cannot count a list of
     * tags, but its post can name their kinds.
     */
    fun tagInfo(site: BooruSite, postId: String, tags: List<String>): HttpUrl? = when {
        tags.isEmpty() -> null
        site.engine == BooruEngine.MOEBOORU -> site.endpoint(
            "/post.json",
            linkedMapOf("tags" to "id:$postId", "api_version" to "2", "include_tags" to "1", "limit" to "1"),
        )
        else -> tagList(site, tags)
    }

    /** Tags the host finds alongside [tags]: Danbooru for a whole search, e621 for one tag; null elsewhere. */
    fun relatedTags(site: BooruSite, tags: List<String>): HttpUrl? = when {
        tags.isEmpty() -> null
        site.engine == BooruEngine.DANBOORU ->
            site.endpoint("/related_tag.json", linkedMapOf("search[query]" to tags.joinToString(" "), "limit" to "25"))
        site.engine == BooruEngine.E621 && tags.size == 1 ->
            site.endpoint("/tags.json", linkedMapOf("search[name]" to tags.single(), "limit" to "1"))
        else -> null
    }

    /** The artist entries matching [name]: Danbooru and e621 keep them; null elsewhere. */
    fun artist(site: BooruSite, name: String): HttpUrl? = when (site.engine) {
        BooruEngine.DANBOORU -> site.endpoint(
            "/artists.json",
            linkedMapOf("search[any_name_matches]" to name, "only" to "name,is_deleted,other_names,urls", "limit" to "10"),
        )
        BooruEngine.E621 -> site.endpoint("/artists.json", linkedMapOf("search[name]" to name, "limit" to "1"))
        else -> null
    }

    /** The pools post [postId] is in; only Danbooru's are read. */
    fun postPools(site: BooruSite, postId: String): HttpUrl? = when (site.engine) {
        BooruEngine.DANBOORU -> site.endpoint("/pools.json", linkedMapOf("search[post_ids_include_all]" to postId, "limit" to "20"))
        else -> null
    }

    /** The translation notes on post [postId]; Danbooru and e621 keep them alike. */
    fun notes(site: BooruSite, postId: String): HttpUrl? = when (site.engine) {
        BooruEngine.DANBOORU, BooruEngine.E621 ->
            site.endpoint("/notes.json", linkedMapOf("search[post_id]" to postId, "limit" to "200"))
        else -> null
    }

    /**
     * The wiki page for [tag]. Moebooru's is a title search, not a lookup: many titles holding the
     * tag can sort before it, so as many as a page holds are asked for and the exact one is picked.
     */
    fun wiki(site: BooruSite, tag: String): HttpUrl? = when (site.engine) {
        BooruEngine.DANBOORU, BooruEngine.E621 -> site.endpoint("/wiki_pages.json", linkedMapOf("search[title]" to tag, "limit" to "1"))
        BooruEngine.MOEBOORU -> site.endpoint("/wiki.json", linkedMapOf("query" to tag, "limit" to "100"))
        BooruEngine.GELBOORU_V2 -> null
    }

    /** A post's comments, up to a hundred; Gelbooru only answers them in XML, which XTA does not read. */
    fun comments(site: BooruSite, postId: String): HttpUrl? = when (site.engine) {
        BooruEngine.DANBOORU -> site.endpoint(
            "/comments.json",
            linkedMapOf(
                "group_by" to "comment",
                "search[post_id]" to postId,
                "limit" to "100",
                "only" to "id,created_at,score,body,is_deleted,creator[name]",
            ),
        )
        BooruEngine.E621 ->
            site.endpoint("/comments.json", linkedMapOf("group_by" to "comment", "search[post_id]" to postId, "limit" to "100"))
        BooruEngine.MOEBOORU -> site.endpoint("/comment.json", linkedMapOf("post_id" to postId))
        BooruEngine.GELBOORU_V2 -> null
    }

    /** The popular posts of [query]'s day, week or month; null where the engine keeps no such list (Gelbooru). */
    fun popular(site: BooruSite, query: BooruPopularQuery, page: Int, limit: Int): HttpUrl? {
        val date = query.date
        return when (site.engine) {
            BooruEngine.DANBOORU -> site.endpoint(
                "/explore/posts/popular.json",
                linkedMapOf("date" to "$date", "scale" to query.scale.wire, "page" to "$page", "limit" to "$limit"),
            )
            BooruEngine.E621 -> site.endpoint("/popular.json", linkedMapOf("date" to "$date", "scale" to query.scale.wire))
            BooruEngine.MOEBOORU -> site.endpoint(
                "/post/popular_by_${query.scale.wire}.json",
                buildMap {
                    if (query.scale != BooruPopularScale.MONTH) put("day", "${date.dayOfMonth}")
                    put("month", "${date.monthValue}")
                    put("year", "${date.year}")
                },
            )
            BooruEngine.GELBOORU_V2 -> null
        }
    }

    /** Gelbooru keeps no popular list; its highest-scored posts stand in. */
    fun hasPopularList(engine: BooruEngine): Boolean = engine != BooruEngine.GELBOORU_V2

    /** Only Danbooru pages through its popular list; the others send one page. */
    fun popularPages(engine: BooruEngine): Boolean = engine == BooruEngine.DANBOORU

    /** Only Danbooru's pools are read so far. */
    fun supportsPools(engine: BooruEngine): Boolean = engine == BooruEngine.DANBOORU

    fun supportsWiki(engine: BooruEngine): Boolean = engine != BooruEngine.GELBOORU_V2

    fun supportsComments(engine: BooruEngine): Boolean = engine != BooruEngine.GELBOORU_V2
}
