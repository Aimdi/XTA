package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToLong
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** A tag as a gallery's page lists it: `namespace:name`, and whether it is dashed (few votes, not settled yet). */
data class EhDetailTag(val raw: String, val weak: Boolean)

/**
 * E-Hentai's pages and API answers read into models without throwing: a
 * missing piece is null or empty. Written against the compact gallery list,
 * the large preview tiles and the image pages that XTA's `uconfig` asks for.
 */
object EhParser {
    private val galleryHref = Regex("""https?://(?:e-hentai|exhentai)\.org/g/(\d+)/([0-9a-f]+)/?""")
    private val pageHref = Regex("""/s/([0-9a-zA-Z]+)/(\d+)-(\d+)""")
    private val rowPages = Regex("""^(\d+)\s*pages?$""")
    private val lengthPages = Regex("""^(\d+)\s*pages?""")
    private val starSprite = Regex("""background-position:\s*(-?\d+)px\s+(-?\d+)px""")
    private val average = Regex("""Average:\s*([0-9.]+)""")
    private val cssImage = Regex("""url\(([^)]+)\)(?:\s*(-?\d+)(?:px)?)?""")
    private val cssSize = Regex("""(?:^|[\s;"])(width|height):\s*(\d+)px""")
    private val sheetParam = Regex("""[?&]p=(\d+)""")
    private val reloadCall = Regex("""nl\(['"]([^'"]+)['"]\)""")
    private val postedOn = Regex("""Posted on\s+(.+?)\s+by:""")
    private val sizeText = Regex("""([\d.]+)\s*(B|KiB|MiB|GiB)""")
    private val digits = Regex("""\d+""")
    private val languageMarks = Regex("""\b(TR|RW)\b""")
    private val spaces = Regex("""\s+""")
    private val lineEnds = Regex("""\s+\n""")
    private val runs = Regex("""[ \t]{2,}""")
    private val banned = Regex("""your ip (?:address )?has been temporarily banned|this ip address has been banned""", RegexOption.IGNORE_CASE)
    private val listTagClasses = setOf("gt", "gtl", "gtw")
    private val sizeUnits = mapOf("B" to 1L, "KiB" to 1024L, "MiB" to 1024L * 1024, "GiB" to 1024L * 1024 * 1024)

    /** A gallery list (front page, popular, a search, a toplist, watched) and its next page's link. */
    fun galleryList(html: String, base: String = EhSettings.EHENTAI): Page<EhGallery> {
        val doc = Jsoup.parse(html, base)
        return Page(doc.select("tr").mapNotNull(::listRow).distinctBy { it.gid }, nextPage(doc))
    }

    private fun listRow(row: Element): EhGallery? {
        val title = row.selectFirst(".glink")?.text()?.trim().orEmpty()
        val link = row.select("a[href]").firstNotNullOfOrNull { galleryHref.find(it.absUrl("href")) }
        val gid = link?.groupValues?.get(1)?.toLongOrNull()
        if (title.isEmpty() || link == null || gid == null) return null
        return EhGallery(
            gid = gid,
            token = link.groupValues[2],
            title = title,
            category = EhCategory.tryParse(row.selectFirst(".glcat .cn")?.text()),
            thumbUrl = row.selectFirst(".glthumb img")?.let(::imageSource),
            uploader = row.selectFirst(".gl4c a")?.text()?.trim()?.ifEmpty { null },
            postedAt = siteTime(row.selectFirst("[id^=posted_]")?.text()),
            pageCount = row.allElements.firstNotNullOfOrNull { rowPages.matchEntire(it.ownText().trim())?.groupValues?.get(1)?.toIntOrNull() },
            rating = starRating(row.selectFirst(".ir")?.attr("style")),
            tags = row.select("div[title]").filter { it.className() in listTagClasses }.map { it.attr("title").trim() },
        )
    }

    /**
     * The next page's link. Only the site's own is followed: the account's
     * cookies go with every read.
     */
    private fun nextPage(doc: Document): String? {
        val link = doc.selectFirst("#unext[href]") ?: doc.select("a[href]").firstOrNull { it.text().trim() == ">" }
        return link?.absUrl("href")?.takeIf { EhSettings.isSiteUrl(it) }
    }

    /**
     * A list row draws its rating as a sprite of stars in its [style]: each
     * 16px left is a star less, and the lower row (-21px) takes off half a star.
     */
    fun starRating(style: String?): Double? {
        val match = starSprite.find(style ?: return null) ?: return null
        val x = match.groupValues[1].toIntOrNull() ?: return null
        val y = match.groupValues[2].toIntOrNull() ?: return null
        val half = if (y == -21) 0.5 else 0.0
        return (5 + x / 16.0 - half).coerceIn(0.0, 5.0)
    }

