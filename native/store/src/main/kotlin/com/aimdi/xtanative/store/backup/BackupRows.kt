package com.aimdi.xtanative.store.backup

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The rows of XTA's own tables as its backup carries them. Times stay the text
// XTA wrote (see XtaTime); a row XTA could not read back is left out, and the
// rest of the file still reads.

/** A saved X search, followed like an account. */
data class SearchSubscriptionRow(val query: String, val createdAt: String) {
    fun toJson(): JsonObject = jsonRow("id" to query, "created_at" to createdAt)

    companion object {
        fun from(row: J): SearchSubscriptionRow? {
            val id = row["id"].string ?: return null
            return SearchSubscriptionRow(id, row["created_at"].string ?: return null)
        }
    }
}

/** An X account followed on the device. */
data class UserSubscriptionRow(
    val id: String,
    val screenName: String,
    val name: String,
    val avatarUrl: String? = null,
    val verified: Boolean = false,
    /** When it was followed, `yyyy-MM-dd HH:mm:ss`. */
    val createdAt: String? = null,
    /** Read in the Following feed, or only in its groups. */
    val inFeed: Boolean = true,
    /** Posts kept from this account per feed load; null or 0 for no cap. */
    val maxPostsPerLoad: Int? = null,
) {
    fun toJson(): JsonObject = jsonRow(
        "id" to id,
        "screen_name" to screenName,
        "name" to name,
        "profile_image_url_https" to avatarUrl,
        "verified" to verified.bit,
        "created_at" to createdAt,
        "in_feed" to inFeed.bit,
        "max_posts_per_load" to maxPostsPerLoad,
    )

    companion object {
        /** XTA reads a row without `in_feed` as kept out of the feed; its column defaults to in, and so does this. */
        fun from(row: J): UserSubscriptionRow? {
            val id = row["id"].string ?: return null
            val screenName = row["screen_name"].string ?: return null
            return UserSubscriptionRow(
                id = id,
                screenName = screenName,
                name = row["name"].string ?: return null,
                avatarUrl = row["profile_image_url_https"].string,
                verified = row["verified"].bit(missing = false),
                createdAt = row["created_at"].string,
                inFeed = row["in_feed"].bit(missing = true),
                maxPostsPerLoad = row["max_posts_per_load"].int,
            )
        }
    }
}

/** A subscription group. `-1` is XTA's own "everyone" row, which an older backup may carry. */
data class GroupRow(
    val id: String,
    val name: String,
    /** The icon as XTA stores it: a JSON pack and key. */
    val icon: String = DEFAULT_ICON,
    /** ARGB, or null for the theme's. */
    val color: Long? = null,
    val createdAt: String,
    val pinned: Boolean = false,
    /** Marked adult by the reader: the board sinks it under Censored. */
    val nsfw: Boolean = false,
    val emoji: String? = null,
    /** 0 picks the mark itself, 1 prefers the emoji, 2 the icon, 3 a generated mark. */
    val markStyle: Int = 0,
    /** The group this one sits inside. */
    val parentId: String? = null,
) {
    fun toJson(): JsonObject = jsonRow(
        "id" to id,
        "name" to name,
        "icon" to icon,
        "color" to color,
        "created_at" to createdAt,
        "pinned" to pinned.bit,
        "nsfw" to nsfw.bit,
        "emoji" to emoji,
        "mark_style" to markStyle,
        "parent_id" to parentId,
    )

    companion object {
        const val EVERYONE = "-1"
        const val DEFAULT_ICON = """{"pack":"custom","key":"rss_feed"}"""

        /** A group from before icons were packs (no icon, empty, or `rss`) gets the default one, as XTA's import gives it. */
        fun from(row: J): GroupRow? {
            val id = row["id"].string ?: return null
            val name = row["name"].string ?: return null
            return GroupRow(
                id = id,
                name = name,
                icon = row["icon"].string?.takeUnless { it.isEmpty() || it == "rss" } ?: DEFAULT_ICON,
                color = row["color"].long,
                createdAt = row["created_at"].string ?: return null,
                pinned = row["pinned"].bit(missing = false),
                nsfw = row["nsfw"].bit(missing = false),
                emoji = row["emoji"].string,
                markStyle = row["mark_style"].int?.takeIf { it in 0..3 } ?: 0,
                parentId = row["parent_id"].string,
            )
        }
    }
}

