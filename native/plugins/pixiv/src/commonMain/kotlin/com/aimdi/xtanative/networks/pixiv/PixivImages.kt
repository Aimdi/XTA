package com.aimdi.xtanative.networks.pixiv

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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

    /** What every picture request sends: Pixiv's image server refuses one without its Referer. */
    val HEADERS: Map<String, String> = mapOf("Referer" to "https://www.pixiv.net/", "User-Agent" to "Mozilla/5.0")

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
