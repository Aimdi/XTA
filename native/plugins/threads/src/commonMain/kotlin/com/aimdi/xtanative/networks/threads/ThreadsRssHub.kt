package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.threads.ThreadsFailure.NOT_CONFIGURED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.NO_SUCH_FEED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.THROTTLED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.UNREACHABLE
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/**
 * Reads an account's threads through an RSSHub instance the reader runs, as
 * XTA's threads_client.dart does: RSSHub talks to Meta on a server the reader
 * controls and hands back a JSON Feed. One page, newest first, with no counts.
 */
class ThreadsRssHub(private val http: XHttp) {
    /** [handle]'s threads from [instance]. */
    suspend fun account(instance: String, handle: String): Page<ThreadsPost> {
        if (instance.isBlank()) throw ThreadsException(NOT_CONFIGURED, "no instance configured")
        val url = feedUrl(instance, handle) ?: throw ThreadsException(UNREACHABLE, "$instance: not a web address")
        // A dead instance fails fast, so the guest read can answer instead.
        val response = withTimeoutOrNull(TIMEOUT_MILLIS) { http.get(url, emptyMap(), followRedirects = true) }
            ?: throw SocketTimeoutException("$url: no answer in ${TIMEOUT_MILLIS / 1000} s")
        when (response.code) {
            200 -> Unit
            404 -> throw ThreadsException(NO_SUCH_FEED, "$url: 404", 404)
            429 -> throw ThreadsException(THROTTLED, "$url: 429", 429)
            else -> throw ThreadsException(UNREACHABLE, "$url: ${response.code}", response.code)
        }
        // An instance that answers 200 with an error page is unreachable as far as anyone reading is concerned.
        val feed = ThreadsJson.decode(response.body)?.let { J(it) } ?: throw ThreadsException(UNREACHABLE, "$url: not JSON", response.code)
        return Page(parseFeed(feed, handle), null)
    }

    /**
     * Confirms [instance] is an RSSHub that serves this route, by reading a real
     * feed: one with the Threads route switched off would pass a front-page test.
     */
    suspend fun verify(instance: String, handle: String = "zuck") {
        account(instance, handle)
    }

    companion object {
        private const val TIMEOUT_MILLIS = 10_000L
        private val titleSeparator = Regex("""\s+[-–|]\s+""")
        private val lineBreak = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
        private val manyLines = Regex("""\n{3,}""")

        /** [handle]'s feed on [instance], as JSON rather than RSS, whatever trailing slashes the instance was given. */
        fun feedUrl(instance: String, handle: String): HttpUrl? =
            "${instance.trim().trimEnd('/')}/threads/$handle".toHttpUrlOrNull()?.newBuilder()?.encodedQuery("format=json")?.build()

        /** One account's JSON Feed as threads; an item with neither words nor pictures is left out, not drawn blank. */
        fun parseFeed(feed: J, handle: String): List<ThreadsPost> {
            val author = authorOf(feed, handle)
            val avatar = feed["icon"].string ?: feed["favicon"].string
            return feed["items"].list.mapNotNull { item ->
                val html = item["content_html"].string
                val text = textOf(item, html)
                val images = imagesOf(item, html)
                if (text.isEmpty() && images.isEmpty()) return@mapNotNull null
                val url = item["url"].string ?: item["external_url"].string
                val id = item["id"].string ?: url ?: return@mapNotNull null
                ThreadsPost(
                    id = id,
                    handle = handle,
                    authorName = item["authors"][0]["name"].string ?: author,
                    avatarUrl = item["authors"][0]["avatar"].string ?: avatar,
                    text = text,
                    media = images.map { ThreadsMedia(it) },
                    publishedAt = instantOf(item["date_published"].string),
                    url = url,
                )
            }
        }

        /** The name a feed titles itself with: RSSHub writes `Name - Threads`, and the handle is known already. */
        private fun authorOf(feed: J, handle: String): String {
            val title = feed["title"].string?.trim()?.ifEmpty { null } ?: return handle
            return title.split(titleSeparator).first().trim().ifEmpty { null }?.removePrefix("@") ?: handle
        }

        /** The pictures the HTML embeds, then each attachment that says it is an image, in order and without repeats. */
        private fun imagesOf(item: J, html: String?): List<String> {
            val embedded = html?.let { Jsoup.parse(it).select("img[src]").map { image -> image.attr("src") } }.orEmpty()
            val attached = item["attachments"].list
                .filter { it["mime_type"].string?.startsWith("image/") == true }
                .mapNotNull { it["url"].string }
            return (embedded + attached + listOfNotNull(item["image"].string)).filter { it.isNotEmpty() }.distinct()
        }

        /** The words of an item: `content_text` as given, else `content_html` with its line breaks and block ends kept. */
        private fun textOf(item: J, html: String?): String {
            item["content_text"].string?.takeIf { it.isNotBlank() }?.let { return it.trim() }
            if (html == null) return ""
            val document = Jsoup.parse(html.replace(lineBreak, "\n"))
            document.select("p, div").forEach { it.appendText("\n") }
            return document.body().wholeText().replace(manyLines, "\n\n").trim()
        }

        /** An ISO-8601 time as Dart's `DateTime.tryParse` reads it; a time without an offset is local. */
        private fun instantOf(text: String?): Instant? {
            val value = text?.trim()?.ifEmpty { null } ?: return null
            val iso = if (value.length > 10 && value[10] == ' ') value.replaceRange(10, 11, "T") else value
            return runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
                ?: runCatching { Instant.parse(iso) }.getOrNull()
                ?: runCatching { LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
                ?: runCatching { LocalDate.parse(iso).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
        }
    }
}
