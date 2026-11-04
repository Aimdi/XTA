package com.aimdi.xtanative.networks.booru

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Recognises a link to a post on a known booru, so it opens in the viewer instead of a browser. */
object BooruLinks {
    private val DIGITS = Regex("^\\d+$")

    /**
     * The post [url] names on one of [sites], in the form each engine links its posts
     * ([BooruPost.hostPageUrl]); null for any other page. `www`, http and letter case do not matter.
     */
    fun postRef(url: String, sites: Iterable<BooruPreset> = BooruPresets.ALL): BooruPostRef? {
        val link = url.toHttpUrlOrNull() ?: return null
        return sites.firstNotNullOfOrNull { site ->
            pathOn(link, site.host)?.let { path -> postId(site.engine, path, link) }?.let { BooruPostRef(site.host, site.engine, it) }
        }
    }

    /** [link]'s path below [host]'s own, when [link] is on [host]'s pages. */
    private fun pathOn(link: HttpUrl, host: String): List<String>? {
        val base = BooruHosts.pageHost(host).toHttpUrlOrNull() ?: return null
        if (bare(base.host) != bare(link.host)) return null
        val prefix = segments(base)
        val path = segments(link)
        return if (path.take(prefix.size) == prefix) path.drop(prefix.size) else null
    }

    private fun postId(engine: BooruEngine, path: List<String>, link: HttpUrl): String? {
        val id = when (engine) {
            BooruEngine.DANBOORU, BooruEngine.E621 -> path.takeIf { it.size == 2 && it[0] == "posts" }?.get(1)
            BooruEngine.MOEBOORU -> path.takeIf { it.size >= 3 && it[0] == "post" && it[1] == "show" }?.get(2)
            BooruEngine.GELBOORU_V2 -> link.queryParameter("id").takeIf {
                path == listOf("index.php") && link.queryParameter("page") == "post" && link.queryParameter("s") == "view"
            }
        }
        return id?.takeIf { DIGITS.matches(it) }
    }

    private fun segments(url: HttpUrl): List<String> = url.pathSegments.filter { it.isNotEmpty() }

    private fun bare(host: String): String = host.lowercase().removePrefix("www.")
}
