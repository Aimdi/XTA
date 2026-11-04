package com.aimdi.xtanative.store.backup

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One of XTA's networks' sections: the rows of a table one of its plugins
 * owns (plugin_backup.dart), under the top-level [key], each keyed by [id] as
 * the table keys it.
 */
internal class NetworkSection<T>(
    val key: String,
    val category: BackupCategory,
    private val read: (J) -> T?,
    private val write: (T) -> JsonObject,
    private val id: (T) -> Any,
    private val rowsOf: (BackupDocument) -> List<T>?,
    private val withRows: BackupDocument.(List<T>?) -> BackupDocument,
) {
    fun count(document: BackupDocument): Int? = rowsOf(document)?.size

    fun json(document: BackupDocument): JsonElement = rowsOf(document).json(write)

    /** The rows as text, when [document] carries at least one. */
    fun text(document: BackupDocument): String? = rowsOf(document)?.takeIf { it.isNotEmpty() }?.json(write)?.toString()

    /** [document] with the rows [section] holds; none when it holds no list. */
    fun parsed(document: BackupDocument, section: J): BackupDocument = document.withRows(section.rows(read))

    /** [into] with [from]'s rows in place of its own, when [from] carries the section. */
    fun copied(from: BackupDocument, into: BackupDocument): BackupDocument = rowsOf(from)?.let { into.withRows(it) } ?: into

    fun merged(into: BackupDocument, incoming: BackupDocument): BackupDocument = into.withRows(upsert(rowsOf(into), rowsOf(incoming), id))

    fun removed(document: BackupDocument): BackupDocument = document.withRows(null)

    fun filled(document: BackupDocument): BackupDocument = document.withRows(rowsOf(document).orEmpty())
}

/** XTA's networks' sections, in the order XTA writes them. */
internal val NETWORK_SECTIONS: List<NetworkSection<*>> = listOf(
    NetworkSection(
        BackupKeys.THREADS_SUBSCRIPTIONS, BackupCategory.THREADS, ProfileSubscriptionRow::from, ProfileSubscriptionRow::toJson, { it.id },
        { it.threadsSubscriptions },
    ) { copy(threadsSubscriptions = it) },
    NetworkSection(
        BackupKeys.THREADS_LOCAL_LIKES, BackupCategory.THREADS_LIKES, LocalMarkRow::from, LocalMarkRow::toJson, { it.id },
        { it.threadsLocalLikes },
    ) { copy(threadsLocalLikes = it) },
    NetworkSection(
        BackupKeys.BLUESKY_SUBSCRIPTIONS, BackupCategory.BLUESKY, ProfileSubscriptionRow::from, ProfileSubscriptionRow::toJson, { it.id },
        { it.blueskySubscriptions },
    ) { copy(blueskySubscriptions = it) },
    NetworkSection(
        BackupKeys.BLUESKY_LOCAL_LIKES, BackupCategory.BLUESKY_LIKES, LocalMarkRow::from, LocalMarkRow::toJson, { it.id },
        { it.blueskyLocalLikes },
    ) { copy(blueskyLocalLikes = it) },
    NetworkSection(
        BackupKeys.MASTODON_SUBSCRIPTIONS, BackupCategory.MASTODON, ProfileSubscriptionRow::from, ProfileSubscriptionRow::toJson, { it.id },
        { it.mastodonSubscriptions },
    ) { copy(mastodonSubscriptions = it) },
    NetworkSection(
        BackupKeys.TIKTOK_SUBSCRIPTIONS, BackupCategory.TIKTOK_SUBSCRIPTIONS, TikTokSubscriptionRow::from, TikTokSubscriptionRow::toJson, { it.id },
        { it.tiktokSubscriptions },
    ) { copy(tiktokSubscriptions = it) },
    NetworkSection(
        BackupKeys.INSTAGRAM_SUBSCRIPTIONS, BackupCategory.INSTAGRAM_SUBSCRIPTIONS, InstagramSubscriptionRow::from, InstagramSubscriptionRow::toJson, { it.id },
        { it.instagramSubscriptions },
    ) { copy(instagramSubscriptions = it) },
    NetworkSection(
        BackupKeys.REDDIT_SUBSCRIPTIONS, BackupCategory.SUBREDDITS, NamedSubscriptionRow::from, NamedSubscriptionRow::toJson, { it.id },
        { it.redditSubscriptions },
    ) { copy(redditSubscriptions = it) },
    NetworkSection(
        BackupKeys.REDDIT_LOCAL_VOTES, BackupCategory.UPVOTES, LocalMarkRow::from, LocalMarkRow::toJson, { it.id },
        { it.redditLocalVotes },
    ) { copy(redditLocalVotes = it) },
    NetworkSection(
        BackupKeys.SUBSTACK_SUBSCRIPTIONS, BackupCategory.SUBSTACK, SubstackSubscriptionRow::from, SubstackSubscriptionRow::toJson, { it.id },
        { it.substackSubscriptions },
    ) { copy(substackSubscriptions = it) },
    NetworkSection(
        BackupKeys.RSS_SUBSCRIPTIONS, BackupCategory.RSS, RssSubscriptionRow::from, RssSubscriptionRow::toJson, { it.id },
        { it.rssSubscriptions },
    ) { copy(rssSubscriptions = it) },
    NetworkSection(
        BackupKeys.BOORU_SUBSCRIPTIONS, BackupCategory.BOORU_TAGS, NamedSubscriptionRow::from, NamedSubscriptionRow::toJson, { it.id },
        { it.booruSubscriptions },
    ) { copy(booruSubscriptions = it) },
    NetworkSection(
        BackupKeys.EH_FAVORITES, BackupCategory.EH_FAVORITES, EhFavoriteRow::from, EhFavoriteRow::toJson, { it.gallery.gid },
        { it.ehFavorites },
    ) { copy(ehFavorites = it) },
    NetworkSection(
        BackupKeys.EH_HISTORY, BackupCategory.EH_HISTORY, EhHistoryRow::from, EhHistoryRow::toJson, { it.gallery.gid },
        { it.ehHistory },
    ) { copy(ehHistory = it) },
    NetworkSection(
        BackupKeys.STOCK_SUBSCRIPTIONS, BackupCategory.STOCKS, StockSubscriptionRow::from, StockSubscriptionRow::toJson, { it.id },
        { it.stockSubscriptions },
    ) { copy(stockSubscriptions = it) },
)

/**
 * [incoming]'s rows over [current]'s, as XTA's import writes them (SQLite's
 * INSERT OR REPLACE): a row with a known key replaces the old one and goes
 * last; null [incoming] leaves [current] alone.
 */
fun <T, K> upsert(current: List<T>?, incoming: List<T>?, key: (T) -> K): List<T>? {
    if (incoming == null) return current
    val fresh = incoming.associateBy(key)
    return current.orEmpty().filterNot { key(it) in fresh } + fresh.values
}
