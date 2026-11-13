package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.networks.JsSpace
import com.aimdi.xtanative.networks.RssDates
import com.aimdi.xtanative.networks.RssParser
import java.time.Instant
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/** One post of a web source, with the fields its rules found. */
data class WebSourceItem(
    val sourceId: String,
    val id: String,
    val title: String? = null,
    /** Its text or excerpt, markup reduced to plain text. */
    val text: String? = null,
    /** Its full text as markup, for the reader without another request. */
    val html: String? = null,
    val link: String? = null,
    val author: String? = null,
    val avatar: String? = null,
    val image: String? = null,
    /** Where its comments are. */
    val discussion: String? = null,
    val images: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val date: Instant? = null,
    val score: Long? = null,
    /** How many comments it has. */
    val comments: Long? = null,
    /** Every single-valued field as read, by its name in the file: what an article address is built from. */
    val values: Map<String, String> = emptyMap(),
) {
    /** Unique across sources. */
    val key: String get() = "$sourceId:$id"

    /** Every picture, the main one first. */
    val pictures: List<String> get() = (listOfNotNull(image) + images).distinct()
}

/** A page of a list read into posts, and the address of the page after it when there is one. */
data class WebSourcePage(val items: List<WebSourceItem>, val next: HttpUrl?)

/**
 * A web source's page, read with its rules into posts. Pure: the page comes
 * in as text. A page that has changed shape gives fewer posts, or none,
 * never a throw.
 */
object WebSourcePages {
    /** The most posts one page gives. */
    const val MAX_ITEMS = 100

    private val addressFields = setOf(WebSourceField.LINK, WebSourceField.AVATAR, WebSourceField.IMAGE, WebSourceField.DISCUSSION)
    private val firstNumber = Regex("""\d[\d,]*""")
    private val digits = Regex("""\d+""")
    private val empty = WebSourcePage(emptyList(), null)

    /** The values one rule reads in one post of a page. */
    private fun interface Read {
        fun values(rule: String): List<String>
    }

    /**
     * The address of [list]'s page [page] (its first unless given), or of the
     * page [cursor] names, or of [query]'s results. Null when the template
     * cannot be filled, as a search without a query.
     */
    fun listUrl(list: WebSourceList, page: Int? = null, cursor: String = "", query: String? = null): HttpUrl? =
        WebSourcePaths.fillTemplate(list.url, encode = true) { name ->
            when (name) {
                "page" -> "${page ?: list.firstPage}"
                "cursor" -> cursor
                "query" -> query
                else -> null
            }
        }?.toHttpUrlOrNull()

    /** [body], the page of [list] at [url] (page number [page] when it counts pages), as posts and the next page. */
    fun read(source: WebSourceDefinition, list: WebSourceList, body: String, url: HttpUrl, page: Int? = null, query: String? = null): WebSourcePage =
        when (list.type) {
            WebSourcePageType.JSON -> jsonPage(source, list, body, url, page, query)
            WebSourcePageType.HTML -> htmlPage(source, list, body, url, page, query)
            WebSourcePageType.RSS -> rssPage(source, body, url)
        }

    /** Where [article] keeps [item]'s full text; null when its address cannot be built from the item. */
    fun articleUrl(article: WebSourceArticle, item: WebSourceItem): HttpUrl? =
        WebSourcePaths.fillTemplate(article.url, encode = true) { item.values[it] }?.toHttpUrlOrNull()

    /** The markup [article] reads from [body], an article page. */
    fun articleHtml(article: WebSourceArticle, body: String): String? = when (article.type) {
        WebSourcePageType.JSON -> runCatching { Json.parseToJsonElement(body) }.getOrNull()
            ?.let { WebSourcePaths.jsonText(WebSourcePaths.jsonValuesAt(it, article.html).firstOrNull()) }
        WebSourcePageType.HTML -> {
            val rule = if (WebSourceSelectors.htmlRule(article.html).attribute == null) "${article.html}@html" else article.html
            val document = Jsoup.parse(body)
            document.children().firstOrNull()?.let { HtmlQuery(document).valuesAt(it, rule).firstOrNull() }
        }
        WebSourcePageType.RSS -> null
    }

    /** A date as sites write them: ISO 8601, an RSS date, or Unix seconds (or milliseconds, from 13 digits). */
    fun date(value: String?): Instant? {
        val text = value?.trim()?.ifEmpty { null } ?: return null
        if (digits.matches(text)) {
            val number = text.toLongOrNull() ?: return null
            return if (text.length >= 13) Instant.ofEpochMilli(number) else Instant.ofEpochSecond(number)
        }
        return RssDates.iso8601(text) ?: RssDates.parse(text)
    }

    /** A count as sites write it: `12`, `12.0`, or the first number in text such as `1,205 points`. */
    fun count(value: String?): Long? {
        val text = value?.trim() ?: return null
        text.toLongOrNull()?.let { return it }
        text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { return it.roundToLong() }
        return firstNumber.find(text)?.value?.replace(",", "")?.toLongOrNull()
    }

    private fun jsonPage(source: WebSourceDefinition, list: WebSourceList, body: String, url: HttpUrl, page: Int?, query: String?): WebSourcePage {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return empty
        val found = WebSourcePaths.jsonValuesAt(root, list.items)
        // A path that leads to one list gives its elements; [*] gives each element found.
        val elements = (found.singleOrNull() as? JsonArray) ?: found
        val items = posts(source, list, elements.map { element -> Read { rule -> WebSourcePaths.jsonValuesAt(element, rule).mapNotNull(WebSourcePaths::jsonText) } }, url)
        val named = list.next?.let { WebSourcePaths.jsonText(WebSourcePaths.jsonValuesAt(root, it).firstOrNull()) }
        return WebSourcePage(items, next(list, items, url, named, page, query, cursor = true))
    }

