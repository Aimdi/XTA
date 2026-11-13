package com.aimdi.xtanative.store.backup

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject

// The rows the networks keep in XTA's database, as its backup carries them:
// followed accounts per network, and the likes and votes that only ever
// existed on the device. A followed account missing from a row's `in_feed`
// is in the feed, as the networks' columns default.

/** An account followed on Threads (its handle), Bluesky (its handle or DID) or Mastodon (`user@domain`). */
data class ProfileSubscriptionRow(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val inFeed: Boolean = true,
    val createdAt: String? = null,
) {
    fun toJson(): JsonObject = jsonRow("id" to id, "name" to name, "avatar_url" to avatarUrl, "in_feed" to inFeed.bit, "created_at" to createdAt)

    companion object {
        fun from(row: J): ProfileSubscriptionRow? {
            return ProfileSubscriptionRow(
                id = row["id"].string ?: return null,
                name = row["name"].string ?: return null,
                avatarUrl = row["avatar_url"].string,
                inFeed = row["in_feed"].bit(missing = true),
                createdAt = row["created_at"].string,
            )
        }
    }
}

/** A followed subreddit, or a followed booru tag: the two keep the same row. */
data class NamedSubscriptionRow(val id: String, val name: String, val inFeed: Boolean = true, val createdAt: String? = null) {
    fun toJson(): JsonObject = jsonRow("id" to id, "name" to name, "in_feed" to inFeed.bit, "created_at" to createdAt)

    companion object {
        fun from(row: J): NamedSubscriptionRow? {
            return NamedSubscriptionRow(
                id = row["id"].string ?: return null,
                name = row["name"].string ?: return null,
                inFeed = row["in_feed"].bit(missing = true),
                createdAt = row["created_at"].string,
            )
        }
    }
}

/** A followed Substack publication, living at [baseUrl]. */
data class SubstackSubscriptionRow(
    val id: String,
    val baseUrl: String,
    val name: String,
    val logoUrl: String? = null,
    val inFeed: Boolean = true,
    val createdAt: String? = null,
) {
    fun toJson(): JsonObject =
        jsonRow("id" to id, "base_url" to baseUrl, "name" to name, "logo_url" to logoUrl, "in_feed" to inFeed.bit, "created_at" to createdAt)

    companion object {
        fun from(row: J): SubstackSubscriptionRow? {
            return SubstackSubscriptionRow(
                id = row["id"].string ?: return null,
                baseUrl = row["base_url"].string ?: return null,
                name = row["name"].string ?: return null,
                logoUrl = row["logo_url"].string,
                inFeed = row["in_feed"].bit(missing = true),
                createdAt = row["created_at"].string,
            )
        }
    }
}

/** A followed RSS or Atom feed. */
data class RssSubscriptionRow(
    val id: String,
    val feedUrl: String,
    val name: String,
    val siteUrl: String? = null,
    val iconUrl: String? = null,
    val inFeed: Boolean = true,
    val createdAt: String? = null,
) {
    fun toJson(): JsonObject = jsonRow(
        "id" to id,
        "feed_url" to feedUrl,
        "name" to name,
        "site_url" to siteUrl,
        "icon_url" to iconUrl,
        "in_feed" to inFeed.bit,
        "created_at" to createdAt,
    )

    companion object {
        fun from(row: J): RssSubscriptionRow? {
            return RssSubscriptionRow(
                id = row["id"].string ?: return null,
                feedUrl = row["feed_url"].string ?: "",
                name = row["name"].string ?: "",
                siteUrl = row["site_url"].string,
                iconUrl = row["icon_url"].string,
                inFeed = row["in_feed"].bit(missing = true),
                createdAt = row["created_at"].string,
            )
        }
    }
}

/** A watched ticker. */
data class StockSubscriptionRow(val id: String, val symbol: String, val inFeed: Boolean = true, val createdAt: String? = null) {
    fun toJson(): JsonObject = jsonRow("id" to id, "symbol" to symbol, "in_feed" to inFeed.bit, "created_at" to createdAt)

    companion object {
        fun from(row: J): StockSubscriptionRow? {
            return StockSubscriptionRow(
                id = row["id"].string ?: return null,
                symbol = row["symbol"].string ?: return null,
                inFeed = row["in_feed"].bit(missing = true),
                createdAt = row["created_at"].string,
            )
        }
    }
}

