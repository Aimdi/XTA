package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivLinkRef
import com.aimdi.xtanative.networks.pixiv.PixivLinks
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.networks.pixiv.PixivUri
import com.aimdi.xtanative.store.settings.PixivPrefs
import java.net.URLDecoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A work opened from a list, with the list: Swipe between works pages through it, and [source] pages the list on past its end. */
internal class PixivWorkHandoff(val works: List<PixivIllust>, val index: Int, val source: PixivList<PixivIllust>?)

/** Opens the work at [index] of [works], among its neighbours when Swipe between works is on. */
internal fun PixivRuntime.openWork(works: List<PixivIllust>, index: Int, source: PixivList<PixivIllust>? = null) {
    val work = works.getOrNull(index) ?: return
    val pager = settings[PixivPrefs.swipeBetweenWorks] && (works.size > 1 || source != null)
    open(PixivRoute.Work(work.id, shelf.put(if (pager) PixivWorkHandoff(works, index, source) else work)))
}

internal fun PixivRuntime.openWork(work: PixivIllust) = open(PixivRoute.Work(work.id, shelf.put(work)))

internal fun PixivRuntime.openNovel(novel: PixivNovel) = open(PixivRoute.Novel(novel.id, shelf.put(novel)))

internal fun PixivRuntime.openUser(id: Long, tab: String? = null) = open(PixivRoute.User(id, tab))

internal fun PixivRuntime.openTagSearch(tag: String, kind: PixivSearchKind = PixivSearchKind.WORKS) = open(PixivRoute.Search(tag, kind))

/** Opens a link written in Pixiv's text: a Pixiv page on its screen here, any other as the app opens links. */
internal fun PixivRuntime.openHref(href: String) {
    val ref: PixivLinkRef? = if ("://" in href) PixivLinks.parse(href) else null
    if (ref != null) open(PixivRoutes.routeOf(ref)) else host.openLink(href)
}

/** A run of a Pixiv text (a caption, a profile's bio) with the formatting it had; [href] already made absolute. */
internal data class PixivTextPart(val text: String, val bold: Boolean = false, val href: String? = null)

/** Reading Pixiv's HTML texts: line breaks, bold and links are kept, every other tag is read for its text alone. */
internal object PixivHtml {
    fun parts(html: String): List<PixivTextPart> {
        if (html.isBlank()) return emptyList()
        return partsOf(Jsoup.parseBodyFragment(html).body().childNodes(), bold = false, href = null)
    }

    private fun partsOf(nodes: List<Node>, bold: Boolean, href: String?): List<PixivTextPart> = nodes.flatMap { partsOf(it, bold, href) }

    private fun partsOf(node: Node, bold: Boolean, href: String?): List<PixivTextPart> = when (node) {
        is TextNode -> node.wholeText.takeIf { it.isNotEmpty() }?.let { listOf(PixivTextPart(it, bold, href)) }.orEmpty()
        is Element -> when (node.normalName()) {
            "br" -> listOf(PixivTextPart("\n"))
            "strong", "b" -> partsOf(node.childNodes(), true, href)
            "a" -> partsOf(node.childNodes(), bold, href(node.attr("href")) ?: href)
            "p", "div" -> partsOf(node.childNodes(), bold, href) + PixivTextPart("\n")
            else -> partsOf(node.childNodes(), bold, href)
        }
        else -> emptyList()
    }

    /** A link's own scheme, such as `javascript:` or `mailto:`, which is no page to open. */
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /** A link made absolute: Pixiv writes its own pages as paths or `pixiv://` links, and wraps outside ones in `/jump.php?<url>`. */
    fun href(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.startsWith("pixiv://")) return text
        val absolute = when {
            text.startsWith("https://") || text.startsWith("http://") -> text
            text.startsWith("//") -> "https:$text"
            text.startsWith("/") -> "https://www.pixiv.net$text"
            "://" in text || scheme.containsMatchIn(text) -> return null
            else -> "https://www.pixiv.net/$text"
        }
        val uri = PixivUri.parse(absolute) ?: return null
        if (uri.path != "/jump.php") return absolute
        val target = uri.query("url") ?: absolute.substringAfter('?', "").let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        return target?.takeIf { it.isNotEmpty() }?.let(::href)
    }
}