    /**
     * One preview sheet of a gallery's page; null when the page is no gallery
     * (a content warning, a sign-in page, a page the site reshaped).
     */
    fun galleryDetail(html: String, gid: Long, token: String, base: String = EhSettings.EHENTAI): EhGalleryDetail? {
        val doc = Jsoup.parse(html, base)
        val title = doc.selectFirst("h1#gn")?.text()?.trim().orEmpty()
        if (title.isEmpty()) return null
        val facts = facts(doc)
        val tags = detailTags(doc)
        val (sheetIndex, sheetCount) = previewSheetMeta(doc)
        val language = facts["language"]
        val gallery = EhGallery(
            gid = gid,
            token = token,
            title = title,
            titleJpn = doc.selectFirst("h1#gj")?.text()?.trim()?.ifEmpty { null },
            category = EhCategory.tryParse(doc.selectFirst("#gdc div")?.text()),
            thumbUrl = cover(doc),
            uploader = doc.selectFirst("#gdn a")?.text()?.trim()?.ifEmpty { null },
            postedAt = siteTime(facts["posted"]?.text()),
            pageCount = facts["length"]?.let { lengthPages.find(it.text().trim())?.groupValues?.get(1)?.toIntOrNull() },
            rating = averageRating(doc),
            tags = tags.map { it.raw },
        )
        return EhGalleryDetail(
            gallery = gallery,
            previews = previewSheet(doc),
            comments = comments(doc),
            fileSizeBytes = facts["file size"]?.let { fileSize(flatText(it)) },
            previewSheetIndex = sheetIndex,
            previewSheetCount = sheetCount,
            weakTags = tags.filter { it.weak }.map { it.raw }.toSet(),
            language = language?.let { flatText(it).replace(languageMarks, "").replace(spaces, " ").trim().ifEmpty { null } },
            translated = language?.allElements?.drop(1)?.any { it.ownText().trim() == "TR" } == true,
            favoritedCount = facts["favorited"]?.let { favorited(flatText(it)) },
            ratingCount = doc.selectFirst("#rating_count")?.text()?.trim()?.toIntOrNull(),
        )
    }

    /** The detail table's value cells by label (`posted`, `language`, `file size`, `length`, `favorited`), the tag list left out. */
    private fun facts(doc: Document): Map<String, Element> = doc.select("tr")
        .filter { it.closest("#taglist") == null }
        .mapNotNull { row ->
            val cells = row.children().filter { it.nameIs("td") }
            val label = cells.firstOrNull()?.text()?.trim().orEmpty()
            if (cells.size >= 2 && label.endsWith(":")) label.dropLast(1).trim().lowercase() to cells[1] else null
        }
        .distinctBy { it.first }
        .toMap()

