package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.min
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Reads Substack's JSON as XTA's substack_models.dart does. Every field may
 * be missing or of another shape: such a field is null, and a row without a
 * title or slug is left out rather than failing its page.
 */
object SubstackParser {
    /** A post from a listing or from `/api/v1/posts/{slug}`; the body only when [includeBody]. */
    fun post(json: J, publicationBaseUrl: String, publicationName: String, includeBody: Boolean = true): SubstackPost = SubstackPost(
        id = idOf(json["id"]) ?: json["slug"].string ?: "",
        title = json["title"].string ?: "",
        slug = json["slug"].string ?: "",
        publicationBaseUrl = publicationBaseUrl,
        publicationName = publicationName,
        subtitle = json["subtitle"].string,
        description = json["description"].string,
        postDate = json["post_date"].string,
        canonicalUrl = json["canonical_url"].string,
        coverImage = json["cover_image"].string,
        bodyHtml = if (includeBody) json["body_html"].string else null,
        audience = json["audience"].string,
        authorName = json["publishedBylines"][0]["name"].string,
        type = json["type"].string,
        // Substack has moved this field around, so its presence is what counts.
        hasVideoUpload = json["videoUpload"].exists || json["video_upload"].exists,
        audioUrl = json["podcast_url"].string,
        reactionCount = count(json["reaction_count"]) ?: reactionSum(json["reactions"]),
        commentCount = count(json["comment_count"]),
        wordCount = count(json["wordcount"]),
    )

    /** Rows of a listing that are posts, in order. */
    fun posts(rows: List<J>, publicationBaseUrl: String, publicationName: (J) -> String): List<SubstackPost> =
        rows.filter { it.raw is JsonObject }
            .map { post(it, publicationBaseUrl, publicationName(it), includeBody = false) }
            .filter { it.title.isNotEmpty() && it.slug.isNotEmpty() }

    /**
     * The publication that wrote [post], from its byline, at the host that
     * served it ([fallbackBase]): an advertised custom domain can point at a
     * site that is no longer Substack's.
     */
    fun publicationOfPost(post: J, fallbackBase: HttpUrl): SubstackPublication {
        val publication = post["publishedBylines"][0]["publicationUsers"][0]["publication"]
        val subdomain = publication["subdomain"].string?.trim().orEmpty()
            .takeUnless { it.isEmpty() || it.lowercase() == "www" } ?: SubstackHosts.subdomainOf(fallbackBase.host)
        val name = publication["name"].string?.trim()?.takeUnless { it.isEmpty() || it.lowercase() == "www" } ?: subdomain
        return SubstackPublication(
            subdomain = subdomain,
            baseUrl = SubstackHosts.origin(fallbackBase),
            name = name,
            description = publication["hero_text"].string,
            logoUrl = publication["logo_url"].string,
        )
    }

    /** The primary publication of a public profile (`/api/v1/user/{handle}/public_profile`). */
    fun publicationOfProfile(json: J): SubstackPublication? =
        discovered(json["primaryPublication"]) ?: json["publicationUsers"].list.firstNotNullOfOrNull { discovered(it["publication"]) }

    /** A publication object from search, a category or the reader feed; null when nothing names it. */
    fun discovered(json: J): SubstackPublication? {
        if (json.raw !is JsonObject) return null
        val subdomain = json["subdomain"].string?.trim().orEmpty()
        val custom = json["custom_domain"].string?.trim()
        val baseRaw = json["base_url"].string?.trim() ?: json["hostname"].string?.trim()
        val baseUrl = when {
            !baseRaw.isNullOrEmpty() -> webOrigin(baseRaw) ?: return null
            !custom.isNullOrEmpty() -> webOrigin(custom) ?: return null
            subdomain.isNotEmpty() && SubstackHosts.isHandle(subdomain) -> "https://$subdomain.substack.com"
            else -> return null
        }
        val fromHost = SubstackHosts.subdomainOf(SubstackHosts.hostOf(baseUrl))
        return SubstackPublication(
            subdomain = subdomain.ifEmpty { fromHost },
            baseUrl = baseUrl,
            name = json["name"].string?.trim()?.takeIf { it.isNotEmpty() } ?: subdomain.ifEmpty { fromHost },
            description = json["hero_text"].string?.trim() ?: json["copyright"].string?.trim(),
            logoUrl = json["logo_url"].string ?: json["logo_url_wide"].string,
        )
    }

