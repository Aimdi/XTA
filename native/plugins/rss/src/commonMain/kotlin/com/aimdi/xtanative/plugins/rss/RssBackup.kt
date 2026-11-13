package com.aimdi.xtanative.plugins.rss

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.rss.RssFeed
import com.aimdi.xtanative.networks.rss.RssFeeds
import com.aimdi.xtanative.store.backup.BackupKeys
import com.aimdi.xtanative.store.backup.RssSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.RssPrefs
import com.aimdi.xtanative.ui.plugins.PluginSettings
import com.aimdi.xtanative.ui.plugins.loaded
import java.time.Instant
import kotlinx.serialization.json.JsonArray

/**
 * The followed feeds as XTA's backup holds them besides its preferences: its
 * `rssSubscriptions` rows, which XTA keeps so a group can hold a feed. XTA's
 * tab reads the preference ([RssPrefs.feeds]); a backup's rows are followed
 * into it, the feeds already there keeping their place.
 */
internal object RssBackup {
    /** XTA's backup key for the rows: load-bearing, as a reader's file is read by it. */
    const val SECTION = BackupKeys.RSS_SUBSCRIPTIONS

    fun rows(feeds: List<RssFeed>, now: Instant = Instant.now()): String =
        JsonArray(feeds.map { feed -> RssSubscriptionRow(feed.id, feed.feedUrl, feed.name, feed.siteUrl, feed.iconUrl, createdAt = XtaTime.sqlite(now)).toJson() }).toString()

    fun feeds(rows: String): List<RssFeed> = J.parse(rows).list.mapNotNull(RssSubscriptionRow::from)
        .filter { it.id.isNotEmpty() && it.feedUrl.isNotEmpty() }
        .map { RssFeed(id = it.id, feedUrl = it.feedUrl, name = it.name, siteUrl = it.siteUrl, iconUrl = it.iconUrl) }

    suspend fun export(settings: PluginSettings): String = rows(RssFeeds.decode(settings.loaded()[RssPrefs.feeds]))

    suspend fun import(settings: PluginSettings, rows: String) {
        val restored = feeds(rows)
        settings.update { it.with(RssPrefs.feeds, RssFeeds.encode(RssFeeds.restored(RssFeeds.decode(it[RssPrefs.feeds]), restored))) }
    }
}
