package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** One source XTA publishes: enough to list it before its file is read. */
data class WebSourceListing(val id: String, val name: String, val description: String, val url: HttpUrl)

/**
 * The web sources XTA publishes (`sources.json` on its default branch), for
 * the Add source screen. Reading the list installs nothing.
 */
object WebSourceCatalogue {
    /** Where installs read the published list: XTA's, so both apps offer the same sources. */
    val DEFAULT_URL: HttpUrl = "https://raw.githubusercontent.com/Aimdi/XTA/HEAD/sources.json".toHttpUrl()

    /**
     * The entries of [body], a published list read from [base]. An entry that
     * is malformed, or whose file is not on HTTPS, is left out; so is one
     * without a `url`, which XTA resolved to the list itself.
     */
    fun parse(body: String, base: HttpUrl): List<WebSourceListing> {
        val decoded = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        return J(decoded)["sources"].list.mapNotNull { listing(it, base) }
    }

    private fun listing(entry: J, base: HttpUrl): WebSourceListing? {
        val id = entry["id"].string?.takeIf(WebSources::isSourceId) ?: return null
        val name = entry["name"].string?.trim().orEmpty().ifEmpty { null } ?: return null
        val url = entry["url"].string?.trim()?.ifEmpty { null }?.let(base::resolve) ?: return null
        if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        return WebSourceListing(id, name, entry["description"].string?.trim().orEmpty(), url)
    }
}

/**
 * A source as installed on a device: its file, where it is read again from,
 * the sites the reader agreed to, and a newer version waiting for the
 * reader to agree to a site it adds.
 */
data class InstalledWebSource(
    val source: WebSourceDefinition,
    val agreedHosts: List<String>,
    /** Where its file is read again from; null for one that cannot update. */
    val origin: HttpUrl? = null,
    val waiting: WebSourceDefinition? = null,
)

/**
 * How an installed source takes a newer file: at most once a day each
 * installed source's file is read again from where it came; keeping that
 * schedule, and the sources, is the device's part.
 */
object WebSourceUpdates {
    /** How often installed sources are read again. */
    const val INTERVAL_HOURS = 23L

    /**
     * [installed] after [fetched], its file read again: a newer version on
     * agreed sites replaces it; one asking for a new site waits for the
     * reader; anything else (not newer, another id, a file that no longer
     * reads) changes nothing, and is null.
     */
    fun updated(installed: InstalledWebSource, fetched: WebSourceFile): InstalledWebSource? {
        val next = (fetched as? WebSourceFile.Read)?.source ?: return null
        val newest = installed.waiting?.version ?: installed.source.version
        if (next.id != installed.source.id || next.version <= newest) return null
        return if (next.hosts.all { it in installed.agreedHosts }) {
            InstalledWebSource(source = next, agreedHosts = installed.agreedHosts, origin = installed.origin)
        } else {
            installed.copy(waiting = next)
        }
    }
}
