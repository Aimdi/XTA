package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XGrokShare
import java.time.Instant

/**
 * What X attaches below a post's text, read from its card (`card.legacy`) as
 * XTA's _card.dart, poll.dart and unified_card.dart read it: link previews,
 * polls, promoted websites, live events, the website cards X builds itself
 * (unified cards) and shared Grok conversations. A card of a kind not drawn
 * is null, never an error.
 */
object XCards {
    private val pollSizes = mapOf(
        "poll2choice_text_only" to 2, "poll2choice_image" to 2,
        "poll3choice_text_only" to 3, "poll3choice_image" to 3,
        "poll4choice_text_only" to 4, "poll4choice_image" to 4,
    )
    private val grokRender = Regex("<grok:render[^>]*>.*?</grok:render>", RegexOption.DOT_MATCHES_ALL)

    /** A card's binding values by key, whether X sent them as a list of `{key, value}` or keyed. */
    fun bindings(card: J): Map<String, J> {
        val values = card["binding_values"]
        val list = values.list
        return if (list.isNotEmpty()) list.associateBy({ it["key"].string.orEmpty() }, { it["value"] }) else values.keys.associateWith { values[it] }
    }

    private fun Map<String, J>.string(key: String): String? = this[key]?.get("string_value")?.string?.takeIf { it.isNotBlank() }

    private fun Map<String, J>.image(key: String): J? = this[key]?.get("image_value")?.takeIf { it["url"].string != null }

    private fun Map<String, J>.firstImage(keys: List<String>): J? = keys.firstNotNullOfOrNull { image(it) }

    /** The card below a post; [entities] lead a `t.co` card address to where it really goes. */
    fun card(card: J, entities: List<XEntity>): XCard? {
        val name = card["name"].string ?: return null
        val values = bindings(card)
        return when {
            name in pollSizes || name.endsWith(":poll_choice_images") -> poll(name, values)
            name == "summary" || name == "summary_large_image" || name == "player" -> preview(name, card, values, entities)
            name == "promo_website" -> promoWebsite(values)
            name.endsWith(":live_event") -> liveEvent(values)
            name == "unified_card" -> website(unified(values))
            else -> null
        }
    }

    private fun preview(name: String, card: J, values: Map<String, J>, entities: List<XEntity>): XCard.Link? {
        val title = values.string("title") ?: return null
        val large = name != "summary"
        val picture = values.firstImage(
            if (large) listOf("summary_photo_image_large", "photo_image_full_size_large", "player_image_large", "thumbnail_image_large") else listOf("thumbnail_image_large", "thumbnail_image"),
        )
        val short = values.string("card_url") ?: card["url"].string
        return XCard.Link(
            url = entities.filterIsInstance<XEntity.Link>().firstOrNull { it.url == short }?.expanded ?: short ?: return null,
            title = title,
            description = values.string("description"),
            domain = values.string("vanity_url") ?: values.string("domain"),
            imageUrl = picture?.get("url")?.string,
            imageWidth = picture?.get("width")?.int,
            imageHeight = picture?.get("height")?.int,
            large = large,
            kind = name,
        )
    }

    /** A promoted website (`promo_website`): its own address, picture and title. */
    private fun promoWebsite(values: Map<String, J>): XCard.Link? {
        val picture = values.firstImage(listOf("promo_image_large", "promo_image", "promo_image_x_large", "promo_image_small"))
        return XCard.Link(
            url = values.string("website_url") ?: return null,
            title = values.string("title").orEmpty(),
            description = null,
            domain = values.string("vanity_url"),
            imageUrl = picture?.get("url")?.string,
            imageWidth = picture?.get("width")?.int,
            imageHeight = picture?.get("height")?.int,
            large = true,
        )
    }

    /** A live event X promotes (`…:live_event`): its page, picture, title and subtitle. */
    private fun liveEvent(values: Map<String, J>): XCard.Link? {
        val picture = values.firstImage(listOf("event_thumbnail_large", "event_thumbnail", "event_thumbnail_x_large", "event_thumbnail_small"))
        return XCard.Link(
            url = values.string("card_url") ?: return null,
            title = values.string("event_title").orEmpty(),
            description = values.string("event_subtitle"),
            domain = null,
            imageUrl = picture?.get("url")?.string,
            imageWidth = picture?.get("width")?.int,
            imageHeight = picture?.get("height")?.int,
            large = true,
        )
    }

