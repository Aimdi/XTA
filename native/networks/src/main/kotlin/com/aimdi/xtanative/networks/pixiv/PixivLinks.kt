package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.x.http.XHttp
import java.io.IOException
import okhttp3.HttpUrl

/** Something a Pixiv link, or a bare id, names. */
sealed interface PixivLinkRef {
    data class Artwork(val id: Long) : PixivLinkRef

    /** A creator, on the profile tab the link names (`works`, `novels`, `bookmarks` or `following`). */
    data class User(val id: Long, val tab: String? = null) : PixivLinkRef

    /** A tag page, which opens as a search for the tag: of novels when it was the tag's novels. */
    data class Tag(val tag: String, val novels: Boolean = false) : PixivLinkRef

    /** A `pixiv.me/<name>` short link; only asking pixiv.me says where it goes ([PixivLinks.resolve]). */
    data class ShortLink(val name: String) : PixivLinkRef {
        val url: HttpUrl get() = HttpUrl.Builder().scheme("https").host("pixiv.me").addPathSegment(name).build()
    }

    /** A page that also lives on the web, with the address to share or to hand a browser. */
    sealed interface WebPage : PixivLinkRef {
        val webUrl: String
    }

    /** An illustration or manga series. */
    data class Series(val id: Long, val userId: Long) : WebPage {
        override val webUrl: String get() = pixivSeriesUrl(userId, id)
    }

    data class Novel(val id: Long) : WebPage {
        override val webUrl: String get() = pixivNovelUrl(id)
    }

    data class NovelSeries(val id: Long) : WebPage {
        override val webUrl: String get() = pixivNovelSeriesUrl(id)
    }

    /** A Pixivision article, in the language its link was written for. */
    data class PixivisionPage(val id: Long, val language: String = "en") : WebPage {
        override val webUrl: String get() = Pixivision.articleUrl(id, language)
    }
}

/** What Pixiv's links name, on pixiv.net, pixiv.me, pixivision.net, the image server and the `pixiv://` app scheme. */
object PixivLinks {
    private val imageFolders = setOf("img-original", "img-master", "custom-thumb", "img-zip-ugoira")
    private val leadingId = Regex("""^(\d+)_""")

    /** What [input] names: a bare number is a work; a link may be any [PixivLinkRef]. Null for anything else. */
    fun parse(input: String): PixivLinkRef? {
        val text = input.trim()
        if (text.isEmpty()) return null
        text.toLongOrNull()?.let { return PixivLinkRef.Artwork(it) }
        return linkUri(text)?.let(::refOf)
    }

    /**
     * Where a pixiv.me short link points, read from one request that does not
     * follow the redirect. Only a page on pixiv.net is trusted; anything else,
     * or a failed request, is null.
     */
    suspend fun resolve(http: XHttp, ref: PixivLinkRef.ShortLink): PixivLinkRef? {
        val response = try {
            http.get(ref.url, emptyMap(), followRedirects = false)
        } catch (_: IOException) {
            return null
        }
        val location = response.header("location")?.takeIf { response.code in 300..399 } ?: return null
        val target = ref.url.resolve(location)?.takeIf(::isPixivNetPage) ?: return null
        return parse(target.toString())?.takeUnless { it is PixivLinkRef.ShortLink }
    }

    /** pixiv.net and pixiv.me, with any subdomain. */
    fun isWebHost(host: String): Boolean = isPixivHost(host.lowercase())

    /** The image server, whose file names start with their work's id. */
    fun isImageHost(host: String): Boolean = host.lowercase().let { it == "pximg.net" || it.endsWith(".pximg.net") }

    /** pixivision.net, Pixiv's magazine, with any subdomain. */
    fun isPixivisionHost(host: String): Boolean = host.lowercase().let { it == "pixivision.net" || it.endsWith(".pixivision.net") }

    private fun isPixivHost(host: String): Boolean =
        host == "pixiv.net" || host.endsWith(".pixiv.net") || host == "pixiv.me" || host.endsWith(".pixiv.me")

    private fun isPixivNetPage(url: HttpUrl): Boolean = url.host == "pixiv.net" || url.host.endsWith(".pixiv.net")

    /** A link as typed or pasted: a full address, a host without its scheme, or a path on pixiv.net. */
    private fun linkUri(text: String): PixivUri? {
        if ("://" in text) return PixivUri.parse(text)
        val first = text.substringBefore('/').lowercase()
        val known = isPixivHost(first) || isImageHost(first) || isPixivisionHost(first)
        return PixivUri.parse(if (known) "https://$text" else "https://www.pixiv.net/$text")
    }

