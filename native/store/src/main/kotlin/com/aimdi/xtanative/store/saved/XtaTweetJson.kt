package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.parse.XParser
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * An X post as XTA keeps it in Saved, likes and notes: the tweet JSON of
 * XTA's `TweetWithCard` (REST-style `id_str`, `full_text`, `user`,
 * `entities`, `extended_entities`, with `quotedStatusWithCard`,
 * `retweetedStatusWithCard`, `noteText` and a card whose `binding_values` is
 * a map). XTA Native keeps X posts the same way, so a backup moves between
 * the two apps unchanged and both read every saved post.
 */
object XtaTweetJson {
    private val twitterTime = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC)

    /** [post] as XTA's tweet JSON. */
    fun encode(post: XPost): JsonObject = buildJsonObject {
        put("id_str", post.id)
        post.createdAt?.let { put("created_at", it.toString()) }
        put("full_text", post.text)
        put("display_text_range", JsonArray(listOf(JsonPrimitive(post.displayStart), JsonPrimitive(post.displayEnd ?: post.text.codePointCount(0, post.text.length)))))
        put("user", user(post.author))
        put("entities", entities(post.entities))
        if (post.media.isNotEmpty()) put("extended_entities", buildJsonObject { put("media", JsonArray(post.media.mapIndexed { i, media -> media(post.id, i, media) })) })
        put("reply_count", post.replyCount)
        put("retweet_count", post.repostCount)
        put("quote_count", post.quoteCount)
        put("favorite_count", post.likeCount)
        post.viewCount?.let { put("viewCount", it) }
        post.replyToScreenName?.let { put("in_reply_to_screen_name", it) }
        post.conversationId?.let { put("conversationIdStr", it) }
        post.card?.let { put("card", card(it)) }
        post.quoted?.let {
            put("is_quote_status", true)
            put("quoted_status_id_str", it.id)
            put("quotedStatusWithCard", encode(it))
        }
        post.reposted?.let { put("retweetedStatusWithCard", encode(it)) }
    }

    /**
     * The post in XTA's tweet JSON [tweet], or null when it holds none. Read
     * through [XParser.post] by giving it the shape X's GraphQL answers have,
     * so a kept post reads exactly as a fetched one.
     */
    fun decode(tweet: J): XPost? = if (tweet.raw is JsonObject) XParser.post(J(graphql(tweet))) else null

    private fun graphql(tweet: J): JsonObject = buildJsonObject {
        tweet["id_str"].string?.let { put("rest_id", it) }
        put("legacy", legacy(tweet))
        put("core", wrap("user_results", wrap("result", wrap("legacy", tweet["user"].raw ?: JsonObject(emptyMap())))))
        tweet["noteText"].string?.let { text ->
            put("note_tweet", wrap("note_tweet_results", wrap("result", buildJsonObject {
                put("text", text)
                tweet["noteEntities"].raw?.let { put("entity_set", it) }
            })))
        }
        cardLegacy(tweet["card"])?.let { put("card", wrap("legacy", it)) }
        tweet["viewCount"].long?.let { put("views", wrap("count", JsonPrimitive(it.toString()))) }
        tweet["quotedStatusWithCard"].takeIf { it.raw is JsonObject }?.let { put("quoted_status_result", wrap("result", graphql(it))) }
    }

    /** The tweet as GraphQL's `legacy`: a Twitter-style time, alt text on its media, the repost and conversation as X sends them. */
    private fun legacy(tweet: J): JsonObject {
        val fields = (tweet.raw as JsonObject).toMutableMap()
        parseTime(tweet["created_at"].string)?.let { fields["created_at"] = JsonPrimitive(twitterTime.format(Instant.ofEpochMilli(it))) }
        val alts = tweet["mediaAltText"]
        if (alts.keys.isNotEmpty()) fields["extended_entities"] = withAlts(tweet["extended_entities"], alts)
        tweet["retweetedStatusWithCard"].takeIf { it.raw is JsonObject }?.let { fields["retweeted_status_result"] = wrap("result", graphql(it)) }
        tweet["conversationIdStr"].string?.let { fields["conversation_id_str"] = JsonPrimitive(it) }
        return JsonObject(fields)
    }

    private fun withAlts(entities: J, alts: J): JsonElement = buildJsonObject {
        put("media", JsonArray(entities["media"].list.map { item ->
            val alt = item["id_str"].string?.let { alts[it].string }
            val fields = (item.raw as? JsonObject).orEmpty()
            if (alt == null) JsonObject(fields) else JsonObject(fields + ("ext_alt_text" to JsonPrimitive(alt)))
        }))
    }

    /** XTA keeps a card's `binding_values` as a map; X sends, and the parser reads, a list of key and value. */
    private fun cardLegacy(card: J): JsonObject? {
        val fields = (card.raw as? JsonObject) ?: return null
        val values = card["binding_values"]
        if (values.raw !is JsonObject) return fields
        val list = values.keys.map { key -> buildJsonObject { put("key", key); put("value", values[key].raw ?: JsonObject(emptyMap())) } }
        return JsonObject(fields + ("binding_values" to JsonArray(list)))
    }

    private fun wrap(key: String, value: JsonElement) = JsonObject(mapOf(key to value))

    private fun user(user: XUser) = buildJsonObject {
        put("id_str", user.id)
        put("screen_name", user.screenName)
        put("name", user.name)
        user.avatarUrl?.let { put("profile_image_url_https", it) }
        put("verified", user.verified)
        put("protected", user.protected)
    }

    private fun entities(entities: List<XEntity>) = buildJsonObject {
        put("urls", JsonArray(entities.filterIsInstance<XEntity.Link>().map { link ->
            span(link) { put("url", link.url); put("expanded_url", link.expanded); put("display_url", link.display) }
        }))
        put("user_mentions", JsonArray(entities.filterIsInstance<XEntity.Mention>().map { span(it) { put("screen_name", it.screenName) } }))
        put("hashtags", JsonArray(entities.filterIsInstance<XEntity.Hashtag>().map { span(it) { put("text", it.tag) } }))
        put("symbols", JsonArray(entities.filterIsInstance<XEntity.Cashtag>().map { span(it) { put("text", it.symbol) } }))
    }

    private fun span(entity: XEntity, fields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject {
        fields()
        put("indices", JsonArray(listOf(JsonPrimitive(entity.start), JsonPrimitive(entity.end))))
    }

    /** XPost's media carry no id, so each gets one from its post and place: stable, and unique as XTA's records need. */
    private fun media(postId: String, index: Int, media: XMedia) = buildJsonObject {
        put("id_str", "$postId-$index")
        put("type", when (media.kind) { XMedia.Kind.PHOTO -> "photo"; XMedia.Kind.VIDEO -> "video"; XMedia.Kind.GIF -> "animated_gif" })
        put("media_url_https", media.url)
        media.shortUrl?.let { put("url", it) }
        if (media.width != null && media.height != null) {
            put("sizes", wrap("large", buildJsonObject { put("w", media.width); put("h", media.height); put("resize", "fit") }))
        }
        media.alt?.let { put("ext_alt_text", it) }
        if (media.variants.isNotEmpty() || media.durationMs != null) put("video_info", videoInfo(media))
    }

    private fun videoInfo(media: XMedia) = buildJsonObject {
        media.durationMs?.let { put("duration_millis", it) }
        put("variants", buildJsonArray {
            media.variants.forEach { variant ->
                add(buildJsonObject {
                    variant.bitrate?.let { put("bitrate", it) }
                    put("content_type", variant.contentType)
                    put("url", variant.url)
                })
            }
        })
    }

    private fun card(card: XCard): JsonObject = when (card) {
        is XCard.Link -> buildJsonObject {
            put("name", if (card.large) "summary_large_image" else "summary")
            put("url", card.url)
            put("binding_values", buildJsonObject {
                put("title", text(card.title))
                put("card_url", text(card.url))
                card.description?.let { put("description", text(it)) }
                card.domain?.let { put("vanity_url", text(it)) }
                card.imageUrl?.let { url -> put(if (card.large) "summary_photo_image_large" else "thumbnail_image_large", image(url, card.imageWidth, card.imageHeight)) }
            })
        }
        is XCard.Poll -> buildJsonObject {
            put("name", "poll${card.choices.size}choice_text_only")
            put("binding_values", buildJsonObject {
                card.choices.forEachIndexed { i, choice ->
                    put("choice${i + 1}_label", text(choice.label))
                    put("choice${i + 1}_count", text(choice.votes.toString()))
                    choice.imageUrl?.let { put("choice${i + 1}_image_large", image(it, null, null)) }
                }
                card.endsAt?.let { put("end_datetime_utc", text(it.toString())) }
                put("counts_are_final", buildJsonObject { put("boolean_value", card.final) })
            })
        }
    }

    private fun text(value: String) = buildJsonObject { put("string_value", value) }

    private fun image(url: String, width: Int?, height: Int?) = wrap("image_value", buildJsonObject {
        put("url", url)
        width?.let { put("width", it) }
        height?.let { put("height", it) }
    })
}