/**
 * A followed account of any network in a group. [profileId] is the id its
 * subscription row has; some networks prefix theirs (`tiktok:<id>`, `pixiv:<id>`).
 */
data class GroupMemberRow(val groupId: String, val profileId: String) {
    fun toJson(): JsonObject = jsonRow("group_id" to groupId, "profile_id" to profileId)

    companion object {
        fun from(row: J): GroupMemberRow? {
            return GroupMemberRow(row["group_id"].string ?: return null, row["profile_id"].string ?: return null)
        }
    }
}

/** A saved search in a group, as older builds stored it; never migrated, so still carried. */
data class SearchGroupMemberRow(val groupId: String, val searchId: String) {
    fun toJson(): JsonObject = jsonRow("group_id" to groupId, "search_id" to searchId)

    companion object {
        fun from(row: J) = SearchGroupMemberRow(row["group_id"].string ?: "", row["search_id"].string ?: "")
    }
}

/** A saved post: X's post as JSON in [content], its folder (null is unfiled) and the reader's note. */
data class SavedPostRow(
    val id: String,
    val userId: String? = null,
    val content: String? = null,
    val folderId: String? = null,
    val note: String? = null,
) {
    fun toJson(): JsonObject = jsonRow("id" to id, "content" to content, "user_id" to userId, "folder_id" to folderId, "note" to note)

    companion object {
        fun from(row: J): SavedPostRow? {
            return SavedPostRow(
                id = row["id"].string ?: return null,
                userId = row["user_id"].string,
                content = row["content"].string,
                folderId = row["folder_id"].string,
                note = row["note"].string,
            )
        }
    }
}

/** A folder of saved posts. */
data class SavedFolderRow(
    val id: String,
    val name: String,
    val position: Int = 0,
    val createdAt: String,
    /** Saving into it also downloads the post's pictures. */
    val autoDownload: Boolean = false,
    /** Saving into it also sends the pictures to the reader's server. */
    val autoUpload: Boolean = false,
) {
    fun toJson(): JsonObject = jsonRow(
        "id" to id,
        "name" to name,
        "position" to position,
        "created_at" to createdAt,
        "auto_download" to autoDownload.bit,
        "auto_upload" to autoUpload.bit,
    )

    companion object {
        fun from(row: J): SavedFolderRow? {
            return SavedFolderRow(
                id = row["id"].string ?: return null,
                name = row["name"].string ?: return null,
                position = row["position"].int ?: 0,
                createdAt = row["created_at"].string ?: return null,
                autoDownload = row["auto_download"].bit(missing = false),
                autoUpload = row["auto_upload"].bit(missing = false),
            )
        }
    }
}

/** A post liked on the device; X is never told. */
data class LikedPostRow(val id: String, val userId: String? = null, val content: String? = null) {
    fun toJson(): JsonObject = jsonRow("id" to id, "content" to content, "user_id" to userId)

    companion object {
        fun from(row: J): LikedPostRow? {
            return LikedPostRow(row["id"].string ?: return null, row["user_id"].string, row["content"].string)
        }
    }
}

/** An account whose reposts, or whose replies, are kept out of every feed. */
data class FeedFilterRow(val userId: String, val screenName: String) {
    fun toJson(): JsonObject = jsonRow("user_id" to userId, "screen_name" to screenName)

    companion object {
        fun from(row: J) = FeedFilterRow(row["user_id"].string ?: "", row["screen_name"].string ?: "")
    }
}

