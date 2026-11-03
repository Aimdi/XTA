package com.aimdi.xtanative.store.backup

/** What a backup preview counts. [xtaName] is the name XTA gives the category. */
enum class BackupCategory(val xtaName: String) {
    ARCHIVE_ANNOTATIONS("archiveAnnotations"),
    SETTINGS("settings"),
    SUBSCRIPTIONS("subscriptions"),
    SUBSTACK("substack"),
    RSS("rss"),
    SUBREDDITS("subreddits"),
    STOCKS("stocks"),
    THREADS("threads"),
    BLUESKY("bluesky"),
    MASTODON("mastodon"),
    BOORU_TAGS("booruTags"),
    EH_FAVORITES("ehFavorites"),
    EH_HISTORY("ehHistory"),
    TIKTOK_SUBSCRIPTIONS("tiktokSubscriptions"),
    INSTAGRAM_SUBSCRIPTIONS("instagramSubscriptions"),
    GROUPS("groups"),
    GROUP_MEMBERS("groupMembers"),
    SAVED_POSTS("savedPosts"),
    FOLDERS("folders"),
    LIKED_POSTS("likedPosts"),
    FILTERS("filters"),
    READ_POSITIONS("readPositions"),
    UPVOTES("upvotes"),
    THREADS_LIKES("threadsLikes"),
    BLUESKY_LIKES("blueskyLikes"),
    ACCOUNTS("accounts"),
    PROFILE_NOTES("profileNotes"),
    ANTENNAS("antennas"),
    LOCAL_POSTS("localPosts"),
}

/**
 * The choices on XTA's export screen. Each covers the sections XTA puts
 * under it: [subscriptions] every network's follows and device-only likes
 * and votes, [savedPosts] the archive notes too, and [filters] the profile
 * notes and antennas beside the repost and reply filters.
 */
data class ExportChoice(
    val settings: Boolean = false,
    val subscriptions: Boolean = false,
    val groups: Boolean = false,
    val groupMembers: Boolean = false,
    val savedPosts: Boolean = false,
    val savedFolders: Boolean = false,
    val likedPosts: Boolean = false,
    val filters: Boolean = false,
    val readPositions: Boolean = false,
    /** The X sessions: tokens, so never by default. */
    val accounts: Boolean = false,
) {
    /** Members only mean something with the subscriptions and groups they join, so XTA offers them only with both. */
    val includesGroupMembers: Boolean get() = groupMembers && subscriptions && groups

    /** Nothing chosen: XTA offers no export then. */
    val isEmpty: Boolean
        get() = listOf(settings, subscriptions, groups, groupMembers, savedPosts, savedFolders, likedPosts, filters, readPositions, accounts).none { it }

    companion object {
        /** What a WebDAV sync uploads: everything a manual export of everything would, the X sessions only when asked. */
        fun everything(includeAccounts: Boolean) = ExportChoice(
            settings = true,
            subscriptions = true,
            groups = true,
            groupMembers = true,
            savedPosts = true,
            savedFolders = true,
            likedPosts = true,
            filters = true,
            readPositions = true,
            accounts = includeAccounts,
        )
    }
}
