package com.aimdi.xtanative.networks.substack

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.jsoup.parser.Parser

/**
 * Reads a publication's RSS feed, the fallback when its JSON fails, as XTA's
 * substack_rss.dart: tolerant, so a mangled feed gives what items it can and
 * never a throw. An item's id is its slug, since RSS has no number; its
 * enclosure, when it is audio, is the episode of a podcast post.
 */
object SubstackRss {
    private val generator = Regex("<generator[^>]*>([^<]+)</generator>", RegexOption.IGNORE_CASE)
    private val cdata = Regex("<!\\[CDATA\\[([\\s\\S]*?)]]>")
    private val tag = Regex("<[^>]+>")
    private val lineBreak = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val image = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val gmt = Regex("^\\w{3},\\s+(\\d{1,2})\\s+(\\w{3})\\s+(\\d{4})\\s+(\\d{2}):(\\d{2}):(\\d{2})\\s+GMT$", RegexOption.IGNORE_CASE)
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val dartIso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).withZone(ZoneOffset.UTC)

    /** Substack stamps `<generator>Substack</generator>` and serves images from substackcdn; Ghost and the rest do not. */
    fun looksLikeSubstack(xml: String): Boolean {
        val name = generator.find(xml)?.groupValues?.get(1)?.lowercase()
        if (name != null && "substack" in name) return true
        return "substackcdn.com" in xml || ".substack.com" in xml
    }

    /** The channel and its items; an item without a title or a `/p/{slug}` link is left out. */
    fun parse(xml: String, publicationBaseUrl: String, publicationName: String): SubstackRssChannel {
        val channel = firstTag(xml, "channel") ?: xml
        val title = textTag(channel, "title")?.trim()
        val posts = allTags(channel, "item").mapNotNull { item ->
            post(item, publicationBaseUrl, title?.takeIf { it.isNotEmpty() } ?: publicationName)
        }
        return SubstackRssChannel(
            title = title,
            description = textTag(channel, "description")?.trim(),
            link = textTag(channel, "link")?.trim(),
            imageUrl = firstTag(channel, "image")?.let { textTag(it, "url") }?.trim(),
            posts = posts,
            looksLikeSubstack = looksLikeSubstack(xml),
        )
    }

    private fun post(item: String, publicationBaseUrl: String, publicationName: String): SubstackPost? {
        val title = textTag(item, "title")?.trim().orEmpty()
        val link = textTag(item, "link")?.trim()
        val slug = link?.let { SubstackHosts.postRef(it)?.slug }
        if (title.isEmpty() || slug == null) return null
        val content = textTag(item, "content:encoded") ?: textTag(item, "description")
        val enclosure = Regex("<enclosure\\b[^>]*>", RegexOption.IGNORE_CASE).find(item)?.value?.let { attribute(it, "url") }
        val audio = enclosure?.takeIf(::looksAudio)
        return SubstackPost(
            id = slug,
            title = title,
            slug = slug,
            publicationBaseUrl = publicationBaseUrl,
            publicationName = publicationName,
            subtitle = excerpt(content),
            postDate = date(textTag(item, "pubDate")),
            canonicalUrl = link,
            coverImage = content?.let { image.find(it)?.groupValues?.get(1) },
            bodyHtml = content,
            audience = "everyone",
            authorName = textTag(item, "dc:creator") ?: textTag(item, "author"),
            type = if (audio != null) "podcast" else "newsletter",
            audioUrl = audio,
        )
    }

    /** An RFC 822 date as ISO-8601 UTC, as XTA stores it; else whatever ISO date the feed gave. */
    private fun date(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val at = gmtDate(text)
            ?: runCatching { ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
            ?: SubstackText.instant(text)
        return at?.let(dartIso::format)
    }

    private fun gmtDate(text: String): Instant? {
        val (day, month, year, hour, minute, second) = gmt.find(text)?.destructured ?: return null
        val monthNumber = months.indexOf(month.lowercase()) + 1
        if (monthNumber == 0) return null
        return runCatching {
            ZonedDateTime.of(year.toInt(), monthNumber, day.toInt(), hour.toInt(), minute.toInt(), second.toInt(), 0, ZoneOffset.UTC).toInstant()
        }.getOrNull()
    }

    /** The text of the item's body, flattened and decoded, at most 240 characters. */
    private fun excerpt(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val text = SubstackText.collapse(decoded(html.replace(lineBreak, "\n").replace(tag, " "))).trim()
        if (text.isEmpty()) return null
        return if (text.length <= 240) text else "${text.substring(0, 237)}…"
    }

    private fun looksAudio(url: String): Boolean {
        val lower = url.lowercase()
        return listOf(".mp3", ".m4a", "audio", "podcast").any { it in lower }
    }

    private fun firstTag(xml: String, name: String): String? = tagPattern(name).find(xml)?.groupValues?.get(1)

    private fun allTags(xml: String, name: String): List<String> = tagPattern(name).findAll(xml).map { it.groupValues[1] }.toList()

    private fun tagPattern(name: String) = Regex("<${Regex.escape(name)}(?:\\s[^>]*)?>([\\s\\S]*?)</${Regex.escape(name)}>", RegexOption.IGNORE_CASE)

    private fun attribute(openTag: String, name: String): String? =
        Regex("${Regex.escape(name)}=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(openTag)?.groupValues?.get(1)?.let(::decoded)

    /**
     * A tag's text: what its CDATA holds, else its text without tags. Entities
     * outside CDATA are decoded, where XTA showed `&amp;` as it stood.
     */
    private fun textTag(xml: String, name: String): String? {
        val inner = firstTag(xml, name) ?: return null
        cdata.find(inner)?.let { return it.groupValues[1] }
        return decoded(inner.replace(tag, "").trim())
    }

    private fun decoded(text: String): String = Parser.unescapeEntities(text, false)
}
