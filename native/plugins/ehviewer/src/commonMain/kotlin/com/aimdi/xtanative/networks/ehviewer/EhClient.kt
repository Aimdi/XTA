package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The reads XTA makes of E-Hentai (or ExHentai, signed in): gallery lists,
 * a gallery's page sheet by sheet, its pages' images, and the `gdata` and
 * `tagsuggest` API. Read-only: nothing is favorited, rated, commented on or
 * uploaded.
 *
 * Every page is read as a desktop browser with [EhSettings.requestCookies],
 * which ask the site for the larger images and tiles the parser expects. The
 * [settings] are asked for on every read, so a changed setting applies to the
 * next one.
 */
class EhClient(
    private val http: XHttp,
    private val settings: () -> EhSettings = { EhSettings() },
) {
    /** What the reads go by now. */
    val current: EhSettings get() = settings()

    val host: String get() = current.host

    /** Signed in: originals can be opened. */
    val signedIn: Boolean get() = current.hasCookies

    /** Headers for loading the image at [url]; see [EhSettings.imageHeaders]. */
    fun imageHeaders(url: String): Map<String, String> = current.imageHeaders(url)

    /** The site's popular galleries; one page. */
    suspend fun popular(next: String? = null): Page<EhGallery> = list(next) { "${it.host}/popular".toHttpUrl() }

    /** The front page, newest first, in the categories the settings keep to. */
    suspend fun frontPage(next: String? = null): Page<EhGallery> = list(next) { now ->
        "${now.host}/".toHttpUrl().newBuilder().addQueryParameter("f_cats", EhCategory.excludeMask(now.categories).toString()).build()
    }

    /** A search for [query] under [filters]; the settings' categories when no filters are given. */
    suspend fun search(query: String, filters: EhSearchFilters? = null, next: String? = null): Page<EhGallery> = list(next) { now ->
        val chosen = filters ?: EhSearchFilters(now.categories)
        val request = EhQuery.build(query, EhCategory.excludeMask(chosen.categories), chosen.minRating, chosen.language.tag)
        now.host.toHttpUrl().newBuilder().encodedPath("/").apply { request.params.forEach { (name, value) -> addQueryParameter(name, value) } }.build()
    }

    /** A toplist over [period]. */
    suspend fun toplist(period: EhToplistPeriod, next: String? = null): Page<EhGallery> = list(next) { now ->
        "${now.host}/toplist.php".toHttpUrl().newBuilder().addQueryParameter("tl", period.tl.toString()).build()
    }

    /** New galleries in the account's watched tags: without cookies the site answers its sign-in page, so none. */
    suspend fun watched(next: String? = null): Page<EhGallery> = list(next) { "${it.host}/watched".toHttpUrl() }

    /**
     * A list's first page from [first], or the page [next] names: a page's
     * `next` link, which leads only to the site's own hosts.
     */
    private suspend fun list(next: String?, first: (EhSettings) -> HttpUrl): Page<EhGallery> {
        val now = current
        val url = when (next) {
            null -> first(now)
            else -> next.toHttpUrlOrNull()?.takeIf { EhSettings.isSiteUrl(next) } ?: throw IllegalArgumentException("Not a page of the site: $next")
        }
        val body = page(url, now)
        return parsing { EhParser.galleryList(body, now.host) }
    }

    /** One preview sheet (`?p=`, from 0) of a gallery's page. */
    suspend fun galleryDetail(gid: Long, token: String, previewSheet: Int = 0): EhGalleryDetail {
        val now = current
        val url = "${now.host}/g/$gid/$token/".toHttpUrl().newBuilder()
            .apply { if (previewSheet > 0) addQueryParameter("p", previewSheet.toString()) }.build()
        val body = page(url, now)
        return parsing { EhParser.galleryDetail(body, gid, token, now.host) ?: throw notAGallery(body, url) }
    }

    /** The tiles of one preview sheet. */
    suspend fun previewSheet(gid: Long, token: String, sheet: Int): List<EhPreview> = galleryDetail(gid, token, sheet).previews

    /** The tile, and so the page token, of [page], from the sheet it sits on if sheets hold [pagesPerSheet]. */
    suspend fun previewForPage(gid: Long, token: String, page: Int, pagesPerSheet: Int = EhPageResolver.DEFAULT_SHEET_SIZE): EhPreview? {
        if (page < 1) return null
        return previewSheet(gid, token, (page - 1) / pagesPerSheet).firstOrNull { it.page == page }
    }

    /**
     * The image page of [page], whose token a preview tile or a neighbouring
     * page gave. With [reloadKey] (a page's `nl`) the site picks another image server.
     */
    suspend fun imagePage(gid: Long, pageToken: String, page: Int, reloadKey: String? = null): EhImagePage {
        val now = current
        val url = "${now.host}/s/$pageToken/$gid-$page".toHttpUrl().newBuilder()
            .apply { if (!reloadKey.isNullOrEmpty()) addQueryParameter("nl", reloadKey) }.build()
        val body = page(url, now)
        return parsing { EhParser.imagePage(body, page, now.host) } ?: throw EhException(EhErrorKind.BAD_RESPONSE, "Could not read the image page: $url")
    }

    /** A gallery as the `gdata` API describes it, tags with their namespaces; null when the API does not know it. */
    suspend fun galleryMeta(gid: Long, token: String): EhGallery? {
        val body = api(
            buildJsonObject {
                put("method", "gdata")
                putJsonArray("gidlist") { addJsonArray { add(gid); add(token) } }
                put("namespace", 1)
            },
        )
        return EhParser.gdata(body).firstOrNull()
    }

    /** Tags the site knows that start like [text]: none for less than two letters, or on any failure. */
    suspend fun suggestTags(text: String): List<EhTag> {
        val query = text.trim()
        if (query.length < 2) return emptyList()
        return try {
            EhParser.tagSuggestions(api(buildJsonObject { put("method", "tagsuggest"); put("text", query) }))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun page(url: HttpUrl, now: EhSettings): String {
        val response = checked(http.get(url, pageHeaders(now), followRedirects = true), url)
        if (EhParser.isBanned(response.body)) throw EhException(EhErrorKind.BANNED, "Banned for too many page loads: $url")
        return response.body
    }

    private suspend fun api(body: JsonObject): String {
        val now = current
        val headers = buildMap {
            put("User-Agent", USER_AGENT)
            put("Accept", "application/json")
            if (now.hasCookies) put("Cookie", now.accountCookies)
        }
        val response = checked(http.post(API_URL, headers, body.toString(), "application/json"), API_URL)
        if (!J.parse(response.body).exists) throw EhException(EhErrorKind.BAD_RESPONSE, "$API_URL: no JSON")
        return response.body
    }

    private fun pageHeaders(now: EhSettings): Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Cookie" to now.requestCookies,
    )

    private fun checked(response: XResponse, url: HttpUrl): XResponse =
        if (response.isSuccessful) response else throw NetworkRequestException(NETWORK, response.code, "$url: ${response.code}")

    /** A gallery's address that answered no gallery: the content warning when the consent was not given, else a page that did not read. */
    private fun notAGallery(body: String, url: HttpUrl): EhException = when {
        EhParser.isContentWarning(body) -> EhException(EhErrorKind.CONTENT_WARNING, "The site warns before this gallery: $url")
        else -> EhException(EhErrorKind.BAD_RESPONSE, "Could not read the gallery: $url")
    }

    /** Pages are parsed away from the caller's thread, which may be the UI's. */
    private suspend fun <T> parsing(block: () -> T): T = withContext(Dispatchers.Default) { block() }

    companion object {
        /** The network's name in [NetworkRequestException]. */
        const val NETWORK = "ehviewer"

        /** A desktop browser, as XTA reads: the parser expects the desktop site's pages. */
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        val API_URL: HttpUrl = "https://api.e-hentai.org/api.php".toHttpUrl()
    }
}
