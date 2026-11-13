package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruHosts
import com.aimdi.xtanative.networks.booru.BooruPreset
import com.aimdi.xtanative.networks.booru.BooruPresets
import com.aimdi.xtanative.networks.booru.BooruRating
import com.aimdi.xtanative.networks.booru.BooruRatingRules
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The cap a site gets once it is chosen: its own, General, or Explicit once the reader confirms being an adult who wants such posts. */
internal sealed interface SiteCap {
    /** The site kept its own cap, or one was set without asking. */
    data object Settled : SiteCap

    /** A mostly explicit site seen for the first time: Explicit if the reader confirms, else General. */
    data class AskAdult(val site: String) : SiteCap
}

/**
 * The boorus to choose from, and each one's own settings (booru_site_profiles.dart,
 * booru_engines.dart): the sites XTA has built in, those the reader added, and
 * for each site in use the rating cap, blacklist, login and key it keeps while
 * another is in use. Followed tags are shared by every site.
 */
internal object BooruSites {
    /** The added sites, as XTA keeps them ({id, name, engine, host}); an entry naming no host or engine is skipped. */
    fun custom(settings: Settings): List<BooruPreset> = J.parse(settings[BooruPrefs.customSites]).list.mapNotNull { row ->
        val host = BooruHosts.normalise(row["host"].string.orEmpty())
        val engine = BooruEngine.parse(row["engine"].string)
        if (host.isEmpty() || engine == null) return@mapNotNull null
        BooruPreset(
            id = row["id"].string?.trim()?.ifEmpty { null } ?: customId(host),
            name = row["name"].string?.trim()?.ifEmpty { null } ?: BooruHosts.displayName(host),
            engine = engine,
            host = host,
        )
    }

    /** The built-in sites and those added, as links to their posts are recognised (booruKnownSites). */
    fun known(settings: Settings): List<BooruPreset> = BooruPresets.ALL + custom(settings)

    /** An added site's id: `custom:` and its host's name. */
    fun customId(host: String): String = BooruHosts.displayName(host).let { if (it.isEmpty()) "" else "custom:$it" }

    /** [site] added, in place of one with its id or host. */
    fun withCustom(settings: Settings, site: BooruPreset): Settings {
        val added = site.copy(id = site.id.ifEmpty { customId(site.host) })
        val sites = custom(settings).filter { it.id != added.id && it.host != added.host } + added
        return settings.with(BooruPrefs.customSites, encode(sites))
    }

    /** [site] removed, with what it kept; when it was in use, the first built-in site is chosen in its place. */
    fun withoutCustom(settings: Settings, site: BooruPreset): Settings {
        val listed = settings.with(BooruPrefs.customSites, encode(custom(settings).filter { it.id != site.id }))
        val moved = if (settings[BooruPrefs.preset] == site.id) choose(listed, BooruPresets.DEFAULT).first else listed
        return forget(moved, BooruHosts.normalise(site.host))
    }

    /** A built-in site on [host], which an added site on it gives way to. */
    fun builtIn(host: String): BooruPreset? = BooruPresets.ALL.firstOrNull { it.host == BooruHosts.normalise(host) }

    /**
     * [preset] in use, with the cap, blacklist, login and key it kept brought
     * back and those of the site before it stored. A site seen for the first
     * time keeps the blacklist in use and gets General, except one whose posts
     * are mostly explicit, where General would show almost nothing: there it is
     * Explicit if the reader already allowed adult posts, and asked otherwise.
     */
    fun choose(settings: Settings, preset: BooruPreset): Pair<Settings, SiteCap> {
        val current = BooruConfig.maxRating(settings)
        val (swapped, own) = swap(settings, from = BooruConfig.host(settings), to = BooruHosts.normalise(preset.host))
        val chosen = swapped.with(BooruPrefs.preset, preset.id).with(BooruPrefs.engine, BooruConfig.stored(preset.engine)).with(BooruPrefs.host, preset.host)
        return when {
            own -> chosen to SiteCap.Settled
            !BooruRatingRules.isMostlyExplicit(preset.host) -> withCap(chosen, BooruRating.GENERAL) to SiteCap.Settled
            !BooruRatingRules.needsAdultConsent(current, BooruRating.EXPLICIT) -> withCap(chosen, BooruRating.EXPLICIT) to SiteCap.Settled
            else -> withCap(chosen, BooruRating.GENERAL) to SiteCap.AskAdult(preset.name)
        }
    }

    fun withCap(settings: Settings, cap: BooruRating): Settings = settings.with(BooruPrefs.maxRating, BooruConfig.stored(cap))

    /**
     * The settings in use stored under [from], and [to]'s own brought back;
     * whether [to] had settings of its own. Staying on a site swaps nothing.
     */
    fun swap(settings: Settings, from: String, to: String): Pair<Settings, Boolean> {
        if (from == to) return settings to false
        val kept = keep(settings, from)
        val profile = map(kept[BooruPrefs.siteSettings])[to] as? JsonObject
        val login = map(kept[BooruPrefs.siteCredentials])[to] as? JsonObject
        val withLogin = if (login == null) {
            kept
        } else {
            kept.with(BooruPrefs.login, J(login)["login"].string.orEmpty())
                .with(BooruPrefs.apiKey, J(login)["key"].string.orEmpty())
                .with(BooruPrefs.credentialsHost, to)
        }
        if (profile == null) return withLogin to false
        val cap = BooruRating.parse(J(profile)["rating"].string) ?: BooruRating.GENERAL
        return withCap(withLogin, cap).with(BooruPrefs.mutedTags, J(profile)["muted"].string ?: "[]") to true
    }

    /** What [host] kept, dropped, for a site that is removed. */
    fun forget(settings: Settings, host: String): Settings = settings
        .with(BooruPrefs.siteSettings, JsonObject(map(settings[BooruPrefs.siteSettings]) - host).toString())
        .with(BooruPrefs.siteCredentials, JsonObject(map(settings[BooruPrefs.siteCredentials]) - host).toString())

    /** The settings in use stored under [host]; its login only while it has one. */
    private fun keep(settings: Settings, host: String): Settings {
        val profile = buildJsonObject {
            put("rating", BooruConfig.maxRating(settings).code)
            put("muted", settings[BooruPrefs.mutedTags])
        }
        val login = BooruConfig.credential(settings, BooruPrefs.login, host)
        val key = BooruConfig.credential(settings, BooruPrefs.apiKey, host)
        val logins = map(settings[BooruPrefs.siteCredentials]).let { kept ->
            if (login.isEmpty() && key.isEmpty()) kept - host else kept + (host to buildJsonObject { put("login", login); put("key", key) })
        }
        return settings
            .with(BooruPrefs.siteSettings, JsonObject(map(settings[BooruPrefs.siteSettings]) + (host to profile)).toString())
            .with(BooruPrefs.siteCredentials, JsonObject(logins).toString())
    }

    private fun map(text: String): Map<String, JsonElement> = (J.parse(text).raw as? JsonObject).orEmpty()

    private fun encode(sites: List<BooruPreset>): String = JsonArray(
        sites.map { site ->
            buildJsonObject {
                put("id", site.id)
                put("name", site.name)
                put("engine", site.engine.id)
                put("host", site.host)
            }
        },
    ).toString()

    /** The preset id XTA keeps once the host or engine is set by hand. */
    const val CUSTOM = "custom"
}
