package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl

/**
 * Reads the HTML pages a publication serves when its JSON does not answer, as
 * XTA does: the home page's Open Graph tags, a Beehiiv site's Remix state, an
 * article's JSON-LD, and the `window._preloads` of `/recommendations`. A page
 * of another shape gives null or nothing, never a throw.
 */
object SubstackPages {
    private val titleTag = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
    private val preloads = Regex("window\\._preloads\\s*=\\s*JSON\\.parse\\(\"((?:\\\\.|[^\"\\\\])*)\"\\)")
    private val jsonLd = Regex(
        "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** A publication from its home page's Open Graph tags, when a custom domain no longer serves Substack's JSON. */
    fun publicationOfHomepage(html: String, base: HttpUrl): SubstackPublication? {
        val name = titleOf(meta(html, "og:site_name"), meta(html, "og:title") ?: titleTag.find(html)?.groupValues?.get(1)?.trim())
        if (name == null || SubstackHosts.nameLooksGeneric(name)) return null
        return SubstackPublication(
            subdomain = SubstackHosts.guessedId(base),
            baseUrl = SubstackHosts.origin(base),
            name = name,
            logoUrl = meta(html, "og:image")?.takeIf { it.isNotEmpty() },
        )
    }

    /** A Beehiiv site's name, logo and blurb from its Remix state, else its Open Graph tags when the page is Beehiiv's. */
    fun publicationOfBeehiivHomepage(html: String, base: HttpUrl): SubstackPublication? =
        publicationOfBeehiivRemix(remixContext(html), base)
            ?: if (looksLikeBeehiiv(html)) publicationOfHomepage(html, base) else null

    /** The publication in a Beehiiv site's Remix state (`state.loaderData.root.publication`), when it has a real name. */
    fun publicationOfBeehiivRemix(remix: J, base: HttpUrl): SubstackPublication? {
        val publication = remix["state"]["loaderData"]["root"]["publication"]
        if (publication.raw !is JsonObject) return null
        val name = publication["name"].string?.trim()
        if (name.isNullOrEmpty() || SubstackHosts.nameLooksGeneric(name)) return null
        val logo = SubstackText.nonBlank(publication["logo"]["url"].string) ?: SubstackText.nonBlank(publication["thumbnail"]["url"].string)
        return SubstackPublication(
            subdomain = SubstackHosts.guessedId(base),
            baseUrl = SubstackHosts.origin(base),
            name = name,
            description = SubstackText.nonBlank(publication["description"].string),
            logoUrl = logo,
        )
    }

    /** A Beehiiv-shaped `/p/{slug}` page: its Remix state, else its JSON-LD article, else its Open Graph article tags. */
    fun beehiivPost(html: String, slug: String, publicationBaseUrl: String, publicationName: String): SubstackPost? =
        beehiivRemixPost(remixContext(html), slug, publicationBaseUrl, publicationName)
            ?: jsonLdPost(J(jsonLdArticle(html)), slug, publicationBaseUrl, publicationName)
            ?: openGraphPost(html, slug, publicationBaseUrl, publicationName)

    /** The post in a Beehiiv page's Remix state, under its `routes/p/$slug` route or any route's `post`, when it is this [slug]. */
    fun beehiivRemixPost(remix: J, slug: String, publicationBaseUrl: String, publicationName: String): SubstackPost? {
        val loader = remix["state"]["loaderData"]
        val routes = (loader.raw as? JsonObject) ?: return null
        val named = loader["routes/p/\$slug"]["post"]
        val post = if (named.raw is JsonObject) named else routes.values.map { J(it)["post"] }.firstOrNull { it.raw is JsonObject } ?: return null
        val postSlug = post["slug"].string?.trim() ?: slug
        if (postSlug != slug) return null
        return SubstackParser.beehiivPost(post, publicationBaseUrl, publicationName)
    }

    /** The recommendations a publication's `/recommendations` page preloads. */
    fun recommendations(html: String): List<SubstackRecommendation> =
        preloadsJson(html)?.let { SubstackParser.recommendations(J.parse(it)["recommendations"]) }.orEmpty()

    /** The JSON a Substack page hydrates through `window._preloads = JSON.parse("…")`. */
    fun preloadsJson(html: String): String? {
        val literal = preloads.find(html)?.groupValues?.get(1) ?: return null
        return J.parse("\"$literal\"").string?.takeIf { it.isNotEmpty() }
    }

    /** What Beehiiv (and other Remix) sites hydrate as `window.__remixContext`. */
    fun remixContext(html: String): J {
        val at = html.indexOf("window.__remixContext")
        if (at < 0) return J(null)
        return jsonObjectAt(html, html.indexOf('{', at))?.let(J::parse) ?: J(null)
    }

    /** The `{…}` that starts at [start], aware of strings, so a brace inside a value does not end it early. */
    fun jsonObjectAt(source: String, start: Int): String? {
        if (start < 0 || start >= source.length || source[start] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until source.length) {
            val c = source[i]
            when {
                inString && escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                inString -> if (c == '"') inString = false
                c == '"' -> inString = true
                c == '{' -> depth++
                c == '}' -> if (--depth == 0) return source.substring(start, i + 1)
            }
        }
        return null
    }

    /** Whether a page names Beehiiv anywhere, as its sites do in their footer and assets. */
    fun looksLikeBeehiiv(html: String): Boolean = "beehiiv" in html.lowercase()

    /** A JSON-LD article; a field of another shape is left out, and no title lets the next reader try. */
    private fun jsonLdPost(ld: J, slug: String, publicationBaseUrl: String, publicationName: String): SubstackPost? {
        fun text(value: J) = SubstackText.nonBlank(value.string)
        val title = text(ld["headline"]) ?: text(ld["name"]) ?: return null
        return SubstackPost(
            id = slug,
            title = title,
            slug = slug,
            publicationBaseUrl = publicationBaseUrl,
            publicationName = publicationName,
            subtitle = text(ld["description"]),
            postDate = ld["datePublished"].string,
            canonicalUrl = text(ld["url"]) ?: "${SubstackHosts.originOf(publicationBaseUrl)}/p/$slug",
            coverImage = text(ld["image"]["url"]) ?: text(ld["image"]),
            audience = if (ld["isAccessibleForFree"].boolean == false) "only_paid" else "everyone",
            authorName = text(ld["author"]["name"]),
        )
    }

    private fun openGraphPost(html: String, slug: String, publicationBaseUrl: String, publicationName: String): SubstackPost? {
        val title = meta(html, "og:title")?.trim()
        if (meta(html, "og:type")?.lowercase() != "article" || title.isNullOrEmpty()) return null
        return SubstackPost(
            id = slug,
            title = title,
            slug = slug,
            publicationBaseUrl = publicationBaseUrl,
            publicationName = publicationName,
            subtitle = meta(html, "og:description"),
            postDate = meta(html, "article:published_time"),
            canonicalUrl = meta(html, "og:url") ?: "${SubstackHosts.originOf(publicationBaseUrl)}/p/$slug",
            coverImage = meta(html, "og:image")?.takeIf { it.isNotEmpty() },
            audience = "everyone",
            authorName = meta(html, "article:author")?.takeIf { it.isNotEmpty() },
        )
    }

    private fun jsonLdArticle(html: String): JsonElement? =
        jsonLd.findAll(html).map { J.parse(it.groupValues[1]) }.firstOrNull { ld ->
            ld.raw is JsonObject && typeOf(ld["@type"]).lowercase() in setOf("article", "newsarticle")
        }?.raw

    private fun typeOf(value: J): String = when (val raw = value.raw) {
        null, is JsonNull -> ""
        is JsonPrimitive -> raw.content
        else -> raw.toString()
    }

    /** A `<meta property=… content=…>` value, attributes in either order. */
    private fun meta(html: String, property: String): String? {
        val name = Regex.escape(property)
        val q = "[\"']"
        return Regex("property=$q$name$q[^>]*content=$q([^\"']+)$q", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()
            ?: Regex("content=$q([^\"']+)$q[^>]*property=$q$name$q", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()
    }

    /** The site name unless it is just "Substack", else the first part of the title that is not "Home" or "Substack". */
    private fun titleOf(siteName: String?, title: String?): String? {
        if (!siteName.isNullOrEmpty() && siteName.lowercase() != "substack") return siteName
        val raw = title?.trim()
        if (raw.isNullOrEmpty()) return null
        val parts = raw.split('|').map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() != "home" && it.lowercase() != "substack" }
        return parts.firstOrNull() ?: raw.takeUnless { SubstackHosts.nameLooksGeneric(it) }
    }
}