    /**
     * A poll: two to four choices, by its name, or as many as its
     * `choice_count` says for a poll with a picture per choice. A choice
     * without its label leaves no poll rather than a wrong one.
     */
    private fun poll(name: String, values: Map<String, J>): XCard.Poll? {
        val size = pollSizes[name] ?: values.string("choice_count")?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val choices = (1..size).map { i ->
            val label = values.string("choice${i}_label") ?: return null
            val thumbnail = values.image("choice${i}_image")?.get("url")?.string
            val original = values.image("choice${i}_image_original")?.get("url")?.string
            XCard.Poll.Choice(label, votes(values.string("choice${i}_count")), thumbnail ?: original, original ?: thumbnail)
        }
        return XCard.Poll(
            choices = choices,
            endsAt = values.string("end_datetime_utc")?.let { runCatching { Instant.parse(it) }.getOrNull() },
            final = values["counts_are_final"]?.get("boolean_value")?.boolean ?: false,
        )
    }

    private fun votes(count: String?): Long = count?.toLongOrNull() ?: count?.toDoubleOrNull()?.toLong() ?: 0

    /** The unified card inside the card, decoded from the JSON string X wraps it in. */
    fun unified(values: Map<String, J>): J = values["unified_card"]?.get("string_value")?.string?.let(J::parse) ?: J(null)

    /** A website card X builds itself: a picture, a video or a carousel of pictures above the page they lead to. */
    private fun website(unified: J): XCard.Link? {
        val details = unified["component_objects"]["details_1"]["data"]
        val title = details["title"]["content"].string.orEmpty()
        val site = details["subtitle"]["content"].string
        fun mediaOf(component: String) = unified["media_entities"][unified["component_objects"][component]["data"]["id"].string.orEmpty()]
        fun urlOf(destination: String) = unified["destination_objects"][destination]["data"]["url_data"]["url"].string
        return when (unified["type"].string) {
            "image_website" -> {
                val media = mediaOf("media_1")
                XCard.Link(
                    url = urlOf("browser_1") ?: return null,
                    title = title,
                    description = null,
                    domain = site,
                    imageUrl = media["media_url_https"].string,
                    imageWidth = media["original_info"]["width"].int,
                    imageHeight = media["original_info"]["height"].int,
                    large = true,
                )
            }
            "video_website" -> {
                val video = XParser.mediaItem(mediaOf("media_1")) ?: return null
                XCard.Link(urlOf("browser_with_docked_media_1") ?: return null, title, null, site, null, null, null, large = true, media = listOf(video))
            }
            "image_carousel_website" -> carousel(unified)
            else -> null
        }
    }

    /** Several pictures that lead to one page; null without the page, a title or a picture. */
    private fun carousel(unified: J): XCard.Link? {
        val details = unified["component_objects"]["details_1"]["data"]
        val destination = details["destination"].string ?: "browser_1"
        val url = unified["destination_objects"][destination]["data"]["url_data"]["url"].string ?: return null
        val title = details["title"]["content"].string ?: return null
        val media = unified["component_objects"]["swipeable_media_1"]["data"]["media_list"].list
            .mapNotNull { it["id"].string }
            .mapNotNull { XParser.mediaItem(unified["media_entities"][it]) }
        if (media.isEmpty()) return null
        return XCard.Link(url, title, null, details["subtitle"]["content"].string, null, null, null, large = true, media = media)
    }

    /**
     * A shared Grok conversation: the question asked and the start of the
     * answer, without Grok's markup. Null without a question or a page to open.
     */
    fun grokShare(card: J): XGrokShare? {
        if (card["name"].string != "unified_card") return null
        val unified = unified(bindings(card))
        val details = unified["component_objects"]["details_1"]
        if (details["type"].string != "grok_share") return null
        val data = details["data"]
        val destination = data["destination"].string ?: "destination_1"
        val url = unified["destination_objects"][destination]["data"]["url_data"]["url"].string ?: return null
        val preview = data["conversation_preview"].list
        val question = firstMessage(preview, "USER") ?: return null
        return XGrokShare(
            url = url,
            question = question,
            answer = firstMessage(preview, "AGENT").orEmpty().replace(grokRender, "").trim(),
            grokScreenName = data["grok_user"]["screen_name"].string ?: "grok",
            grokImageUrl = data["grok_user"]["profile_image_url_https"].string,
        )
    }

    private fun firstMessage(preview: List<J>, sender: String): String? =
        preview.filter { it["sender"].string == sender }.firstNotNullOfOrNull { message -> message["message"].string?.trim()?.takeIf { it.isNotEmpty() } }
}
