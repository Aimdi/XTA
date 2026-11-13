package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruCredentials
import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruFilter
import com.aimdi.xtanative.networks.booru.BooruHosts
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruPresets
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.networks.booru.BooruRating
import com.aimdi.xtanative.networks.booru.BooruRatingRules
import com.aimdi.xtanative.networks.booru.BooruSettings
import com.aimdi.xtanative.networks.booru.BooruTagSort
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import com.aimdi.xtanative.store.settings.BooruEngine as StoredEngine
import com.aimdi.xtanative.store.settings.BooruRating as StoredRating
import com.aimdi.xtanative.store.settings.BooruTagSort as StoredTagSort

/**
 * What the reader chose for the boorus, read from the app's settings, where
 * XTA keeps them ([BooruPrefs], its keys): the site in use, its login, its
 * rating cap and blacklist (booru_credentials.dart, booru_client.dart).
 */
internal object BooruConfig {
    fun engine(settings: Settings): BooruEngine = BooruEngine.parse(settings[BooruPrefs.engine].stored) ?: BooruPresets.DEFAULT.engine

    /** The host requests go to: the one set, else the first preset (booruHostOf). */
    fun host(settings: Settings): String = BooruHosts.normalise(settings[BooruPrefs.host]).ifEmpty { BooruPresets.DEFAULT.host }

    /** The rating cap; XTA seeds General, safe search's default. */
    fun maxRating(settings: Settings): BooruRating = BooruRating.parse(settings[BooruPrefs.maxRating].stored) ?: BooruRating.GENERAL

    /** The blacklist entries kept, as typed and normalised. */
    fun muted(settings: Settings): Set<String> = BooruFilter.blacklist(strings(settings[BooruPrefs.mutedTags]))

    /** What the client reads before each request: the site, the credentials with the host they belong to, the cap and the blacklist. */
    fun client(settings: Settings): BooruSettings = BooruSettings(
        engine = engine(settings),
        host = host(settings),
        credentials = credentials(settings),
        maxRating = maxRating(settings),
        muted = muted(settings),
    )

    fun tagSort(settings: Settings): BooruTagSort = if (settings[BooruPrefs.tagSort] == StoredTagSort.COUNT) BooruTagSort.COUNT else BooruTagSort.NAME

    fun display(settings: Settings): BooruDisplay = BooruDisplay(
        columns = settings[BooruPrefs.gridColumns],
        smallThumbnails = settings[BooruPrefs.smallThumbnails],
        originalInViewer = settings[BooruPrefs.originalInViewer],
        tileDetails = settings[BooruPrefs.tileDetails],
        blurExplicit = settings[BooruPrefs.blurExplicit],
    )

    // ---- Credentials: one pair, for the host it was entered for ----

    /** The host the saved login and key belong to; empty when there is none. */
    fun credentialsHost(settings: Settings): String = BooruHosts.normalise(settings[BooruPrefs.credentialsHost])

    private fun hasCredentials(settings: Settings): Boolean = listOf(BooruPrefs.login, BooruPrefs.apiKey).any { settings[it].isNotBlank() }

    private fun credentials(settings: Settings): BooruCredentials? =
        if (hasCredentials(settings)) BooruCredentials(credentialsHost(settings), settings[BooruPrefs.login], settings[BooruPrefs.apiKey]) else null

    /** The saved login or key ([pref]) when it belongs to [host] (the host in use by default), else empty. */
    fun credential(settings: Settings, pref: Pref<String>, host: String = host(settings)): String =
        if (credentialsHost(settings) == host) settings[pref] else ""

    /** The host saved credentials belong to when it is not the host in use; null when there are none, or they are its own. */
    fun credentialsElsewhere(settings: Settings): String? {
        val owner = credentialsHost(settings)
        return owner.takeIf { hasCredentials(settings) && it.isNotEmpty() && it != host(settings) }
    }

    /** [value] saved as the login or key ([pref]) of the host in use; a pair saved for another host goes first, so its other half cannot follow. */
    fun withCredential(settings: Settings, pref: Pref<String>, value: String): Settings {
        val host = host(settings)
        val cleared = if (credentialsHost(settings) == host) settings else settings.with(BooruPrefs.login, "").with(BooruPrefs.apiKey, "")
        return cleared.with(pref, value).with(BooruPrefs.credentialsHost, host)
    }

    /** Credentials saved before they were tied to a host belong to the host in use, where they have been going (migrateBooruCredentialsHost). */
    fun withCredentialsHost(settings: Settings): Settings =
        if (credentialsHost(settings).isNotEmpty() || !hasCredentials(settings)) settings else settings.with(BooruPrefs.credentialsHost, host(settings))

    // ---- The blacklist, as XTA's BooruMuteStore keeps it ----

    fun withMuted(settings: Settings, entry: String): Settings = muteList(settings, muted(settings) + listOfNotNull(BooruQuery.normalise(entry)))

    fun withoutMuted(settings: Settings, entry: String): Settings {
        val gone = BooruQuery.normalise(entry) ?: return settings
        return muteList(settings, muted(settings) - gone)
    }

    private fun muteList(settings: Settings, entries: Set<String>): Settings = settings.with(BooruPrefs.mutedTags, json(entries.sorted()))

    // ---- Conversions between the stored choices and the reading layer's ----

    fun stored(engine: BooruEngine): StoredEngine = StoredEngine.parse(engine.id) ?: StoredEngine.DANBOORU

    fun stored(rating: BooruRating): StoredRating = StoredRating.parse(rating.code) ?: StoredRating.GENERAL

    fun stored(sort: BooruTagSort): StoredTagSort = if (sort == BooruTagSort.COUNT) StoredTagSort.COUNT else StoredTagSort.NAME

    /** A JSON list of strings, as XTA keeps its lists; none when [text] is no such list. */
    fun strings(text: String): List<String> = J.parse(text).list.mapNotNull { it.string }

    fun json(strings: List<String>): String = JsonArray(strings.map(::JsonPrimitive)).toString()
}

/** How posts are laid out and which file sizes are fetched (booru_display.dart). */
internal data class BooruDisplay(
    /** Grid columns; 0 fits as many as the width allows. */
    val columns: Int = 0,
    val smallThumbnails: Boolean = false,
    val originalInViewer: Boolean = false,
    val tileDetails: Boolean = true,
    val blurExplicit: Boolean = false,
) {
    /** The picture for a tile [pixelWidth] wide: the preview when small thumbnails are on, else the smallest size that fills the tile. */
    fun tileUrl(post: BooruPost, pixelWidth: Int?): String = when {
        smallThumbnails -> post.thumbnailUrl
        pixelWidth == null -> post.catalogUrl
        else -> post.tileUrlFor(pixelWidth)
    }

    fun viewerUrl(post: BooruPost): String = if (originalInViewer) post.originalUrl else post.displayUrl

    fun blurs(post: BooruPost): Boolean = blurExplicit && post.rating in BooruRatingRules.BLURRED

    /**
     * Grid columns for a [widthDp] wide grid: the reader's, else as many as fit
     * (pluginGalleryColumns), artwork kept readable with large text ([largeText]).
     */
    fun columnsFor(widthDp: Float, largeText: Boolean): Int =
        if (columns > 0) columns else (widthDp / (if (largeText) 240f else 152f)).toInt().coerceIn(1, 4)

    companion object {
        /** The choices the grid columns setting offers; 0 fits the screen. */
        val COLUMN_CHOICES: List<Int> = listOf(0, 2, 3, 4, 5)
    }
}
