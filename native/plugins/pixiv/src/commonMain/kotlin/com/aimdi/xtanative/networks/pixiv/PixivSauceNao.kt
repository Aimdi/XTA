package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** One SauceNAO match that points at a Pixiv work. */
data class PixivSauceMatch(
    val illustId: Long,
    /** How alike SauceNAO judged the pictures, in percent. */
    val similarity: Double? = null,
    val title: String = "",
    val author: String = "",
)

/**
 * SauceNAO's answers to a reverse image search, read for the Pixiv works they
 * name. XTA uploads the picture to [SEARCH_URL] as a multipart form (a PNG in
 * the field `file`, no Pixiv token with it); that upload is binary, which
 * [com.aimdi.xtanative.core.x.http.XHttp] cannot send yet, so only the reading
 * of the answer lives here.
 */
object PixivSauceNao {
    const val SEARCH_URL = "https://saucenao.com/search.php"

    /** The Pixiv work a link names, from `artworks/<id>` or `illust_id=<id>`; null for any other site. */
    fun idFromLink(link: String): Long? {
        val uri = PixivUri.parse(link.trim()) ?: return null
        if (uri.host != "pixiv.net" && !uri.host.endsWith(".pixiv.net")) return null
        val segments = uri.allSegments
        val at = segments.indexOf("artworks")
        val digits = if (at >= 0 && at + 1 < segments.size) segments[at + 1] else uri.query("illust_id")
        return digits?.toLongOrNull()?.takeIf { it > 0 }
    }

    /** SauceNAO's answer, the public page's HTML or its API's JSON, as the Pixiv works it found: best first, each once. */
    fun parse(body: String): List<PixivSauceMatch> {
        val text = body.trimStart()
        val matches = if (text.startsWith("{")) fromJson(J.parse(text)) else fromHtml(text)
        return matches.distinctBy { it.illustId }
    }

    private fun fromJson(json: J): List<PixivSauceMatch> = json["results"].list.mapNotNull { result ->
        val data = result["data"]
        jsonId(data)?.let { id ->
            PixivSauceMatch(
                illustId = id,
                similarity = result["header"]["similarity"].double,
                title = data["title"].string?.trim().orEmpty(),
                author = (data["member_name"].string ?: data["author_name"].string).orEmpty().trim(),
            )
        }
    }

    private fun jsonId(data: J): Long? =
        data["pixiv_id"].long?.takeIf { it > 0 } ?: data["ext_urls"].list.firstNotNullOfOrNull { idFromLink(it.string.orEmpty()) }

    private fun fromHtml(html: String): List<PixivSauceMatch> {
        val document = Jsoup.parse(html)
        val matches = document.below(".result").mapNotNull { block ->
            linkedId(block)?.let { id ->
                PixivSauceMatch(
                    illustId = id,
                    similarity = percent(block.firstBelow(".resultsimilarityinfo")),
                    title = PixivText.textOf(block.firstBelow(".resulttitle")).trim(),
                    author = author(block),
                )
            }
        }
        return matches.ifEmpty { document.below("a[href]").mapNotNull { idFromLink(it.attr("href")) }.map { PixivSauceMatch(it) } }
    }

    private fun linkedId(block: Element): Long? = block.below("a[href]").firstNotNullOfOrNull { idFromLink(it.attr("href")) }

    private fun author(block: Element): String = block.below("a[href]")
        .firstOrNull { "member.php" in it.attr("href") || "/users/" in it.attr("href") }
        ?.let { PixivText.textOf(it).trim() }
        .orEmpty()

    private fun percent(element: Element?): Double? = element?.let { PixivText.textOf(it).replace("%", "").trim().toDoubleOrNull() }
}
