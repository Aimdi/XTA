package com.aimdi.xtanative.core.backup

import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XAccount
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** An account XTA follows, as its backup keeps it. */
data class FollowedAccount(
    val id: String,
    val screenName: String,
    val name: String,
    val avatarUrl: String?,
    val verified: Boolean,
    /** Shown in the Following feed; XTA lets an account be followed but kept out of it. */
    val inFeed: Boolean,
    /** When it was followed, in epoch milliseconds: the feed's order. */
    val followedAt: Long,
) {
    val member: FeedMember get() = FeedMember(id, screenName, followedAt)
}

/** A group of followed accounts, as XTA's subscription groups keep them; XTA's home shows each as a tab. */
data class FollowGroup(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val emoji: String? = null,
    val pinned: Boolean = false,
    val nsfw: Boolean = false,
) {
    companion object {
        /** XTA's order: NSFW groups last, pinned ones first within each part, then by name. */
        fun ordered(groups: List<FollowGroup>): List<FollowGroup> =
            groups.sortedWith(compareBy<FollowGroup> { it.nsfw }.thenByDescending { it.pinned }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}

/**
 * What XTA Native takes from a file XTA exported (Settings → Data → Export):
 * the X sessions, when "Export accounts" was on, and the accounts followed.
 * Everything else in the file is left alone.
 */
data class XtaBackup(val accounts: List<XAccount>, val following: List<FollowedAccount>, val groups: List<FollowGroup>) {
    val isEmpty: Boolean get() = accounts.isEmpty() && following.isEmpty()

    companion object {
        /** Reads [text]; null when it is not an XTA backup at all. */
        fun parse(text: String): XtaBackup? {
            val root = J.parse(text)
            if (root.keys.none { it in KNOWN_SECTIONS }) return null
            val groupMembers = root["subscriptionGroupMembers"].list
                .mapNotNull { row -> row["group_id"].string?.let { group -> row["profile_id"].string?.let { group to it } } }
                .groupBy({ it.first }, { it.second })
            return XtaBackup(
                accounts = root["accounts"].list.mapNotNull(::account),
                following = root["subscriptions"].list.mapNotNull(::followed).distinctBy { it.id },
                groups = root["subscriptionGroups"].list.mapNotNull { row ->
                    val id = row["id"].string?.takeIf { it != EVERYONE } ?: return@mapNotNull null
                    FollowGroup(
                        id = id,
                        name = row["name"].string ?: id,
                        memberIds = groupMembers[id].orEmpty(),
                        emoji = row["emoji"].string?.takeIf { it.isNotBlank() },
                        pinned = row["pinned"].long == 1L,
                        nsfw = row["nsfw"].long == 1L,
                    )
                },
            )
        }

        private val KNOWN_SECTIONS = setOf("accounts", "subscriptions", "subscriptionGroups", "formatVersion")

        /** XTA's own "everyone" row, not a group anyone made. */
        private const val EVERYONE = "-1"

        /** The `auth_header` column holds the headers as a JSON object, itself stored as a string. */
        private fun account(row: J): XAccount? {
            val id = row["id"].string ?: return null
            val stored = row["auth_header"]
            val headers = (stored.string?.let { J.parse(it) } ?: stored).let { object_ ->
                object_.keys.mapNotNull { key -> object_[key].string?.let { key to it } }.toMap()
            }
            if (headers.keys.none { it.equals("cookie", ignoreCase = true) }) return null
            return XAccount(id, row["screen_name"].string, headers)
        }

        private fun followed(row: J): FollowedAccount? {
            val id = row["id"].string ?: return null
            val screenName = row["screen_name"].string ?: return null
            return FollowedAccount(
                id = id,
                screenName = screenName,
                name = row["name"].string ?: screenName,
                avatarUrl = row["profile_image_url_https"].string,
                verified = row["verified"].long == 1L,
                inFeed = row["in_feed"].long?.let { it == 1L } ?: true,
                followedAt = millisOf(row["created_at"].string),
            )
        }

        private val sqliteFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /**
         * XTA writes `yyyy-MM-dd HH:mm:ss`; older builds wrote ISO-8601. Only
         * the order matters, so a missing or unreadable date sorts first.
         */
        internal fun millisOf(value: String?): Long {
            if (value.isNullOrBlank()) return 0
            return runCatching { LocalDateTime.parse(value, sqliteFormat).toInstant(ZoneOffset.UTC).toEpochMilli() }
                .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
                .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
                .getOrDefault(0)
        }
    }
}