    /** One item of `/api/v1/reader/feed`. */
    fun note(item: J): SubstackNote {
        val comment = item["comment"]
        val handle = comment["handle"].string?.trim()
        val id = idOf(comment["id"]) ?: item["entity_key"].string ?: ""
        val publication = (item["publication"].takeIf { it.exists } ?: comment["user_primary_publication"]).let(::discovered)
        return SubstackNote(
            id = id,
            body = comment["body"].string?.trim().orEmpty(),
            authorName = comment["name"].string,
            authorHandle = handle,
            authorPhotoUrl = comment["photo_url"].string,
            at = SubstackText.instant(comment["date"].string),
            reactionCount = count(comment["reaction_count"]),
            imageUrl = noteImage(comment["attachments"].list),
            url = if (!handle.isNullOrEmpty() && id.isNotEmpty()) "https://substack.com/@$handle/note/c-$id" else null,
            publication = publication,
        )
    }

    /** The first image attachment that has an address; XTA keeps the last one asked when none has. */
    private fun noteImage(attachments: List<J>): String? {
        var image: String? = null
        for (attachment in attachments.filter { it["type"].string == "image" }) {
            image = attachment["imageUrl"].string
            if (!image.isNullOrEmpty()) break
        }
        return image
    }

    /** A category; an id that is no JSON number is 0, which the client leaves out. */
    fun category(json: J): SubstackCategory = SubstackCategory(
        id = number(json["id"])?.toLong() ?: 0,
        name = json["name"].string?.trim().orEmpty(),
        slug = json["slug"].string?.trim().orEmpty(),
    )

    /** The recommendations `/recommendations` preloads, each publication once. */
    fun recommendations(json: J): List<SubstackRecommendation> {
        val seen = HashSet<String>()
        return json.list.mapNotNull { item ->
            val publication = discovered(item["recommendedPublication"].takeIf { it.exists } ?: item["recommended_publication"])
            if (publication == null || publication.subdomain.isEmpty() || !seen.add(publication.id)) return@mapNotNull null
            SubstackRecommendation(publication, SubstackText.nonBlank(item["description"].string))
        }
    }

    /** An author's recommendations first, then search hits, never [seed] itself, at most [limit]. */
    fun mergeSimilar(
        seed: SubstackPublication,
        recommended: List<SubstackRecommendation> = emptyList(),
        searched: List<SubstackPublication> = emptyList(),
        limit: Int = 12,
    ): List<SubstackRecommendation> {
        val seen = hashSetOf(seed.id)
        return (recommended + searched.map { SubstackRecommendation(it) }).filter { seen.add(it.publication.id) }.take(limit)
    }

