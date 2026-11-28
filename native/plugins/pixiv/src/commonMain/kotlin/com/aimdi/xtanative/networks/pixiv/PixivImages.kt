package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.repairs.RepairKind
import com.aimdi.xtanative.core.repairs.RepairSlot
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * How Pixiv's websites are asked (its image server, Pixivision): as a current
 * stable desktop Chrome in its reduced form, `pixiv.web_user_agent` in XTA's
 * published repairs. A bare `Mozilla/5.0` or a browser years out of date is
 * itself a bot tell; keep [USER_AGENT] and `repairs.json` in step.
 */
object PixivWeb {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/157.0.0.0 Safari/537.36"

    val USER_AGENT_SLOT = RepairSlot("pixiv.web_user_agent", USER_AGENT, RepairKind.HEADER)

    /** XTA's published repairs as they stand, which the plugin's runtime hands over when it starts. */
    @Volatile
    var published: () -> Map<String, String> = { emptyMap() }

    /** The agent in force: the published repair when it fits, else [USER_AGENT]. */
    val userAgent: String get() = USER_AGENT_SLOT.valueIn(published())
}

/** Every Pixiv value `repairs.json` may replace: the app API's and the websites'. */
val PIXIV_REPAIR_SLOTS = PixivApp.SLOTS + PixivWeb.USER_AGENT_SLOT

/** Which image server a reader's setting names. */
enum class PixivImageHostChoice { PIXIV, MIRROR, CUSTOM }

/** The sizes Pixiv serves every work in, smallest first. */
enum class PixivImageQuality { MEDIUM, LARGE, ORIGINAL }

/**
 * Where Pixiv's pictures come from and how to ask for them. Pixiv's image
 * server answers only requests carrying Pixiv's Referer ([HEADERS]); a reader
 * who cannot reach it may pick the public mirror or a server of their own,
 * which then serves the same paths.
 */
object PixivImages {
    /** Pixiv's own image server, where every artwork address points. */
    const val HOST = "i.pximg.net"

    /** The public reverse proxy of [HOST]. */
    const val MIRROR_HOST = "i.pixiv.re"

    /** What every picture request sends: Pixiv's image server refuses one without its Referer, and is asked as a browser ([PixivWeb]). */
    val HEADERS: Map<String, String> get() = mapOf("Referer" to "https://www.pixiv.net/", "User-Agent" to PixivWeb.userAgent)

    /** Which server a stored [host] setting names: blank is Pixiv's own. */
    fun hostChoice(host: String): PixivImageHostChoice = when (host.trim()) {
        "", HOST -> PixivImageHostChoice.PIXIV
        MIRROR_HOST -> PixivImageHostChoice.MIRROR
        else -> PixivImageHostChoice.CUSTOM
    }

    /**
     * The server a host setting names, with its port and path prefix; null when
     * it is unusable: blank, holding spaces, not https, or carrying a query or
     * credentials. Plain http would carry every work's address and Pixiv's
     * Referer in the clear.
     */
    fun parseHost(input: String): HttpUrl? {
        val text = input.trim()
        if (text.isEmpty() || text.any(Char::isWhitespace)) return null
        val full = if ("://" in text) text else "https://$text"
        if (!full.startsWith("https://", ignoreCase = true)) return null
        val url = full.toHttpUrlOrNull() ?: return null
        val plain = url.encodedQuery == null && url.fragment == null && url.username.isEmpty() && url.password.isEmpty()
        return url.takeIf { plain }
    }

    /**
     * [url] as served by [host] instead of Pixiv. Only [HOST] moves: the static
     * `s.pximg.net`, every other server, and everything when [host] is Pixiv's
     * own or unusable stay as they are.
     */
    fun url(url: String, host: String): String {
        val server = if (hostChoice(host) == PixivImageHostChoice.PIXIV) null else parseHost(host)
        val image = url.toHttpUrlOrNull()
        if (server == null || image == null || image.host != HOST) return url
        return HttpUrl.Builder()
            .scheme("https")
            .host(server.host)
            .port(server.port)
            .encodedPath(server.encodedPath.removeSuffix("/") + image.encodedPath)
            .encodedQuery(image.encodedQuery)
            .build()
            .toString()
    }

    /**
     * Whether [url] is Pixiv's stand-in for a deleted or restricted work. Those
     * pictures load fine and carry text such as 削除済み, so a list taking them
     * for real works fills with blank tiles.
     */
    fun isLimitPlaceholder(url: String?): Boolean {
        val lower = url?.lowercase() ?: return false
        return listOf("limit_unknown", "limit_r18", "limit_sanity", "/common/images/limit").any { it in lower }
    }

    /** A grid tile's picture: the medium preview, or the large image when asked for and Pixiv sent one. */
    fun tileUrl(illust: PixivIllust, quality: PixivImageQuality): String {
        val large = illust.largeUrl
        return if (quality == PixivImageQuality.MEDIUM || large.isNullOrEmpty()) illust.thumbnailUrl else large
    }

    /** [page] of [illust] in [quality]; each size falls back to one Pixiv did send. */
    fun pageUrl(illust: PixivIllust, page: Int, quality: PixivImageQuality): String = when (quality) {
        PixivImageQuality.MEDIUM -> illust.thumbUrlAt(page)
        PixivImageQuality.LARGE -> illust.viewerUrls.let { it[page.coerceIn(0, it.size - 1)] }
        PixivImageQuality.ORIGINAL -> illust.downloadUrlAt(page)
    }
}