/** How far the reader had got in one group's feed; the time stays the ISO text the column holds. */
data class ReadPositionRow(val groupId: String, val chainId: String, val chainCreatedAt: String? = null) {
    fun toJson(): JsonObject = jsonRow("group_id" to groupId, "chain_id" to chainId, "chain_created_at" to chainCreatedAt)

    companion object {
        fun from(row: J) = ReadPositionRow(row["group_id"].string ?: "", row["chain_id"].string ?: "", row["chain_created_at"].string)
    }
}

/**
 * An X session, only in a backup made with "Export accounts". [authHeader]
 * is what XTA stored: the headers as a JSON object, itself kept as a string.
 */
data class AccountRow(
    val id: String,
    val authHeader: JsonElement,
    val screenName: String? = null,
    /** When X last answered 404, and how many times in a row: the account's health. */
    val lastNotFoundAt: String? = null,
    val consecutiveNotFound: Int = 0,
) {
    /** The headers, from either form XTA has written them in. */
    val headers: Map<String, String>
        get() {
            val stored = J(authHeader)
            val object_ = stored.string?.let { J.parse(it) } ?: stored
            return object_.keys.mapNotNull { key -> object_[key].string?.let { key to it } }.toMap()
        }

    fun toJson(): JsonObject = jsonRow(
        "id" to id,
        "auth_header" to authHeader,
        "screen_name" to screenName,
        "last_not_found_at" to lastNotFoundAt,
        "consecutive_not_found" to consecutiveNotFound,
    )

    companion object {
        fun from(row: J): AccountRow? {
            return AccountRow(
                id = row["id"].string ?: return null,
                authHeader = row["auth_header"].raw ?: JsonNull,
                screenName = row["screen_name"].string,
                lastNotFoundAt = row["last_not_found_at"].string,
                consecutiveNotFound = row["consecutive_not_found"].int ?: 0,
            )
        }
    }
}

/** A private note on a profile, never synced anywhere but the reader's own backup. */
data class ProfileNoteRow(val profileId: String, val note: String, val updatedAt: String? = null) {
    fun toJson(): JsonObject = jsonRow("id" to profileId, "note" to note, "updated_at" to updatedAt)

    companion object {
        fun from(row: J): ProfileNoteRow? {
            return ProfileNoteRow(row["id"].string ?: return null, row["note"].string ?: "", row["updated_at"].string)
        }
    }
}

/**
 * An antenna: a saved keyword search, of all of X or only the accounts
 * followed. XTA keeps the terms comma-joined; they are split here as its
 * import splits them, at commas and line breaks.
 */
data class AntennaRow(
    val id: String,
    val name: String,
    val includeTerms: List<String>,
    val excludeTerms: List<String> = emptyList(),
    /** `search` or `following`. */
    val scope: String = SEARCH,
    val createdAt: String? = null,
) {
    fun toJson(): JsonObject = jsonRow(
        "id" to id,
        "name" to name,
        "include_terms" to includeTerms.joinToString(", "),
        "exclude_terms" to excludeTerms.takeIf { it.isNotEmpty() }?.joinToString(", "),
        "scope" to scope,
        "created_at" to createdAt,
    )

    companion object {
        const val SEARCH = "search"
        const val FOLLOWING = "following"

        fun from(row: J): AntennaRow? {
            return AntennaRow(
                id = row["id"].string ?: return null,
                name = row["name"].string ?: "",
                includeTerms = terms(row["include_terms"].string),
                excludeTerms = terms(row["exclude_terms"].string),
                scope = if (row["scope"].string == FOLLOWING) FOLLOWING else SEARCH,
                createdAt = row["created_at"].string,
            )
        }

        /** XTA's split of what was typed: at commas and line breaks, trimmed, empties dropped. */
        fun terms(raw: String?): List<String> = raw.orEmpty().split(',', '\n').map(String::trim).filter(String::isNotEmpty)
    }
}

