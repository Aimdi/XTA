package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Posts and publications as XTA writes them into its preferences (likes,
 * saves, the feed it remembers, the follows it kept before they had a table):
 * `SubstackPost.toJson` / `fromSnapshot` and `SubstackPublication.toJson`. A
 * snapshot never carries a body; the reader reads it again.
 */
object SubstackPostJson {
    fun encode(post: SubstackPost): JsonObject = buildJsonObject {
        put("id", post.id)
        put("title", post.title)
        put("subtitle", post.subtitle)
        put("description", post.description)
        put("slug", post.slug)
        put("post_date", post.postDate)
        put("canonical_url", post.canonicalUrl)
        put("cover_image", post.coverImage)
        put("audience", post.audience)
        put("authorName", post.authorName)
        put("type", post.type)
        put("hasVideoUpload", post.hasVideoUpload)
        put("podcast_url", post.audioUrl)
        put("reaction_count", post.reactionCount)
        put("comment_count", post.commentCount)
        put("wordcount", post.wordCount)
        put("publicationBaseUrl", post.publicationBaseUrl)
        put("publicationName", post.publicationName)
    }

    /** A post from a snapshot, as XTA reads one back; its older camel-case names too. */
    fun decode(json: J): SubstackPost = SubstackPost(
        id = SubstackParser.idOf(json["id"]) ?: json["slug"].string.orEmpty(),
        title = json["title"].string.orEmpty(),
        subtitle = json["subtitle"].string,
        description = json["description"].string,
        slug = json["slug"].string.orEmpty(),
        postDate = json["post_date"].string ?: json["postDate"].string,
        canonicalUrl = json["canonical_url"].string ?: json["canonicalUrl"].string,
        coverImage = json["cover_image"].string ?: json["coverImage"].string,
        audience = json["audience"].string,
        authorName = json["authorName"].string,
        type = json["type"].string,
        hasVideoUpload = json["hasVideoUpload"].boolean == true,
        audioUrl = json["podcast_url"].string ?: json["audioUrl"].string,
        reactionCount = SubstackParser.count(json["reaction_count"]),
        commentCount = SubstackParser.count(json["comment_count"]),
        wordCount = SubstackParser.count(json["wordcount"]),
        publicationBaseUrl = json["publicationBaseUrl"].string.orEmpty(),
        publicationName = json["publicationName"].string.orEmpty(),
    )

    /** A preference's list of posts; entries without an id or slug, and anything that is no list, are left out. */
    fun list(text: String?): List<SubstackPost> =
        J.parse(text.orEmpty()).list.filter { it.raw is JsonObject }.map(::decode).filter { it.id.isNotEmpty() && it.slug.isNotEmpty() }

    fun listText(posts: List<SubstackPost>): String = JsonArray(posts.map(::encode)).toString()

    /** The publications XTA kept in `plugin.substack.publications` before follows had a table. */
    fun publications(text: String?): List<SubstackPublication> =
        J.parse(text.orEmpty()).list.filter { it.raw is JsonObject }.map { json ->
            SubstackPublication(
                subdomain = json["subdomain"].string.orEmpty(),
                baseUrl = json["baseUrl"].string.orEmpty(),
                name = json["name"].string ?: json["subdomain"].string.orEmpty(),
                description = json["description"].string,
                logoUrl = json["logoUrl"].string,
            )
        }.filter { it.subdomain.isNotEmpty() && it.baseUrl.isNotEmpty() }

    /** A preference's list of strings, as XTA's read marks are kept. */
    fun strings(text: String?): List<String> = J.parse(text.orEmpty()).list.mapNotNull { it.string?.takeIf(String::isNotEmpty) }

    fun stringsText(values: List<String>): String = JsonArray(values.map(::JsonPrimitive)).toString()
}
