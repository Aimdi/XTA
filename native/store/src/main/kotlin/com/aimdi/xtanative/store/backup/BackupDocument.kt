package com.aimdi.xtanative.store.backup

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * XTA's backup document (Settings › Data › Export, and what a WebDAV sync
 * uploads), read and written in XTA's own format: every section, the
 * header that says which format wrote it, and the settings.
 *
 * A section is null when the file does not carry it, which a restore must
 * tell from an empty one: null restores nothing, empty restores an empty
 * table. Times stay the text XTA wrote ([XtaTime] reads them).
 */
data class BackupDocument(
    /** [FORMAT_VERSION] for what XTA writes today, [LEGACY_FORMAT_VERSION] for a file from before the header. */
    val formatVersion: Int = FORMAT_VERSION,
    /** When the file was written, ISO-8601. */
    val exportedAt: String? = null,
    /** The build that wrote it, `version+build`. */
    val appVersion: String? = null,
    /** Notes on archived articles, by their `archive-note:` key. */
    val archiveAnnotations: Map<String, ArchiveAnnotationRow>? = null,
    /** XTA's preferences, key to value, credentials already left out by whoever wrote the file. */
    val settings: Map<String, JsonElement>? = null,
    val threadsSubscriptions: List<ProfileSubscriptionRow>? = null,
    val threadsLocalLikes: List<LocalMarkRow>? = null,
    val blueskySubscriptions: List<ProfileSubscriptionRow>? = null,
    val blueskyLocalLikes: List<LocalMarkRow>? = null,
    val mastodonSubscriptions: List<ProfileSubscriptionRow>? = null,
    val tiktokSubscriptions: List<TikTokSubscriptionRow>? = null,
    val instagramSubscriptions: List<InstagramSubscriptionRow>? = null,
    val redditSubscriptions: List<NamedSubscriptionRow>? = null,
    val redditLocalVotes: List<LocalMarkRow>? = null,
    val substackSubscriptions: List<SubstackSubscriptionRow>? = null,
    val rssSubscriptions: List<RssSubscriptionRow>? = null,
    val booruSubscriptions: List<NamedSubscriptionRow>? = null,
    val ehFavorites: List<EhFavoriteRow>? = null,
    val ehHistory: List<EhHistoryRow>? = null,
    val stockSubscriptions: List<StockSubscriptionRow>? = null,
    val searchSubscriptions: List<SearchSubscriptionRow>? = null,
    /** The X accounts followed. */
    val subscriptions: List<UserSubscriptionRow>? = null,
    val groups: List<GroupRow>? = null,
    val groupMembers: List<GroupMemberRow>? = null,
    val searchGroupMembers: List<SearchGroupMemberRow>? = null,
    val savedPosts: List<SavedPostRow>? = null,
    val savedFolders: List<SavedFolderRow>? = null,
    val likedPosts: List<LikedPostRow>? = null,
    val repostFilters: List<FeedFilterRow>? = null,
    val replyFilters: List<FeedFilterRow>? = null,
    val readPositions: List<ReadPositionRow>? = null,
    /** The X sessions, only when "Export accounts" was chosen. */
    val accounts: List<AccountRow>? = null,
    val profileNotes: List<ProfileNoteRow>? = null,
    val antennas: List<AntennaRow>? = null,
    val localPosts: List<LocalPostRow>? = null,
    /** On a document a WebDAV sync uploaded: the earlier versions it kept on the server, newest first. */
    val webDavHistory: List<WebDavVersion>? = null,
) {
    val isSupported: Boolean get() = isSupported(formatVersion)

    /** Nothing in the file is worth restoring. */
    val isEmpty: Boolean get() = counts().isEmpty()

    /**
     * What the file would restore, for the preview shown before anything is
     * written, in the order XTA lists it. Sections the file is silent about,
     * and empty ones, are left out.
     */
    fun counts(): Map<BackupCategory, Int> = linkedMapOf(
        BackupCategory.ARCHIVE_ANNOTATIONS to archiveAnnotations?.size,
        BackupCategory.SETTINGS to settings?.size,
        BackupCategory.SUBSCRIPTIONS to total(subscriptions, searchSubscriptions),
        BackupCategory.GROUPS to groups?.size,
        BackupCategory.GROUP_MEMBERS to total(groupMembers, searchGroupMembers),
        BackupCategory.SAVED_POSTS to savedPosts?.size,
        BackupCategory.FOLDERS to savedFolders?.size,
        BackupCategory.LIKED_POSTS to likedPosts?.size,
        BackupCategory.FILTERS to total(repostFilters, replyFilters),
        BackupCategory.READ_POSITIONS to readPositions?.size,
        BackupCategory.ACCOUNTS to accounts?.size,
        BackupCategory.PROFILE_NOTES to profileNotes?.size,
        BackupCategory.ANTENNAS to antennas?.size,
        BackupCategory.LOCAL_POSTS to localPosts?.size,
        BackupCategory.THREADS to threadsSubscriptions?.size,
        BackupCategory.THREADS_LIKES to threadsLocalLikes?.size,
        BackupCategory.BLUESKY to blueskySubscriptions?.size,
        BackupCategory.BLUESKY_LIKES to blueskyLocalLikes?.size,
        BackupCategory.MASTODON to mastodonSubscriptions?.size,
        BackupCategory.TIKTOK_SUBSCRIPTIONS to tiktokSubscriptions?.size,
        BackupCategory.INSTAGRAM_SUBSCRIPTIONS to instagramSubscriptions?.size,
        BackupCategory.SUBREDDITS to redditSubscriptions?.size,
        BackupCategory.UPVOTES to redditLocalVotes?.size,
        BackupCategory.SUBSTACK to substackSubscriptions?.size,
        BackupCategory.RSS to rssSubscriptions?.size,
        BackupCategory.BOORU_TAGS to booruSubscriptions?.size,
        BackupCategory.EH_FAVORITES to ehFavorites?.size,
        BackupCategory.EH_HISTORY to ehHistory?.size,
        BackupCategory.STOCKS to stockSubscriptions?.size,
    ).mapNotNull { (category, count) -> count?.takeIf { it > 0 }?.let { category to it } }.toMap()

    /**
     * Only the sections [choice] asks for, as XTA's export screen picks them.
     * Local posts always go; [settings] is left as given, since what it may
     * carry is the settings' own business (see `Settings.exported`).
     */
    fun chosen(choice: ExportChoice): BackupDocument {
        fun <T> List<T>?.ifChosen(chosen: Boolean) = takeIf { chosen }
        val networks = choice.subscriptions
        return copy(
            archiveAnnotations = archiveAnnotations.takeIf { choice.savedPosts },
            threadsSubscriptions = threadsSubscriptions.ifChosen(networks),
            threadsLocalLikes = threadsLocalLikes.ifChosen(networks),
            blueskySubscriptions = blueskySubscriptions.ifChosen(networks),
            blueskyLocalLikes = blueskyLocalLikes.ifChosen(networks),
            mastodonSubscriptions = mastodonSubscriptions.ifChosen(networks),
            tiktokSubscriptions = tiktokSubscriptions.ifChosen(networks),
            instagramSubscriptions = instagramSubscriptions.ifChosen(networks),
            redditSubscriptions = redditSubscriptions.ifChosen(networks),
            redditLocalVotes = redditLocalVotes.ifChosen(networks),
            substackSubscriptions = substackSubscriptions.ifChosen(networks),
            rssSubscriptions = rssSubscriptions.ifChosen(networks),
            booruSubscriptions = booruSubscriptions.ifChosen(networks),
            ehFavorites = ehFavorites.ifChosen(networks),
            ehHistory = ehHistory.ifChosen(networks),
            stockSubscriptions = stockSubscriptions.ifChosen(networks),
            searchSubscriptions = searchSubscriptions.ifChosen(choice.subscriptions),
            subscriptions = subscriptions.ifChosen(choice.subscriptions),
            groups = groups.ifChosen(choice.groups),
            groupMembers = groupMembers.ifChosen(choice.includesGroupMembers),
            searchGroupMembers = searchGroupMembers.ifChosen(choice.includesGroupMembers),
            savedPosts = savedPosts.ifChosen(choice.savedPosts),
            savedFolders = savedFolders.ifChosen(choice.savedFolders),
            likedPosts = likedPosts.ifChosen(choice.likedPosts),
            repostFilters = repostFilters.ifChosen(choice.filters),
            replyFilters = replyFilters.ifChosen(choice.filters),
            readPositions = readPositions.ifChosen(choice.readPositions),
            accounts = accounts.ifChosen(choice.accounts),
            profileNotes = profileNotes.ifChosen(choice.filters),
            antennas = antennas.ifChosen(choice.filters),
        )
    }

    /**
     * What a restore writes. Reading positions go only when asked for: one
     * from another device marks posts as seen that this reader never saw.
     */
    fun forRestore(includeReadPositions: Boolean): BackupDocument = copy(readPositions = readPositions.takeIf { includeReadPositions })

    /** The document as XTA writes it: every section in XTA's order, null where it carries nothing. */
    fun toJson(): JsonObject = JsonObject(
        buildMap {
            put(BackupKeys.ARCHIVE_ANNOTATIONS, archiveAnnotations?.let { notes -> JsonObject(notes.mapValues { it.value.toJson() }) } ?: JsonNull)
            put(BackupKeys.FORMAT_VERSION, JsonPrimitive(formatVersion))
            put(BackupKeys.EXPORTED_AT, jsonOf(exportedAt))
            put(BackupKeys.APP_VERSION, jsonOf(appVersion))
            put(BackupKeys.SETTINGS, settings?.let(::JsonObject) ?: JsonNull)
            put(BackupKeys.THREADS_SUBSCRIPTIONS, threadsSubscriptions.json { it.toJson() })
            put(BackupKeys.THREADS_LOCAL_LIKES, threadsLocalLikes.json { it.toJson() })
            put(BackupKeys.BLUESKY_SUBSCRIPTIONS, blueskySubscriptions.json { it.toJson() })
            put(BackupKeys.BLUESKY_LOCAL_LIKES, blueskyLocalLikes.json { it.toJson() })
            put(BackupKeys.MASTODON_SUBSCRIPTIONS, mastodonSubscriptions.json { it.toJson() })
            put(BackupKeys.TIKTOK_SUBSCRIPTIONS, tiktokSubscriptions.json { it.toJson() })
            put(BackupKeys.INSTAGRAM_SUBSCRIPTIONS, instagramSubscriptions.json { it.toJson() })
            put(BackupKeys.REDDIT_SUBSCRIPTIONS, redditSubscriptions.json { it.toJson() })
            put(BackupKeys.REDDIT_LOCAL_VOTES, redditLocalVotes.json { it.toJson() })
            put(BackupKeys.SUBSTACK_SUBSCRIPTIONS, substackSubscriptions.json { it.toJson() })
            put(BackupKeys.RSS_SUBSCRIPTIONS, rssSubscriptions.json { it.toJson() })
            put(BackupKeys.BOORU_SUBSCRIPTIONS, booruSubscriptions.json { it.toJson() })
            put(BackupKeys.EH_FAVORITES, ehFavorites.json { it.toJson() })
            put(BackupKeys.EH_HISTORY, ehHistory.json { it.toJson() })
            put(BackupKeys.STOCK_SUBSCRIPTIONS, stockSubscriptions.json { it.toJson() })
            put(BackupKeys.SEARCH_SUBSCRIPTIONS, searchSubscriptions.json { it.toJson() })
            put(BackupKeys.SUBSCRIPTIONS, subscriptions.json { it.toJson() })
            put(BackupKeys.GROUPS, groups.json { it.toJson() })
            put(BackupKeys.GROUP_MEMBERS, groupMembers.json { it.toJson() })
            put(BackupKeys.SEARCH_GROUP_MEMBERS, searchGroupMembers.json { it.toJson() })
            put(BackupKeys.SAVED_POSTS, savedPosts.json { it.toJson() })
            put(BackupKeys.SAVED_FOLDERS, savedFolders.json { it.toJson() })
            put(BackupKeys.LIKED_POSTS, likedPosts.json { it.toJson() })
            put(BackupKeys.REPOST_FILTERS, repostFilters.json { it.toJson() })
            put(BackupKeys.REPLY_FILTERS, replyFilters.json { it.toJson() })
            put(BackupKeys.READ_POSITIONS, readPositions.json { it.toJson() })
            put(BackupKeys.ACCOUNTS, accounts.json { it.toJson() })
            put(BackupKeys.PROFILE_NOTES, profileNotes.json { it.toJson() })
            put(BackupKeys.ANTENNAS, antennas.json { it.toJson() })
            put(BackupKeys.LOCAL_POSTS, localPosts.json { it.toJson() })
            webDavHistory?.let { put(BackupKeys.WEBDAV_HISTORY, it.json { version -> version.toJson() }) }
        },
    )

    fun encode(): String = toJson().toString()

    companion object {
        /** Raised whenever an older build could misread a newer file. */
        const val FORMAT_VERSION = 2

        /** A file from before the header; every one still imports as it did. */
        const val LEGACY_FORMAT_VERSION = 0

        /** A reader meeting a higher number refuses the file rather than apply the part it understands. */
        fun isSupported(version: Int): Boolean = version <= FORMAT_VERSION

        /** The document in [text]; null when it is not a JSON object at all. */
        fun parse(text: String): BackupDocument? {
            val root = J.parse(text)
            if (root.raw !is JsonObject) return null
            return BackupDocument(
                formatVersion = root[BackupKeys.FORMAT_VERSION].int ?: LEGACY_FORMAT_VERSION,
                exportedAt = root[BackupKeys.EXPORTED_AT].string,
                appVersion = root[BackupKeys.APP_VERSION].string,
                archiveAnnotations = root[BackupKeys.ARCHIVE_ANNOTATIONS].takeIf { it.exists }?.let { ArchiveAnnotationRow.section(it) },
                settings = (root[BackupKeys.SETTINGS].raw as? JsonObject)?.toMap(),
                threadsSubscriptions = root[BackupKeys.THREADS_SUBSCRIPTIONS].rows { ProfileSubscriptionRow.from(it) },
                threadsLocalLikes = root[BackupKeys.THREADS_LOCAL_LIKES].rows { LocalMarkRow.from(it) },
                blueskySubscriptions = root[BackupKeys.BLUESKY_SUBSCRIPTIONS].rows { ProfileSubscriptionRow.from(it) },
                blueskyLocalLikes = root[BackupKeys.BLUESKY_LOCAL_LIKES].rows { LocalMarkRow.from(it) },
                mastodonSubscriptions = root[BackupKeys.MASTODON_SUBSCRIPTIONS].rows { ProfileSubscriptionRow.from(it) },
                tiktokSubscriptions = root[BackupKeys.TIKTOK_SUBSCRIPTIONS].rows { TikTokSubscriptionRow.from(it) },
                instagramSubscriptions = root[BackupKeys.INSTAGRAM_SUBSCRIPTIONS].rows { InstagramSubscriptionRow.from(it) },
                redditSubscriptions = root[BackupKeys.REDDIT_SUBSCRIPTIONS].rows { NamedSubscriptionRow.from(it) },
                redditLocalVotes = root[BackupKeys.REDDIT_LOCAL_VOTES].rows { LocalMarkRow.from(it) },
                substackSubscriptions = root[BackupKeys.SUBSTACK_SUBSCRIPTIONS].rows { SubstackSubscriptionRow.from(it) },
                rssSubscriptions = root[BackupKeys.RSS_SUBSCRIPTIONS].rows { RssSubscriptionRow.from(it) },
                booruSubscriptions = root[BackupKeys.BOORU_SUBSCRIPTIONS].rows { NamedSubscriptionRow.from(it) },
                ehFavorites = root[BackupKeys.EH_FAVORITES].rows { EhFavoriteRow.from(it) },
                ehHistory = root[BackupKeys.EH_HISTORY].rows { EhHistoryRow.from(it) },
                stockSubscriptions = root[BackupKeys.STOCK_SUBSCRIPTIONS].rows { StockSubscriptionRow.from(it) },
                searchSubscriptions = root[BackupKeys.SEARCH_SUBSCRIPTIONS].rows { SearchSubscriptionRow.from(it) },
                subscriptions = root[BackupKeys.SUBSCRIPTIONS].rows { UserSubscriptionRow.from(it) },
                groups = root[BackupKeys.GROUPS].rows { GroupRow.from(it) },
                groupMembers = root[BackupKeys.GROUP_MEMBERS].rows { GroupMemberRow.from(it) },
                searchGroupMembers = root[BackupKeys.SEARCH_GROUP_MEMBERS].rows { SearchGroupMemberRow.from(it) },
                savedPosts = root[BackupKeys.SAVED_POSTS].rows { SavedPostRow.from(it) },
                savedFolders = root[BackupKeys.SAVED_FOLDERS].rows { SavedFolderRow.from(it) },
                likedPosts = root[BackupKeys.LIKED_POSTS].rows { LikedPostRow.from(it) },
                repostFilters = root[BackupKeys.REPOST_FILTERS].rows { FeedFilterRow.from(it) },
                replyFilters = root[BackupKeys.REPLY_FILTERS].rows { FeedFilterRow.from(it) },
                readPositions = root[BackupKeys.READ_POSITIONS].rows { ReadPositionRow.from(it) },
                accounts = root[BackupKeys.ACCOUNTS].rows { AccountRow.from(it) },
                profileNotes = root[BackupKeys.PROFILE_NOTES].rows { ProfileNoteRow.from(it) },
                antennas = root[BackupKeys.ANTENNAS].rows { AntennaRow.from(it) },
                localPosts = root[BackupKeys.LOCAL_POSTS].rows { LocalPostRow.from(it) },
                webDavHistory = root[BackupKeys.WEBDAV_HISTORY].takeIf { it.exists }?.let { WebDavVersion.list(it) },
            )
        }

        private fun total(vararg sections: List<*>?): Int? =
            sections.filterNotNull().takeIf { it.isNotEmpty() }?.sumOf { it.size }
    }
}