/** A note the reader wrote on the device, with its pictures' bytes, so a restore keeps them. */
data class LocalPostRow(
    val id: String,
    val body: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val media: List<LocalPostMediaRow> = emptyList(),
    val quotedPostId: String? = null,
    /** The quoted X post as JSON. */
    val quotedPostJson: String? = null,
    /** The note this one answers; null for a note of its own. */
    val inReplyToId: String? = null,
) {
    /** The table's row plus `media` with the bytes, as XTA's backup writes it. */
    fun toJson(): JsonObject = jsonRow(
        "id" to id,
        "body" to body,
        "created_at" to createdAt,
        "updated_at" to updatedAt,
        "media_json" to JsonArray(media.map { it.toJson(withData = false) }).toString(),
        "quoted_tweet_id" to quotedPostId,
        "quoted_tweet_json" to quotedPostJson,
        "in_reply_to_id" to inReplyToId,
        "media" to JsonArray(media.map { it.toJson(withData = true) }),
    )

    companion object {
        fun from(row: J): LocalPostRow? {
            return LocalPostRow(
                id = row["id"].string ?: return null,
                body = row["body"].string ?: "",
                createdAt = row["created_at"].string,
                updatedAt = row["updated_at"].string,
                media = LocalPostMediaRow.list(row["media"].takeIf { it.exists } ?: row["media_json"]),
                quotedPostId = row["quoted_tweet_id"].string,
                quotedPostJson = row["quoted_tweet_json"].string,
                inReplyToId = row["in_reply_to_id"].string?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

/** One file of a [LocalPostRow]; [data] is its bytes in base64, present only in a backup. */
data class LocalPostMediaRow(val id: String, val name: String = "", val mime: String = "application/octet-stream", val data: String? = null) {
    fun toJson(withData: Boolean): JsonObject = JsonObject(
        buildMap {
            put("id", JsonPrimitive(id))
            put("name", JsonPrimitive(name))
            put("mime", JsonPrimitive(mime))
            if (withData && data != null) put("data", JsonPrimitive(data))
        },
    )

    companion object {
        /** A list, or the same list as a JSON string; files without an id are dropped. */
        fun list(raw: J): List<LocalPostMediaRow> {
            val items = raw.string?.let { J.parse(it) } ?: raw
            return items.list.mapNotNull { item ->
                (item.raw as? JsonObject)?.let {
                    LocalPostMediaRow(item["id"].string ?: "", item["name"].string ?: "", item["mime"].string ?: "application/octet-stream", item["data"].string)
                }
            }.filter { it.id.isNotEmpty() }
        }
    }
}

/** What the reader marked in an archived article: highlights with their notes, tags, and the text taken from its pictures. */
data class ArchiveAnnotationRow(val highlights: Map<String, String> = emptyMap(), val tags: List<String> = emptyList(), val extracted: String = "") {
    fun toJson(): JsonObject = jsonRow(
        "highlights" to JsonObject(highlights.mapValues { JsonPrimitive(it.value) }),
        "tags" to tags,
        "extracted" to extracted,
    )

    companion object {
        /** Only notes under `archive-note:`; profiles and request caches in the same storage stay out. */
        const val PREFIX = "archive-note:"

        fun from(raw: J): ArchiveAnnotationRow = ArchiveAnnotationRow(
            highlights = raw["highlights"].let { highlights -> highlights.keys.mapNotNull { key -> highlights[key].string?.let { key to it } }.toMap() },
            tags = raw["tags"].list.mapNotNull { it.string }.distinct(),
            extracted = raw["extracted"].string ?: "",
        )

        /** The annotations a backup carries; anything else stored beside them is left out. */
        fun section(raw: J): Map<String, ArchiveAnnotationRow> =
            raw.keys.filter { it.startsWith(PREFIX) && raw[it].raw is JsonObject }.associateWith { from(raw[it]) }
    }
}
