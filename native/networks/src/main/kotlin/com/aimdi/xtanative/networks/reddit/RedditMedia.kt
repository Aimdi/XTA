package com.aimdi.xtanative.networks.reddit

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What a Reddit URL points at (XTA's `reddit_media_urls.dart`). A post and a
 * comment ask the same question, a picture to show or a page to link to, and
 * Reddit's markup names no content type, so the answer comes from the host
 * and the extension rather than from a request per link.
 */
object RedditMedia {
    /** Hosts that serve the picture itself, whatever the path looks like. */
    val IMAGE_HOSTS = setOf("i.redd.it", "preview.redd.it", "i.imgur.com", "i.redditmedia.com")

    /** Hosts whose links are a video: a card can say so instead of offering a dead thumbnail. */
    val VIDEO_HOSTS = setOf("v.redd.it", "youtube.com", "youtu.be", "m.youtube.com", "streamable.com", "gfycat.com", "redgifs.com")

    private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

    /**
     * [url] when it is a picture that can be shown inline, else null. Imgur's
     * `.gifv` is a video player page; the `.gif` beside it is the animation.
     */
    fun imageUrl(url: String?): String? {
        val parsed = url?.toHttpUrlOrNull() ?: return null
        val path = parsed.encodedPath.lowercase()
        return when {
            path.endsWith(".gifv") -> url.takeIf { it.lowercase().endsWith(".gifv") }?.dropLast(1)
            bareHost(parsed.host) in IMAGE_HOSTS || imageExtensions.any(path::endsWith) -> url
            else -> null
        }
    }

    /** Whether [host] serves video rather than anything a card can show inline. */
    fun isVideoHost(host: String?): Boolean = host != null && bareHost(host) in VIDEO_HOSTS

    /**
     * A picture for [url], rewriting the viewer pages that have a predictable
     * file behind them (a Giphy share link, a single Imgur picture). Anything
     * that would need a request or a key to resolve is left a link.
     */
    fun embeddableImage(url: String?): String? {
        imageUrl(url)?.let { return it }
        val parsed = url?.toHttpUrlOrNull() ?: return null
        return when (bareHost(parsed.host)) {
            "giphy.com", "media.giphy.com", "i.giphy.com" -> giphyFromPath(parsed)
            "imgur.com", "m.imgur.com" -> imgurFromPath(parsed)
            else -> null
        }
    }

    /** `giphy.com/gifs/some-slug-<id>`: the id ends the slug, and Giphy serves its file with no key. */
    private fun giphyFromPath(url: HttpUrl): String? {
        val id = nameAfter(url.toString(), "gifs")?.substringAfterLast('-') ?: return null
        return if (giphyId.matches(id)) giphyFile(id) else null
    }

    /** The one picture of an Imgur page; an album or gallery needs the API to say which, so stays a link. */
    private fun imgurFromPath(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val id = segments.singleOrNull()?.takeIf { it !in setOf("a", "gallery", "t") } ?: return null
        return if (imgurId.matches(id)) "https://i.imgur.com/$id.jpeg" else null
    }

    /** The file for a Giphy id, which Reddit's own `giphy|id|size` token carries instead of a URL. */
    fun giphyFile(id: String): String = "https://media.giphy.com/media/$id/giphy.gif"

    private val giphyId = Regex("^[A-Za-z0-9]{6,}$")
    private val imgurId = Regex("^[A-Za-z0-9]{5,10}$")

    /**
     * Reddit's own media markdown, which its old site prints raw: a comment
     * posted with the GIF picker arrives as `![gif](giphy|l0HlvtIPzPdt2usKs|downsized)`.
     */
    val MEDIA_TOKEN = Regex("""!\[[a-z]*\]\((giphy|emote)\|([A-Za-z0-9_]+)(?:\|[^)]*)?\)""")

    /** The picture a media token names; an `emote` is a sticker with no public file. */
    fun tokenImage(match: MatchResult): String? =
        match.groupValues[2].takeIf { match.groupValues[1] == "giphy" && it.isNotEmpty() }?.let(::giphyFile)

    private fun bareHost(host: String): String = host.lowercase().removePrefix("www.")

    /**
     * [urls] with each picture once: the same file under another host or
     * width is one picture, `i.redd.it` preferred over `preview.redd.it`, else
     * the larger `width`. First-seen order is kept; different files stay apart.
     */
    fun collapseImageUrls(urls: Iterable<String>): List<String> {
        val result = mutableListOf<String>()
        val indexByKey = HashMap<String, Int>()
        for (url in urls) {
            val key = imageKey(url)
            val existing = key?.let(indexByKey::get)
            when {
                key == null -> result += url
                existing == null -> { indexByKey[key] = result.size; result += url }
                prefers(url, result[existing]) -> result[existing] = url
            }
        }
        return result
    }

    private fun imageKey(url: String): String? =
        url.toHttpUrlOrNull()?.pathSegments?.lastOrNull { it.isNotEmpty() }?.lowercase()

    private fun prefers(candidate: String, current: String): Boolean {
        val next = candidate.toHttpUrlOrNull() ?: return false
        val now = current.toHttpUrlOrNull() ?: return false
        val nextDirect = bareHost(next.host) == "i.redd.it"
        val nowDirect = bareHost(now.host) == "i.redd.it"
        return if (nextDirect != nowDirect) nextDirect else widthOf(next) > widthOf(now)
    }

    private fun widthOf(url: HttpUrl): Int = url.queryParameter("width")?.toIntOrNull() ?: -1

    private const val PLACEHOLDER_WORD = "(?:image|img|pic|picture|photo|gif|video|media|foto|bild)"
    private val placeholderWrappers = Regex("""[\[\](){}<>＜＞｛｝【】「」『』"'`]+""")
    private val placeholderBare = Regex("^$PLACEHOLDER_WORD$", RegexOption.IGNORE_CASE)
    private const val WRAP = """(?:&lt;|&gt;|[<>\[\](){}｛｝【】「」『』])+"""
    private val placeholderToken = Regex("$WRAP[$SPACE]*$PLACEHOLDER_WORD[$SPACE]*$WRAP", RegexOption.IGNORE_CASE)

    private fun decodeAngles(text: String) = text.replace("&lt;", "<").replace("&gt;", ">")

    /**
     * Whether [title] only announces the picture the card shows: meme
     * communities title image posts `<image>`, `>image>` or `[image]`.
     */
    fun isPlaceholderTitle(title: String): Boolean {
        val text = decodeAngles(title).trim().ifEmpty { return false }
        return placeholderBare.matches(text.replace(placeholderWrappers, "").trim().replace(trailingPunctuation, ""))
    }

    private val trailingPunctuation = Regex("[.!?,;:]+$")

    /**
     * [text] without its `>image>` and `<gif>` leftovers. The spaces a token
     * leaves are folded; the author's line breaks and paragraphs are not.
     */
    fun stripPlaceholderTokens(text: String): String = decodeAngles(text)
        .replace(placeholderToken, " ")
        .replace(spacesAndTabs, " ")
        .replace(spacedBreak, "\n")
        .replace(blankLines, "\n\n")
        .trim()

    private val spacesAndTabs = Regex("[ \t]+")
    private val spacedBreak = Regex(" ?\n ?")
    private val blankLines = Regex("\n{3,}")
    private val httpUrl = Regex("""https?://[^$SPACE\])<>]+""", RegexOption.IGNORE_CASE)
    private val doubleSpaces = Regex(" {2,}")
    private val trailingSeparators = Regex("[$SPACE:–—\\-]+$")

    /** [text] without the picture links a card already shows: meme titles often end with the CDN URL. */
    fun stripMediaLinks(text: String): String {
        val withoutLinks = httpUrl.replace(text) { match ->
            if (imageUrl(match.value) != null || embeddableImage(match.value) != null) "" else match.value
        }
        return stripPlaceholderTokens(withoutLinks)
            .replace(doubleSpaces, " ").trim()
            .replace(trailingSeparators, "").trim()
    }

    /**
     * The DASH manifest of a bare `v.redd.it/<id>` link, which an HTML listing
     * carries without the stream itself: Reddit serves every native video there.
     */
    fun vRedditDashUrl(url: String?): String? {
        val parsed = url?.toHttpUrlOrNull()?.takeIf { bareHost(it.host) == "v.redd.it" } ?: return null
        val id = parsed.pathSegments.firstOrNull { it.isNotEmpty() }?.takeUnless { '.' in it } ?: return null
        return "https://v.redd.it/$id/DASHPlaylist.mpd"
    }

    /** Whether [url] is a `reddit.com/gallery/<id>` link a listing carried without the pictures. */
    fun isGalleryUrl(url: String?): Boolean {
        val parsed = url?.toHttpUrlOrNull() ?: return false
        if (parsed.host != "reddit.com" && !parsed.host.endsWith(".reddit.com")) return false
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }
        return segments.size >= 2 && segments.first() == "gallery"
    }

    /**
     * A community's own artwork from `community_icon`, else `icon_img`.
     * Reddit escapes the ampersands, which its CDN rejects; it signs the
     * `?width=&s=` query, so a resized copy 403s and the original file without
     * the query is the one to load. Empty, `default` and the site's own snoo
     * are no community's picture.
     */
    fun communityIconUrl(communityIcon: String? = null, iconImg: String? = null): String? =
        listOf(communityIcon, iconImg).firstNotNullOfOrNull(::qualityIconUrl)

    private fun qualityIconUrl(raw: String?): String? {
        val trimmed = raw?.trim()?.replace("&amp;", "&") ?: return null
        if (trimmed.isEmpty() || trimmed in setOf("default", "self", "nsfw")) return null
        val absolute = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://${trimmed.substring(7)}"
            "://" in trimmed -> trimmed
            else -> return null
        }
        val parsed = absolute.toHttpUrlOrNull() ?: return null
        return when {
            "redditstatic.com" in parsed.host.lowercase() -> null
            parsed.encodedQuery != null -> parsed.newBuilder().query(null).build().toString()
            else -> absolute
        }
    }
}