    private fun refOf(uri: PixivUri): PixivLinkRef? {
        if (uri.scheme == "pixiv") return appSchemeRef(uri)
        val host = uri.host
        return when {
            isImageHost(host) -> imageFileRef(uri.segments)
            isPixivisionHost(host) -> pixivisionRef(uri.segments)
            !isPixivHost(host) -> null
            else -> queryRef(uri) ?: if (host == "pixiv.me") shortRef(uri.segments) else pathRef(uri.segments)
        }
    }

    /** `pixiv://illusts/<id>`, `pixiv://users/<id>` and `pixiv://novels/<id>`. */
    private fun appSchemeRef(uri: PixivUri): PixivLinkRef? {
        val id = uri.segments.lastOrNull()?.let(::positiveId) ?: return null
        return when (uri.host) {
            "illusts", "illust" -> PixivLinkRef.Artwork(id)
            "users", "user" -> PixivLinkRef.User(id)
            "novels", "novel" -> PixivLinkRef.Novel(id)
            else -> null
        }
    }

    /** A file such as `…/img-original/img/2026/07/01/00/00/00/123_p0.png` carries its work's id before the first underscore. */
    private fun imageFileRef(segments: List<String>): PixivLinkRef? {
        if (segments.none { it in imageFolders }) return null
        return leadingId.find(segments.last())?.groupValues?.get(1)?.let(::positiveId)?.let { PixivLinkRef.Artwork(it) }
    }

    /** `pixivision.net/<language>/a/<id>`. */
    private fun pixivisionRef(segments: List<String>): PixivLinkRef? {
        val at = segments.indexOf("a")
        val id = (if (at < 0) null else segments.getOrNull(at + 1))?.let(::positiveId) ?: return null
        return if (at > 0) PixivLinkRef.PixivisionPage(id, segments[at - 1]) else PixivLinkRef.PixivisionPage(id)
    }

    /** The old PHP pages: `member_illust.php?illust_id=`, `member.php?id=` and `novel/show.php?id=`. */
    private fun queryRef(uri: PixivUri): PixivLinkRef? {
        positiveId(uri.query("illust_id"))?.let { return PixivLinkRef.Artwork(it) }
        val id = positiveId(uri.query("id")) ?: return null
        return when (uri.segments.lastOrNull()) {
            "member.php", "member_illust.php" -> PixivLinkRef.User(id)
            "show.php" -> if ("novel" in uri.path) PixivLinkRef.Novel(id) else null
            else -> null
        }
    }

    private fun shortRef(segments: List<String>): PixivLinkRef? =
        segments.singleOrNull()?.takeUnless { it.endsWith(".php") }?.let { PixivLinkRef.ShortLink(it) }

    private fun pathRef(path: List<String>): PixivLinkRef? {
        val segments = if (path.firstOrNull() == "en") path.drop(1) else path
        if (segments.size < 2) return null
        return namedPathRef(segments) ?: idPathRef(segments)
    }

    /** Paths whose second segment is not the id: tags, series and novel series. */
    private fun namedPathRef(segments: List<String>): PixivLinkRef? = when {
        segments[0] == "tags" -> segments[1].trim().ifEmpty { null }?.let { PixivLinkRef.Tag(it, novels = segments.getOrNull(2) == "novels") }
        segments[0] == "novel" && segments[1] == "series" && segments.size > 2 -> positiveId(segments[2])?.let { PixivLinkRef.NovelSeries(it) }
        (segments[0] == "user" || segments[0] == "users") && segments.size > 3 && segments[2] == "series" -> {
            val userId = positiveId(segments[1])
            val id = positiveId(segments[3])
            if (userId == null || id == null) null else PixivLinkRef.Series(id, userId)
        }
        else -> null
    }

    private fun idPathRef(segments: List<String>): PixivLinkRef? {
        val id = positiveId(segments[1]) ?: return null
        return when (segments[0]) {
            "artworks", "artwork", "illust", "i" -> PixivLinkRef.Artwork(id)
            "users", "user", "u" -> PixivLinkRef.User(id, profileTab(segments.getOrNull(2)))
            "n" -> PixivLinkRef.Novel(id)
            else -> null
        }
    }

    /** The profile tab a page under `/users/<id>/` shows. */
    private fun profileTab(page: String?): String? = when (page) {
        "artworks", "illustrations", "manga" -> "works"
        "novels" -> "novels"
        "bookmarks" -> "bookmarks"
        "following" -> "following"
        else -> null
    }

    private fun positiveId(text: String?): Long? = text?.trim()?.toLongOrNull()?.takeIf { it > 0 }
}