    private fun htmlPage(source: WebSourceDefinition, list: WebSourceList, body: String, url: HttpUrl, page: Int?, query: String?): WebSourcePage {
        val document = Jsoup.parse(body)
        val root = document.children().firstOrNull() ?: return empty
        val html = HtmlQuery(document)
        val items = posts(source, list, html.selectAll(root, list.items).map { element -> Read { rule -> html.valuesAt(element, rule) } }, url)
        val named = list.next?.let { html.valuesAt(root, it).firstOrNull() }
        return WebSourcePage(items, next(list, items, url, named, page, query, cursor = false))
    }

    /** RSS and Atom pages are read as the RSS reader reads a feed: one page, no next. */
    private fun rssPage(source: WebSourceDefinition, body: String, url: HttpUrl): WebSourcePage {
        val items = RssParser.parse(body, feedUrl = url.toString()).items.take(MAX_ITEMS).map { item ->
            WebSourceItem(
                sourceId = source.id,
                id = item.id,
                title = item.title,
                text = item.excerpt,
                html = item.bodyHtml,
                link = item.link,
                author = item.author,
                image = item.imageUrl,
                date = item.publishedAt,
                tags = item.categories,
                values = listOfNotNull("id" to item.id, "title" to item.title, item.link?.let { "link" to it }).toMap(),
            )
        }
        return WebSourcePage(items, next = null)
    }

    private fun posts(source: WebSourceDefinition, list: WebSourceList, elements: List<Read>, url: HttpUrl): List<WebSourceItem> =
        elements.take(MAX_ITEMS).mapNotNull { post(source, list.fields, it, url) }

    /** One post, or null when it has neither an id nor a link, or neither a title nor a text. */
    private fun post(source: WebSourceDefinition, fields: Map<WebSourceField, String>, read: Read, url: HttpUrl): WebSourceItem? {
        val values = singleValues(fields, read, url)
        val id = values[WebSourceField.ID] ?: values[WebSourceField.LINK]
        val title = plain(values[WebSourceField.TITLE])
        val text = plain(values[WebSourceField.TEXT])
        if (id == null || (title == null && text == null)) return null
        return WebSourceItem(
            sourceId = source.id,
            id = id,
            title = title,
            text = text,
            html = values[WebSourceField.HTML],
            link = values[WebSourceField.LINK],
            author = values[WebSourceField.AUTHOR],
            avatar = values[WebSourceField.AVATAR],
            image = values[WebSourceField.IMAGE],
            discussion = values[WebSourceField.DISCUSSION],
            images = allValues(fields[WebSourceField.IMAGES], read).mapNotNull { address(it, url) },
            tags = allValues(fields[WebSourceField.TAGS], read),
            date = date(values[WebSourceField.DATE]),
            score = count(values[WebSourceField.SCORE]),
            comments = count(values[WebSourceField.COMMENTS]),
            values = values.mapKeys { it.key.key },
        )
    }

    /** The first value of every field, addresses made absolute. */
    private fun singleValues(fields: Map<WebSourceField, String>, read: Read, url: HttpUrl): Map<WebSourceField, String> =
        fields.mapNotNull { (field, rule) ->
            val value = single(rule, read)?.ifEmpty { null } ?: return@mapNotNull null
            (if (field in addressFields) address(value, url) else value)?.let { field to it }
        }.toMap()

    private fun single(rule: String, read: Read): String? =
        if (WebSourcePaths.isTemplate(rule)) WebSourcePaths.fillTemplate(rule) { read.values(it).firstOrNull() } else read.values(rule).firstOrNull()

    private fun allValues(rule: String?, read: Read): List<String> = when {
        rule == null -> emptyList()
        WebSourcePaths.isTemplate(rule) -> listOfNotNull(single(rule, read))
        else -> read.values(rule)
    }

    /** [value] as an absolute web address, resolved against the page [url]; only http and https are kept. */
    private fun address(value: String, url: HttpUrl): String? = url.resolve(value.trim())?.toString()

    /**
     * Text that may arrive with markup in it, as plain text. Blocks are kept
     * apart by a space; XTA ran paragraphs together (`<p>a</p><p>b</p>` gave
     * `ab`).
     */
    private fun plain(value: String?): String? {
        if (value == null) return null
        val text = if ('<' in value || '&' in value) Jsoup.parseBodyFragment(value).body().text() else value
        return JsSpace.collapse(text).ifEmpty { null }
    }

    private fun next(list: WebSourceList, items: List<WebSourceItem>, url: HttpUrl, named: String?, page: Int?, query: String?, cursor: Boolean): HttpUrl? {
        if (list.next != null) {
            if (named.isNullOrEmpty()) return null
            val isAddress = named.startsWith("http") || named.startsWith("/") || !cursor
            val next = if (isAddress) address(named, url)?.toHttpUrlOrNull() else listUrl(list, cursor = named, query = query)
            return next?.takeIf { it != url }
        }
        if (items.isEmpty() || "page" !in WebSourcePaths.templatePlaceholders(list.url)) return null
        return listUrl(list, page = (page ?: list.firstPage) + 1, query = query)
    }
}
