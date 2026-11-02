package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XAudioSpace
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.core.x.model.XLiveLinks
import java.time.Instant

/**
 * Live video and Spaces, as XTA's broadcasts.dart finds them: in a post (its
 * links, its card, or its text), and in what X answers about them.
 */
object XLiveParser {
    private val thumbnailKeys = listOf(
        "broadcast_thumbnail_large", "broadcast_thumbnail_x_large", "broadcast_thumbnail", "broadcast_thumbnail_small",
        "thumbnail_image_large", "thumbnail_image_x_large", "thumbnail_image", "thumbnail_image_small", "thumbnail",
    )

    /**
     * The broadcast or Space a post carries: marked by a link to one (also a
     * video's own link), by a `…:broadcast` or `…:audiospace` card or its
     * bindings, or by a link in its text that its entities left out. Null for
     * any other post.
     */
    fun liveOf(legacy: J, noteEntities: J, card: J, texts: List<String?>): XLive? {
        val links = linksOf(legacy, noteEntities)
        val values = XCards.bindings(card)
        fun string(key: String) = values[key]?.get("string_value")?.string?.takeIf { it.isNotEmpty() }
        val broadcastId = links.firstNotNullOfOrNull(XLiveLinks::broadcastId)
            ?: XLiveLinks.broadcastId(string("broadcast_url")) ?: string("broadcast_id")
            ?: texts.firstNotNullOfOrNull(XLiveLinks::broadcastIdInText)
        val spaceId = links.firstNotNullOfOrNull(XLiveLinks::spaceId)
            ?: XLiveLinks.spaceId(string("vanity_url")) ?: string("id") ?: XLiveLinks.spaceId(string("card_url"))
            ?: texts.firstNotNullOfOrNull(XLiveLinks::spaceIdInText)
        val name = card["name"].string.orEmpty()
        if (broadcastId == null && spaceId == null && !name.endsWith(":broadcast") && !name.endsWith(":audiospace")) return null
        return XLive(
            mediaKey = string("broadcast_media_key") ?: string("media_key"),
            broadcastId = broadcastId,
            spaceId = spaceId,
            imageUrl = thumbnailOf(values),
            aspectRatio = aspectOf(string("broadcast_width"), string("broadcast_height")),
        )
    }

    /** Every address a post's links and media name: where they lead, what they show, and the `t.co` one. */
    private fun linksOf(legacy: J, noteEntities: J): List<String> =
        listOf(legacy["entities"]["urls"], noteEntities["urls"], legacy["extended_entities"]["media"], legacy["entities"]["media"])
            .flatMap { it.list }
            .flatMap { listOfNotNull(it["expanded_url"].string, it["display_url"].string, it["url"].string) }

    private fun thumbnailOf(values: Map<String, J>): String? = thumbnailKeys.firstNotNullOfOrNull { key ->
        values[key]?.get("image_value")?.get("url")?.string?.takeIf { it.isNotEmpty() }
            ?: values[key]?.get("string_value")?.string?.takeIf { it.startsWith("http") }
    }

    /** The player's shape: 16:9 unless the card names a wider one, as XTA draws it. */
    private fun aspectOf(width: String?, height: String?): Float {
        val w = width?.toDoubleOrNull() ?: 16.0
        val h = height?.toDoubleOrNull() ?: 9.0
        val ratio = if (h == 0.0) 16.0 / 9 else w / h
        return if (!ratio.isFinite() || ratio < 1.2) 16f / 9f else ratio.toFloat()
    }

    /** A Space's metadata from AudioSpaceById; null when X sent none. */
    fun audioSpace(body: J, spaceId: String): XAudioSpace? {
        val metadata = body["data"]["audioSpace"]["metadata"]
        if (!metadata.exists) return null
        fun at(key: String) = metadata[key].long?.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        return XAudioSpace(
            id = metadata["rest_id"].string ?: spaceId,
            mediaKey = metadata["media_key"].string,
            title = metadata["title"].string,
            state = metadata["state"].string,
            creator = XParser.user(metadata["creator_results"]["result"]),
            startedAt = at("started_at"),
            endedAt = at("ended_at"),
            scheduledStart = at("scheduled_start"),
            liveListeners = metadata["total_live_listeners"].long,
            replayWatched = metadata["total_replay_watched"].long,
            replayAvailable = metadata["is_space_available_for_replay"].boolean ?: false,
        )
    }

    /** The `media_key` of broadcast [id] in `broadcasts/show.json`. */
    fun broadcastMediaKey(body: J, id: String): String? = body["broadcasts"][id]["media_key"].string

    /** The HLS playlist in `live_video_stream/status`. */
    fun playbackUrl(body: J): String? = body["source"]["noRedirectPlaybackUrl"].string
}
