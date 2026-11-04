package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.NetworkRequestException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** How a Reddit failure names its network. */
internal const val REDDIT = "reddit"

/**
 * The characters JavaScript's `\s` matches, which is what XTA's Dart regular
 * expressions mean by it: Java's `\s` leaves out the no-break space and the
 * other Unicode spaces Reddit's markup is full of.
 */
internal const val SPACE = "\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff"

private val www = "https://www.reddit.com/".toHttpUrl()

/** [link] as a URL: a whole one as it is, a path against www.reddit.com; null when it is neither. */
internal fun urlOf(link: String): HttpUrl? = link.toHttpUrlOrNull() ?: www.resolve(link)

/** The host of [link], when it is a whole http(s) URL. */
internal fun hostOf(link: String): String? = link.toHttpUrlOrNull()?.host

/** The non-empty path segments of [link], decoded: what Dart reads off `Uri.pathSegments`. */
internal fun segmentsOf(link: String?): List<String> =
    link?.let(::urlOf)?.pathSegments.orEmpty().filter { it.isNotEmpty() }

/** The segment after [segment] in [link]'s path: `name` in `/r/name` or `/user/name`. */
internal fun nameAfter(link: String?, segment: String): String? {
    val segments = segmentsOf(link)
    val at = segments.indexOf(segment)
    return if (at < 0) null else segments.getOrNull(at + 1)
}

/** A whole number written as text, or null. */
internal fun intOf(value: String?): Int? = value?.trim()?.toIntOrNull()

/** Reddit's timestamps: `2026-10-20T14:40:51.849000+0000`, or the same with a colon in the offset. */
internal fun instantOf(value: String?): Instant? {
    val text = value?.trim()?.replace(' ', 'T')?.replace(offsetWithoutColon, "$1:$2")?.ifEmpty { null } ?: return null
    return runCatching { OffsetDateTime.parse(text).toInstant() }
        .recoverCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC) }
        .getOrNull()
}

private val offsetWithoutColon = Regex("([+-]\\d{2})(\\d{2})$")

/** Seconds since the epoch, as the JSON's `created_utc` holds them. */
internal fun instantOfSeconds(seconds: Double?): Instant? = seconds?.let { Instant.ofEpochMilli(Math.round(it * 1000)) }

/** A whole number however the JSON wrote it: XTA's `Json.integer`. */
internal val J.integer: Int? get() = long?.toInt() ?: double?.toInt()

/** The value of attribute [name], or null when the element does not carry it. */
internal fun Element.attrOrNull(name: String): String? = if (hasAttr(name)) attr(name) else null

/** The elements under this one matching [css]: jsoup's own select would include this element too. */
internal fun Element.below(css: String): List<Element> = select(css).filter { it !== this }

internal fun Element.firstBelow(css: String): Element? = select(css).firstOrNull { it !== this }

/** The nearest ancestor named [tag], or null. */
internal fun Element.ancestorNamed(tag: String): Element? = generateSequence(parent()) { it.parent() }.firstOrNull { it.normalName() == tag }

/** Every text under this node as it was written, as Dart's `Element.text` reads it: jsoup's `text()` folds spaces. */
internal fun Node.rawText(): String = buildString { appendRawText(this@rawText) }

private fun StringBuilder.appendRawText(node: Node) {
    when (node) {
        is TextNode -> append(node.wholeText)
        is Element -> node.childNodes().forEach { appendRawText(it) }
    }
}

/** A failure Reddit answered with: [code] is its status, or the status a refusal in the page itself stands for. */
internal fun refused(code: Int, message: String) = NetworkRequestException(REDDIT, code, message)

internal fun blocked(message: String) = refused(403, message)

internal fun notFound(message: String) = refused(404, message)

/** Reddit answered, with [code], something that is not what was asked for. */
internal fun unreadable(message: String, code: Int = 200) = refused(code, message)
