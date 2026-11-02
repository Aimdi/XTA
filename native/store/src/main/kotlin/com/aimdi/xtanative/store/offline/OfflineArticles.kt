package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.core.json.J
import java.io.File
import java.net.URI
import java.util.Base64
import org.jsoup.Jsoup

/** [raw] as an absolute http or https address, read against [base] when relative; null for anything else. */
fun resolveOfflineUrl(raw: String?, base: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val resolved = if (uri.isAbsolute) uri else base?.let { runCatching { URI(it).resolve(uri) }.getOrNull() } ?: return null
    return resolved.takeIf { it.scheme in setOf("http", "https") && !it.host.isNullOrEmpty() }?.toString()
}

/** An article's address as compared across its forms: no fragment, no slash at the end of the path. */
internal fun canonicalArticleUrl(raw: String?): String? {
    val uri = raw?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    if (uri.host.isNullOrEmpty()) return null
    val path = uri.rawPath.orEmpty().trimEnd('/')
    return "${uri.scheme}://${uri.rawAuthority}$path${uri.rawQuery?.let { "?$it" }.orEmpty()}"
}

/** The pictures an article shows, each once: its cover first, then those of its text. */
fun articleImageSources(article: OfflineArticle): List<OfflineMediaSource> {
    val payload = J(article.payload)
    val cover = resolveOfflineUrl(payload["imageUrl"].string ?: payload["cover_image"].string, article.url)
    val images = Jsoup.parseBodyFragment(article.bodyHtml).select("img").map { image ->
        resolveOfflineUrl(image.attr("src").ifEmpty { image.attr("data-src") }, article.url)
    }
    return (listOf(cover) + images).filterNotNull().distinct().map { OfflineMediaSource(it) }
}

private const val CONTENT_POLICY =
    "default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"

/**
 * [sanitizedDocument] made to read without the network: a policy that lets
 * nothing be fetched, its pictures put in as data from [kept] (by the address
 * they were kept from, read against [base]), any other picture left blank,
 * and players and frames taken out.
 */
internal fun renderOffline(sanitizedDocument: String, base: String?, kept: Map<String, File>): String {
    val document = Jsoup.parse(sanitizedDocument)
    document.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content", CONTENT_POLICY)
    document.select("img").forEach { image ->
        val file = kept[resolveOfflineUrl(image.attr("src"), base)]
        image.removeAttr("srcset")
        image.removeAttr("onerror")
        val bytes = file?.readBytes()
        val mime = bytes?.let(::offlineImageMime)
        if (bytes == null || mime == null) image.removeAttr("src")
        else image.attr("src", "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}")
    }
    document.select("source,video,audio,iframe,object,embed").remove()
    document.outputSettings().prettyPrint(false)
    return document.outerHtml()
}
