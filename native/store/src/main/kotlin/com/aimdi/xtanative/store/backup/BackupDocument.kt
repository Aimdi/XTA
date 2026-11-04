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
    /**
     * XTA Native's downloadable plugins' own sections, by the name each plugin
     * gives one ("bluesky.follows"), as the text it exported. XTA reads past them.
     * A section named by one of XTA's networks' keys ([BackupKeys.NETWORKS])
     * is not here: it is that network's rows, where XTA reads them.
     */
    val pluginSections: Map<String, String>? = null,
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
    fun counts(): Map<BackupCategory, Int> = (
        listOf(
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
        ) + NETWORK_SECTIONS.map { it.category to it.count(this) } + (BackupCategory.PLUGINS to pluginSections?.size)
    ).mapNotNull { (category, count) -> count?.takeIf { it > 0 }?.let { category to it } }.toMap()

    /**
     * Only the sections [choice] asks for, as XTA's export screen picks them.
     * Local posts always go; [settings] is left as given, since what it may
     * carry is the settings' own business (see `Settings.exported`). The
     * plugins' sections go with the follows, as XTA's plugins' tables do.
     */
    fun chosen(choice: ExportChoice): BackupDocument {
        fun <T> List<T>?.ifChosen(chosen: Boolean) = takeIf { chosen }
        val networks = choice.subscriptions
        val picked = copy(
            archiveAnnotations = archiveAnnotations.takeIf { choice.savedPosts },
            pluginSections = pluginSections.takeIf { networks },
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
        return if (networks) picked else picked.withoutNetworks(BackupKeys.NETWORKS)
    }

    /** [other]'s networks' sections in place of these, where [other] carries one. */
    fun withNetworks(other: BackupDocument): BackupDocument =
        NETWORK_SECTIONS.fold(this) { document, section -> section.copied(other, document) }

    /** [incoming]'s networks' rows over these, as XTA's import writes them ([upsert]); a section [incoming] does not carry stays. */
    fun withNetworksMerged(incoming: BackupDocument): BackupDocument =
        NETWORK_SECTIONS.fold(this) { document, section -> section.merged(document, incoming) }

    fun withoutNetworks(keys: Collection<String>): BackupDocument =
        NETWORK_SECTIONS.filter { it.key in keys }.fold(this) { document, section -> section.removed(document) }

    /** Every network's section, an empty one where this carries none, as XTA writes each of its tables. */
    fun withEveryNetwork(): BackupDocument = NETWORK_SECTIONS.fold(this) { document, section -> section.filled(document) }

    /** The rows under each of XTA's networks' keys, as the text of XTA's rows, by key; a section without a row is left out. */
    fun networkRows(): Map<String, String> =
        NETWORK_SECTIONS.mapNotNull { section -> section.text(this)?.let { section.key to it } }.toMap()

    private fun withNetworksRead(section: (key: String) -> J): BackupDocument =
        NETWORK_SECTIONS.fold(this) { document, network -> network.parsed(document, section(network.key)) }

    /**
     * What a restore writes. Reading positions go only when asked for: one
     * from another device marks posts as seen that this reader never saw.
     */
    fun forRestore(includeReadPositions: Boolean): BackupDocument = copy(readPositions = readPositions.takeIf { includeReadPositions })

    /**
     * The document as XTA writes it: every section in XTA's order, null where
     * it carries nothing; the plugins' sections only where there are some.
     */
    fun toJson(): JsonObject = JsonObject(
        buildMap {
            put(BackupKeys.ARCHIVE_ANNOTATIONS, archiveAnnotations?.let { notes -> JsonObject(notes.mapValues { it.value.toJson() }) } ?: JsonNull)
            put(BackupKeys.FORMAT_VERSION, JsonPrimitive(formatVersion))
            put(BackupKeys.EXPORTED_AT, jsonOf(exportedAt))
            put(BackupKeys.APP_VERSION, jsonOf(appVersion))
            put(BackupKeys.SETTINGS, settings?.let(::JsonObject) ?: JsonNull)
            NETWORK_SECTIONS.forEach { put(it.key, it.json(this@BackupDocument)) }
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
            pluginSections?.let { put(BackupKeys.PLUGIN_SECTIONS, JsonObject(it.mapValues { (_, text) -> JsonPrimitive(text) })) }
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
                pluginSections = texts(root[BackupKeys.PLUGIN_SECTIONS]),
                webDavHistory = root[BackupKeys.WEBDAV_HISTORY].takeIf { it.exists }?.let { WebDavVersion.list(it) },
            ).withNetworksRead { root[it] }
        }

        /**
         * A document carrying [rows]: texts of XTA's rows by its networks'
         * keys, as a plugin keeps them, read as a file is. A name no network
         * has, or a text holding no list of rows, carries nothing.
         */
        fun ofNetworkRows(rows: Map<String, String>): BackupDocument = BackupDocument().withNetworksRead { key -> J.parse(rows[key] ?: "") }

        /** A section of texts by name; null when the file has none. */
        private fun texts(section: J): Map<String, String>? =
            (section.raw as? JsonObject)?.let { section.keys.mapNotNull { name -> section[name].string?.let { name to it } }.toMap() }

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

    /** Where XTA writes its networks' tables, the ones its plugins own, from [THREADS_SUBSCRIPTIONS] to [STOCK_SUBSCRIPTIONS]. */
    val NETWORKS: Set<String> = NETWORK_SECTIONS.mapTo(LinkedHashSet()) { it.key }

    /** XTA Native's alone: the plugins' sections. */
    const val PLUGIN_SECTIONS = "pluginSections"

    /** Only on the document a WebDAV sync keeps on the server. */
    const val WEBDAV_HISTORY = "_xtaWebDavHistory"
}