/** A version a WebDAV sync archived on the server before replacing the document. */
data class WebDavVersion(val file: String, val at: String) {
    fun toJson(): JsonObject = jsonRow("file" to file, "at" to at)

    companion object {
        private val name = Regex("^\\d+-[a-f0-9]{64}\\.json$")

        /** As XTA reads them: the first 20, each named the way a sync names it and dated. */
        fun list(raw: J): List<WebDavVersion> = raw.list.take(20).mapNotNull { row ->
            val file = row["file"].string?.takeIf(name::matches) ?: return@mapNotNull null
            val at = row["at"].string?.takeIf { XtaTime.parse(it) != null } ?: return@mapNotNull null
            WebDavVersion(file, at)
        }
    }
}

/** The document's top-level keys. Load-bearing: a reader's file imports only while they stay put. */
object BackupKeys {
    const val ARCHIVE_ANNOTATIONS = "archiveAnnotations"
    const val FORMAT_VERSION = "formatVersion"
    const val EXPORTED_AT = "exportedAt"
    const val APP_VERSION = "appVersion"
    const val SETTINGS = "settings"
    const val THREADS_SUBSCRIPTIONS = "threadsSubscriptions"
    const val THREADS_LOCAL_LIKES = "threadsLocalLikes"
    const val BLUESKY_SUBSCRIPTIONS = "blueskySubscriptions"
    const val BLUESKY_LOCAL_LIKES = "blueskyLocalLikes"
    const val MASTODON_SUBSCRIPTIONS = "mastodonSubscriptions"
    const val TIKTOK_SUBSCRIPTIONS = "tiktokSubscriptions"
    const val INSTAGRAM_SUBSCRIPTIONS = "instagramSubscriptions"
    const val REDDIT_SUBSCRIPTIONS = "redditSubscriptions"
    const val REDDIT_LOCAL_VOTES = "redditLocalVotes"
    const val SUBSTACK_SUBSCRIPTIONS = "substackSubscriptions"
    const val RSS_SUBSCRIPTIONS = "rssSubscriptions"
    const val BOORU_SUBSCRIPTIONS = "booruSubscriptions"
    const val EH_FAVORITES = "ehFavorites"
    const val EH_HISTORY = "ehHistory"
    const val STOCK_SUBSCRIPTIONS = "stockSubscriptions"
    const val SEARCH_SUBSCRIPTIONS = "searchSubscriptions"
    const val SUBSCRIPTIONS = "subscriptions"
    const val GROUPS = "subscriptionGroups"
    const val GROUP_MEMBERS = "subscriptionGroupMembers"
    const val SEARCH_GROUP_MEMBERS = "searchGroupMembers"
    const val SAVED_POSTS = "tweets"
    const val SAVED_FOLDERS = "savedTweetFolders"
    const val LIKED_POSTS = "likedTweets"
    const val REPOST_FILTERS = "retweetFilters"
    const val REPLY_FILTERS = "replyFilters"
    const val READ_POSITIONS = "feedReadPositions"
    const val ACCOUNTS = "accounts"
    const val PROFILE_NOTES = "profileNotes"
    const val ANTENNAS = "antennas"
    const val LOCAL_POSTS = "localPosts"

    /** Only on the document a WebDAV sync keeps on the server. */
    const val WEBDAV_HISTORY = "_xtaWebDavHistory"
}