    /**
     * The comments endpoint's tree in reading order. A deleted comment is
     * skipped and its replies keep their place; replies deeper than [maxDepth]
     * stay at that depth, so a long exchange stops indenting, not reading
     * (BUG-01). Anything that does not fit is skipped, never thrown.
     */
    fun comments(json: J, maxDepth: Int = 8): List<SubstackComment> {
        class Pending(val node: JsonElement?, val depth: Int, val parent: String?)
        val out = ArrayList<SubstackComment>()
        val root = if (json.raw is JsonObject) json["comments"] else json
        val pending = ArrayDeque(root.list.reversed().map { Pending(it.raw, 0, null) })
        val visited = Collections.newSetFromMap(IdentityHashMap<JsonElement, Boolean>())
        val ids = HashSet<String>()
        while (pending.isNotEmpty() && visited.size < 20_000) {
            val entry = pending.removeLast()
            if (entry.node !is JsonObject || !visited.add(entry.node)) continue
            val node = J(entry.node)
            val body = node["body"].string?.trim().orEmpty()
            val id = idOf(node["id"]).orEmpty()
            val visible = id.isNotEmpty() && body.isNotEmpty() && ids.add(id)
            if (visible) {
                out += SubstackComment(
                    id = id,
                    body = body,
                    author = node["name"].string,
                    parentId = entry.parent ?: idOf(node["parent_id"]) ?: idOf(node["parent"]["id"]),
                    at = SubstackText.instant(node["date"].string),
                    depth = entry.depth,
                )
            }
            val depth = min(entry.depth + if (visible) 1 else 0, maxDepth)
            node["children"].list.reversed().forEach { pending.addLast(Pending(it.raw, depth, if (visible) id else entry.parent)) }
        }
        return out
    }

    /** A post from Beehiiv's `/posts` listing, or the post a Beehiiv page carries. */
    fun beehiivPost(json: J, publicationBaseUrl: String, publicationName: String): SubstackPost {
        val slug = json["slug"].string?.trim().orEmpty()
        val premium = json["is_premium"].boolean == true || json["audience"].string?.lowercase() == "premium"
        val origin = SubstackHosts.originOf(publicationBaseUrl)
        return SubstackPost(
            id = json["id"].raw?.takeUnless { it is JsonNull }?.let { (it as? JsonPrimitive)?.content ?: it.toString() } ?: slug,
            title = json["web_title"].string?.trim() ?: json["title"].string?.trim() ?: "",
            slug = slug,
            publicationBaseUrl = publicationBaseUrl,
            publicationName = publicationName,
            subtitle = SubstackText.nonBlank(json["web_subtitle"].string),
            postDate = json["override_scheduled_at"].string ?: json["created_at"].string,
            canonicalUrl = if (slug.isEmpty()) null else "$origin/p/$slug",
            coverImage = SubstackText.nonBlank(json["image_url"].string),
            audience = if (premium) "only_paid" else "everyone",
            authorName = SubstackText.nonBlank(json["authors"][0]["name"].string),
        )
    }

    /** Beehiiv's public `/posts` listing: `{"posts": [...], "pagination": {...}}`. */
    fun looksLikeBeehiivPosts(json: J): Boolean {
        val posts = json["posts"].raw as? JsonArray ?: return false
        if (json["pagination"].raw !is JsonObject) return false
        if (posts.isEmpty()) return true
        val first = J(posts.first())
        return first.raw is JsonObject && first["slug"].exists && (first["web_title"].exists || first["title"].exists)
    }

    /**
     * A count as Substack sends it: a number or digits, finite, never below
     * zero. Null when there is none, which is not the same as zero.
     */
    internal fun count(json: J): Long? = json.double?.takeIf { it.isFinite() }?.toLong()?.coerceIn(0, 1L shl 53)

    /** Older payloads carry no total reaction count, only the count per emoji. */
    private fun reactionSum(json: J): Long? {
        val reactions = json.raw as? JsonObject
        if (reactions.isNullOrEmpty()) return null
        return reactions.values.sumOf { count(J(it)) ?: 0 }
    }

    /** An id as Substack sends it: a string as it is, a whole number in digits; anything else is none. */
    internal fun idOf(json: J): String? {
        val primitive = json.raw as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return if (primitive.isString) primitive.content else primitive.content.takeIf { it.toLongOrNull() != null }
    }

    /** A JSON number, not a string holding one. */
    internal fun number(json: J): Double? = (json.raw as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.content?.toDoubleOrNull()

    private fun webOrigin(raw: String): String? = (if ("://" in raw) raw else "https://$raw").toHttpUrlOrNull()?.let(SubstackHosts::origin)
}
