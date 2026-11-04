package com.aimdi.xtanative.networks.pixiv

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** One work a Pixivision article features, with what it takes to open it. */
data class PixivisionWork(
    val artworkId: Long,
    val userId: Long,
    val title: String,
    val userName: String,
    val imageUrl: String? = null,
    val avatarUrl: String? = null,
)

/** A Pixivision article as its page shows it: title, intro and featured works. */
data class PixivisionArticle(
    val title: String = "",
    val intro: String = "",
    val coverUrl: String? = null,
    val works: List<PixivisionWork> = emptyList(),
) {
    /** Nothing to show: no intro and no work. */
    val isEmpty: Boolean get() = intro.isEmpty() && works.isEmpty()
}

/**
 * Pixivision, Pixiv's magazine, read from its web pages: the articles the
 * spotlight list announces ([PixivDiscoveryApi.spotlightArticles]) open here.
 */
object Pixivision {
    /** pixivision.net serves its whole article markup to desktop browsers only. */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    private val artworkHref = Regex("""/artworks/(\d+)|[?&]illust_id=(\d+)""")
    private val userHref = Regex("""/users/(\d+)|member\.php\?(?:[^#]*&)?id=(\d+)""")

    /** The pixivision.net language a locale reads best: it is written in Japanese, English, Korean and both Chinese scripts. */
    fun language(locale: String): String = pixivLanguageOf(locale).code

    /** An article's page on pixivision.net in [language]. */
    fun articleUrl(id: Long, language: String): String = "https://www.pixivision.net/$language/a/$id"

    /**
     * An article page, read the same way in every language's layout: by the
     * links a work's block carries rather than by class names, which differ
     * between them. A page with no article reads as empty.
     */
    fun parse(html: String): PixivisionArticle {
        val document = Jsoup.parse(html)
        val article = document.firstBelow("article") ?: document.body()
        val body = article.firstBelow(".am__body") ?: article
        return PixivisionArticle(
            title = title(document, article),
            intro = intro(article, body),
            coverUrl = document.firstBelow("meta[property=\"og:image\"]")?.takeIf { it.hasAttr("content") }?.attr("content"),
            works = works(body),
        )
    }

    private fun title(document: Document, article: Element): String =
        PixivText.collapsed(article.firstBelow("h1")).ifEmpty {
            document.firstBelow("meta[property=\"og:title\"]")?.attr("content")?.trim().orEmpty()
        }

    /** The description under the title, else the article's opening paragraphs before its first featured work. */
    private fun intro(article: Element, body: Element): String {
        val description = article.firstBelow("[class*=description]") ?: article.firstBelow("header")
        val paragraphs = description?.below("p").orEmpty().ifEmpty { leadingParagraphs(body) }
        return paragraphs.map(PixivText::collapsed).filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun leadingParagraphs(body: Element): List<Element> = body.below("p, a[href]")
        .takeWhile { it.normalName() != "a" || artworkIdOf(it) == null }
        .filter { it.normalName() == "p" }

    private fun works(body: Element): List<PixivisionWork> {
        val seen = HashSet<Long>()
        return body.below("a[href]").mapNotNull { link ->
            artworkIdOf(link)?.takeIf(seen::add)?.let { id -> blockOf(link, id, body)?.let { workIn(it, id) } }
        }
    }

    /**
     * The widest element around [link] that features no work but [artworkId],
     * when it also links the artist; null for a work without one, which must
     * not borrow a neighbour's.
     */
    private fun blockOf(link: Element, artworkId: Long, body: Element): Element? {
        val stop = body.parent()
        val block = generateSequence(link.parent()) { it.parent() }
            .takeWhile { it !== stop }
            .takeWhile { node -> node.below("a[href]").mapNotNullTo(mutableSetOf(artworkId), ::artworkIdOf).size == 1 }
            .lastOrNull()
        return block?.takeIf { it.below("a[href]").any { a -> userIdOf(a) != null } }
    }

    private fun workIn(block: Element, artworkId: Long): PixivisionWork? {
        val links = block.below("a[href]")
        val userLinks = links.filter { userIdOf(it) != null }
        val userId = userLinks.firstOrNull()?.let(::userIdOf) ?: return null
        val artworkLinks = links.filter { artworkIdOf(it) != null }
        val avatar = userLinks.firstNotNullOfOrNull { it.firstBelow("img") }
        return PixivisionWork(
            artworkId = artworkId,
            userId = userId,
            title = firstText(artworkLinks + listOfNotNull(block.firstBelow("h3"), block.firstBelow("h2"))),
            userName = firstText(userLinks + listOfNotNull(block.firstBelow("p"))),
            imageUrl = imageSource(artworkLinks.firstNotNullOfOrNull { it.firstBelow("img") })
                ?: imageSource(block.below("img").firstOrNull { it !== avatar }),
            avatarUrl = imageSource(avatar),
        )
    }

    private fun firstText(candidates: List<Element>): String =
        candidates.asSequence().map(PixivText::collapsed).firstOrNull { it.isNotEmpty() }.orEmpty()

    /** Lazy-loaded pictures keep their address in a data attribute until scrolled to. */
    private fun imageSource(image: Element?): String? = listOf("data-src", "data-original", "src").firstNotNullOfOrNull { name ->
        image?.attr(name)?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("data:") }?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun artworkIdOf(link: Element): Long? = idIn(artworkHref, link.attr("href"))

    private fun userIdOf(link: Element): Long? = idIn(userHref, link.attr("href"))

    private fun idIn(pattern: Regex, href: String): Long? =
        pattern.find(href)?.let { match -> (match.groups[1] ?: match.groups[2])?.value?.toLongOrNull() }
}
