package com.aimdi.xtanative.networks.substack

import kotlin.coroutines.cancellation.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** How an OPML import went: each site [added], [alreadyFollowed] or [failed], out of [total]. */
data class SubstackOpmlImport(val added: Int = 0, val alreadyFollowed: Int = 0, val failed: Int = 0, val total: Int) {
    val done: Int get() = added + alreadyFollowed + failed
}

/** Follows as OPML 2.0 and back, as XTA's substack_opml.dart. */
object SubstackOpml {
    /** [publications] as OPML 2.0: one RSS outline each, with its site and its `/feed`. */
    fun export(publications: List<SubstackPublication>, title: String): String {
        val outlines = publications.joinToString("\n") { publication ->
            val name = attribute(publication.name)
            val site = attribute(SubstackHosts.originOf(publication.baseUrl))
            "    <outline type=\"rss\" text=\"$name\" title=\"$name\" xmlUrl=\"$site/feed\" htmlUrl=\"$site\"/>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<opml version=\"2.0\">\n" +
            "  <head><title>${element(title)}</title></head>\n" +
            "  <body>\n" +
            "$outlines\n" +
            "  </body>\n" +
            "</opml>\n"
    }

    /**
     * The sites an OPML file lists, once each (`www.` aside), in its order: an
     * outline's `htmlUrl`, else its `xmlUrl` without the feed path. Nested
     * outlines count; entries without a web address do not.
     */
    fun sites(opml: String): List<String> {
        val sites = LinkedHashMap<String, String>()
        for (outline in Jsoup.parse(opml).select("outline")) {
            val url = address(outline)?.trim()?.toHttpUrlOrNull() ?: continue
            sites.putIfAbsent(SubstackHosts.withoutWww(url.host), SubstackHosts.origin(url))
        }
        return sites.values.toList()
    }

    /**
     * Follows [sites] one at a time, each through [resolve] (the client, so its
     * limiter paces them). A site already followed ([followOf]) is left as it
     * is; a new one goes to [follow]. A rate limit ends the import and counts
     * what is left as failed; any other failure counts that site alone.
     * [progress] hears after each site.
     */
    suspend fun import(
        sites: List<String>,
        resolve: suspend (String) -> SubstackPublication,
        followOf: (SubstackPublication) -> SubstackPublication?,
        follow: suspend (SubstackPublication) -> Unit,
        progress: (SubstackOpmlImport) -> Unit = {},
    ): SubstackOpmlImport {
        var result = SubstackOpmlImport(total = sites.size)
        progress(result)
        for ((index, site) in sites.withIndex()) {
            result = try {
                val publication = resolve(site)
                if (followOf(publication) != null) {
                    result.copy(alreadyFollowed = result.alreadyFollowed + 1)
                } else {
                    follow(publication)
                    result.copy(added = result.added + 1)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isSubstackRateLimit) return result.copy(failed = result.failed + sites.size - index).also(progress)
                result.copy(failed = result.failed + 1)
            }
            progress(result)
        }
        return result
    }

    private fun address(outline: Element): String? = when {
        outline.hasAttr("htmlurl") -> outline.attr("htmlurl")
        outline.hasAttr("xmlurl") -> outline.attr("xmlurl")
        else -> null
    }

    private fun element(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun attribute(text: String): String = element(text).replace("\"", "&quot;")
}