    /** `Average: 4.62` beside the stars; a page whose label sits elsewhere still says it somewhere. */
    private fun averageRating(doc: Document): Double? {
        val said = doc.selectFirst("#rating_label")?.text()?.let { average.find(it) }
            ?: doc.allElements.asSequence().flatMap { it.textNodes() }.firstNotNullOfOrNull { average.find(it.text()) }
        return said?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /** The cover: `#gd1`'s background, or its image. */
    private fun cover(doc: Document): String? {
        val box = doc.selectFirst("#gd1") ?: return null
        return box.select("[style]").firstNotNullOfOrNull { cssImage.find(it.attr("style"))?.groupValues?.get(1) }?.let(::unquoted)
            ?: box.selectFirst("img")?.let(::imageSource)
    }

    /**
     * A gallery's tags as its page lists them. Element ids write spaces as
     * underscores, so the name comes from the link text when there is one;
     * dashed (`gtl`, `gtw`) tags are the ones not settled by votes yet.
     */
    fun detailTags(html: String): List<EhDetailTag> = detailTags(Jsoup.parse(html))

    private fun detailTags(doc: Document): List<EhDetailTag> = doc.select("div[id^=td_]").map { cell ->
        val id = cell.id().removePrefix("td_").replace('+', ' ').trim()
        val colon = id.indexOf(':')
        val namespace = if (colon > 0) id.substring(0, colon + 1) else ""
        val name = cell.selectFirst("a")?.text()?.trim()?.ifEmpty { null } ?: id.substring(namespace.length).replace('_', ' ')
        EhDetailTag(namespace + name, weak = cell.classNames().any { it == "gtl" || it == "gtw" })
    }

    /** The preview tiles on one sheet of a gallery's page, each page once, in page order. */
    fun previewSheet(html: String): List<EhPreview> = previewSheet(Jsoup.parse(html))

    private fun previewSheet(doc: Document): List<EhPreview> {
        val grid: Element = doc.selectFirst("#gdt") ?: doc
        return grid.select("a[href]")
            .mapNotNull { anchor -> pageHref.find(anchor.attr("href"))?.let { tile(anchor, it) } }
            .distinctBy { it.page }
            .sortedBy { it.page }
    }

    /** A tile cut from a sprite sheet (`url(...) -200px 0`) with its size from the style, or a lone thumbnail. */
    private fun tile(anchor: Element, link: MatchResult): EhPreview? {
        val page = link.groupValues[3].toIntOrNull() ?: return null
        val styles = anchor.select("[style]").map { it.attr("style") }
        val sprite = styles.firstNotNullOfOrNull { cssImage.find(it) }
        val sizes = styles.flatMap { style -> cssSize.findAll(style).map { it.groupValues[1] to it.groupValues[2].toDouble() }.toList() }.toMap()
        return EhPreview(
            pageToken = link.groupValues[1],
            page = page,
            thumbUrl = sprite?.groupValues?.get(1)?.let(::unquoted) ?: anchor.selectFirst("img")?.let(::imageSource),
            thumbOffsetX = sprite?.groupValues?.get(2)?.toDoubleOrNull()?.plus(0.0),
            thumbWidth = sizes["width"],
            thumbHeight = sizes["height"],
        )
    }

    /** Which preview sheet a gallery's page shows, and how many there are, from its sheet links (`?p=`). */
    private fun previewSheetMeta(doc: Document): Pair<Int, Int> {
        val links = doc.select("table.ptt a[href], table.ptb a[href]").ifEmpty { doc.select("a[href]") }
        val last = links.mapNotNull { sheetParam.find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        val label = doc.selectFirst(".ptds a")?.text()?.trim()?.toIntOrNull() ?: 1
        return (label - 1).coerceIn(0, last) to last + 1
    }

    /** The comments under a gallery, the uploader's marked. */
    fun comments(html: String): List<EhComment> = comments(Jsoup.parse(html))

    private fun comments(doc: Document): List<EhComment> = doc.select("div.c1").mapNotNull { comment ->
        val header = comment.selectFirst(".c3")
        val author = header?.selectFirst("a")?.text()?.trim().orEmpty()
        val body = comment.selectFirst(".c6")?.let(::flatText).orEmpty()
        if (author.isEmpty() && body.isEmpty()) return@mapNotNull null
        EhComment(
            author = author,
            posted = header?.let { postedOn.find(it.text())?.groupValues?.get(1)?.trim() }.orEmpty(),
            body = body,
            score = comment.selectFirst("[id^=comment_score_]")?.text()?.trim()?.ifEmpty { null },
            uploader = "Uploader Comment" in comment.text(),
        )
    }

    /** A page's image page: the image, the original, the reload key and the neighbours; null when it shows no image. */
    fun imagePage(html: String, page: Int, base: String = EhSettings.EHENTAI): EhImagePage? {
        val doc = Jsoup.parse(html, base)
        val imageUrl = doc.selectFirst("img#img")?.attr("src")?.trim()?.ifEmpty { null } ?: return null
        return EhImagePage(
            imageUrl = imageUrl,
            page = page,
            originalImageUrl = originalImage(doc),
            reloadKey = reloadKey(html),
            nextPageUrl = doc.selectFirst("#next[href]")?.absUrl("href")?.ifEmpty { null },
            prevPageUrl = doc.selectFirst("#prev[href]")?.absUrl("href")?.ifEmpty { null },
            pageCount = imagePageCount(doc),
        )
    }

    /** `fullimg` (the "Download original" link), else `#i7`'s link, when the page offers one. */
    private fun originalImage(doc: Document): String? =
        (doc.select("[href]").firstOrNull { it.attr("href").contains("fullimg", ignoreCase = true) } ?: doc.selectFirst("#i7 a[href]"))
            ?.attr("href")?.trim()?.ifEmpty { null }

    /** The gallery's length from the image page's counter (`2 / 24`). */
    private fun imagePageCount(doc: Document): Int? {
        val counter = doc.selectFirst("#next")?.previousElementSibling()?.takeIf { it.nameIs("div") }?.children()?.lastOrNull()
            ?: doc.select("div").firstNotNullOfOrNull { div ->
                div.children().lastOrNull()?.takeIf { it.nameIs("span") && it.text().trim().toIntOrNull() != null }
            }
        return counter?.text()?.trim()?.toIntOrNull()
    }

    /** The key in `nl('…')`, "Click here if the image fails loading": asking with it gets another image server. */
    fun reloadKey(html: String): String? = reloadCall.find(html)?.groupValues?.get(1)

    /** The `gdata` API's galleries; an entry it could not find is left out. */
    fun gdata(body: String): List<EhGallery> = J.parse(body)["gmetadata"].list.mapNotNull(::gdataEntry)

    private fun gdataEntry(entry: J): EhGallery? {
        if (entry["error"].exists) return null
        val gid = entry["gid"].long ?: return null
        val token = entry["token"].string?.ifEmpty { null } ?: return null
        val title = entry["title"].string ?: return null
        return EhGallery(
            gid = gid,
            token = token,
            title = title,
            titleJpn = entry["title_jpn"].string,
            category = EhCategory.tryParse(entry["category"].string),
            thumbUrl = entry["thumb"].string,
            uploader = entry["uploader"].string,
            postedAt = entry["posted"].long?.let(Instant::ofEpochSecond),
            pageCount = entry["filecount"].int,
            rating = entry["rating"].double,
            tags = entry["tags"].list.mapNotNull { it.string },
        )
    }

    /**
     * `tagsuggest` answers with its tags keyed by id (or an empty list). An
     * alias carries its master tag (`mns`, `mtn`), which is the one to search.
     */
    fun tagSuggestions(body: String): List<EhTag> {
        val tags = J.parse(body)["tags"]
        val entries = tags.keys.map { tags[it] }.ifEmpty { tags.list }
        return entries.mapNotNull(::suggestion).distinctBy { it.raw }
    }

    private fun suggestion(entry: J): EhTag? {
        val alias = entry["mtn"].string != null
        val name = entry[if (alias) "mtn" else "tn"].string?.trim()?.ifEmpty { null } ?: return null
        return EhTag(EhNamespace.tryParse(entry[if (alias) "mns" else "ns"].string), name)
    }

    /**
     * The site's page for an IP banned for too many page loads: "Your IP
     * address has been temporarily banned", which XTA's wording (without
     * "address") never matched, or XTA's other phrase.
     */
    fun isBanned(body: String): Boolean = banned.containsMatchIn(body)

    /** The site's content warning, which offers to view the gallery once (`?nw=session`) or never warn again (`?nw=always`). */
    fun isContentWarning(html: String): Boolean =
        Jsoup.parse(html).select("a[href]").any { link -> link.attr("href").let { "nw=session" in it || "nw=always" in it } }

    /** `96.57 MiB` in bytes. */
    fun fileSize(text: String): Long? {
        val match = sizeText.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = sizeUnits[match.groupValues[2]] ?: return null
        return (value * unit).roundToLong()
    }

    /** How often a gallery was favorited: `Never`, `Once` or `123 times`. */
    fun favorited(text: String): Int? = when (val said = text.trim().lowercase()) {
        "never" -> 0
        "once" -> 1
        else -> digits.find(said)?.value?.toIntOrNull()
    }

    /** A thumbnail's address: a lazy one keeps it in `data-src`, behind a `data:` placeholder in `src`. */
    private fun imageSource(img: Element): String? =
        listOf("data-src", "src").map { img.attr(it).trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("data:") }

    /** `2026-08-11 21:36`, written in the site's time, which is UTC. */
    private fun siteTime(raw: String?): Instant? {
        val text = raw?.trim()?.ifEmpty { null } ?: return null
        return runCatching { LocalDateTime.parse(text.replaceFirst(' ', 'T')).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    private fun unquoted(raw: String): String = raw.trim().removeSurrounding("\"").removeSurrounding("'")

    /** An element's text with each tag read as a space and each `<br>` as a line break, as XTA strips HTML. */
    private fun flatText(element: Element): String {
        val text = StringBuilder()
        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    when {
                        node is TextNode -> text.append(node.wholeText)
                        node.nameIs("br") -> text.append('\n')
                        node is Element && depth > 0 -> text.append(' ')
                    }
                }

                override fun tail(node: Node, depth: Int) {
                    if (node is Element && depth > 0 && !node.nameIs("br")) text.append(' ')
                }
            },
            element,
        )
        return text.toString().replace(' ', ' ').replace(lineEnds, "\n").replace(runs, " ").trim()
    }
}