/** A followed TikTok account: its handle, lowercased, and TikTok's own id for it. Always in the feed. */
data class TikTokSubscriptionRow(
    val id: String,
    val secUid: String,
    val name: String,
    val avatarUrl: String? = null,
    val signature: String? = null,
    val createdAt: String? = null,
) {
    fun toJson(): JsonObject = jsonRow(
        "id" to id,
        "sec_uid" to secUid,
        "name" to name,
        "avatar_url" to avatarUrl,
        "signature" to signature,
        "in_feed" to 1,
        "created_at" to createdAt,
    )

    companion object {
        fun from(row: J): TikTokSubscriptionRow? {
            return TikTokSubscriptionRow(
                id = row["id"].string ?: return null,
                secUid = row["sec_uid"].string ?: return null,
                name = row["name"].string ?: return null,
                avatarUrl = row["avatar_url"].string,
                signature = row["signature"].string,
                createdAt = row["created_at"].string,
            )
        }
    }
}

/** A followed Instagram account: its username, lowercased, and Instagram's id ([pk]). Always in the feed. */
data class InstagramSubscriptionRow(
    val id: String,
    val pk: String,
    val name: String,
    val avatarUrl: String? = null,
    val biography: String? = null,
    val createdAt: String? = null,
) {
    fun toJson(): JsonObject = jsonRow(
        "id" to id,
        "pk" to pk,
        "name" to name,
        "avatar_url" to avatarUrl,
        "signature" to biography,
        "in_feed" to 1,
        "created_at" to createdAt,
    )

    companion object {
        fun from(row: J): InstagramSubscriptionRow? {
            return InstagramSubscriptionRow(
                id = row["id"].string ?: return null,
                pk = row["pk"].string ?: return null,
                name = row["name"].string ?: return null,
                avatarUrl = row["avatar_url"].string,
                biography = row["signature"].string,
                createdAt = row["created_at"].string,
            )
        }
    }
}

/**
 * A Reddit upvote, a Threads like or a Bluesky like: the network is never
 * told, so this is the only copy there is.
 */
data class LocalMarkRow(val id: String) {
    fun toJson(): JsonObject = jsonRow("id" to id)

    companion object {
        fun from(row: J) = LocalMarkRow(row["id"].string ?: "")
    }
}

/** An E-Hentai gallery as a favourite or a history row keeps it. */
data class EhGalleryRow(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String? = null,
    /** E-Hentai's label, such as `Doujinshi` or `Non-H`. */
    val category: String? = null,
    val thumbUrl: String? = null,
    val uploader: String? = null,
    val pageCount: Int? = null,
    val rating: Double? = null,
    val postedAt: String? = null,
) {
    internal fun fields(): Array<Pair<String, Any?>> = arrayOf(
        "gid" to gid,
        "token" to token,
        "title" to title,
        "title_jpn" to titleJpn,
        "category" to category,
        "thumb_url" to thumbUrl,
        "uploader" to uploader,
        "page_count" to pageCount,
        "rating" to rating,
        "posted_at" to postedAt,
    )

    companion object {
        fun from(row: J): EhGalleryRow? {
            return EhGalleryRow(
                gid = row["gid"].long ?: return null,
                token = row["token"].string ?: return null,
                title = row["title"].string ?: return null,
                titleJpn = row["title_jpn"].string,
                category = row["category"].string,
                thumbUrl = row["thumb_url"].string,
                uploader = row["uploader"].string,
                pageCount = row["page_count"].int,
                rating = row["rating"].double,
                postedAt = row["posted_at"].string,
            )
        }
    }
}

/** A gallery kept as a favourite on the device. */
data class EhFavoriteRow(val gallery: EhGalleryRow, val favoritedAt: String? = null) {
    fun toJson(): JsonObject = jsonRow(*gallery.fields(), "favorited_at" to favoritedAt)

    companion object {
        fun from(row: J): EhFavoriteRow? = EhGalleryRow.from(row)?.let { EhFavoriteRow(it, row["favorited_at"].string) }
    }
}

/** A gallery read, and the page the reader stopped on. */
data class EhHistoryRow(val gallery: EhGalleryRow, val lastPage: Int = 1, val viewedAt: String? = null) {
    fun toJson(): JsonObject = jsonRow(*gallery.fields(), "last_page" to lastPage, "viewed_at" to viewedAt)

    companion object {
        fun from(row: J): EhHistoryRow? =
            EhGalleryRow.from(row)?.let { EhHistoryRow(it, row["last_page"].int ?: 1, row["viewed_at"].string) }
    }
}
