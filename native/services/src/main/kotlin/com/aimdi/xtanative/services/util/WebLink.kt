package com.aimdi.xtanative.services.util

import java.net.URI
import java.net.URLDecoder

/**
 * A link taken apart the way XTA's Dart code reads one with `Uri.tryParse`:
 * the scheme and host in lower case, the path's non-empty segments decoded,
 * and the query's parameters. [parse] is null for text that is no URI at all.
 */
internal class WebLink private constructor(private val uri: URI, val text: String) {
    val scheme: String = uri.scheme?.lowercase().orEmpty()

    val host: String = (uri.host ?: authorityHost(uri.rawAuthority)).orEmpty().lowercase().removeSurrounding("[", "]")

    val userInfo: String? = uri.rawUserInfo

    /** The port the link names, -1 when it names none. */
    val port: Int = uri.port

    val path: String = uri.rawPath.orEmpty()

    val segments: List<String> = path.split('/').filter { it.isNotEmpty() }.map(::decodeSegment)

    val isWeb: Boolean get() = (scheme == "http" || scheme == "https") && host.isNotEmpty()

    /** Every query parameter in order, names and values decoded (`+` is a space, as in a form). */
    val query: List<Pair<String, String>> by lazy {
        uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map { piece ->
            decodeQuery(piece.substringBefore('=')) to decodeQuery(piece.substringAfter('=', ""))
        }
    }

    fun query(name: String): String? = query.firstOrNull { it.first == name }?.second

    /**
     * The link without the query parameters [drop] names, everything else
     * as written; the text itself when nothing is dropped.
     */
    fun withoutQuery(drop: (String) -> Boolean): String {
        val pieces = uri.rawQuery?.split('&') ?: return text
        val kept = pieces.filterNot { it.isNotEmpty() && drop(decodeQuery(it.substringBefore('='))) }
        if (kept.size == pieces.size) return text
        return buildString {
            append(uri.scheme).append(':')
            uri.rawAuthority?.let { append("//").append(it) }
            append(uri.rawPath.orEmpty())
            if (kept.any { it.isNotEmpty() }) append('?').append(kept.filter { it.isNotEmpty() }.joinToString("&"))
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    /** [reference] read against this link, as a browser follows a relative `Location`. */
    fun resolve(reference: String): WebLink? = runCatching { from(uri.resolve(reference.trim())) }.getOrNull()

    override fun toString(): String = text

    companion object {
        fun parse(text: String): WebLink? = runCatching { from(URI(text)) }.getOrNull()

        private fun from(uri: URI) = WebLink(uri, uri.toString())

        /**
         * An http(s) link with a host, as plugin_url.dart's `httpUri` reads a
         * link: trimmed, and a link written without a scheme (`bsky.app/…`)
         * taken as https. Another scheme is no web link.
         */
        fun web(input: String): WebLink? {
            val text = input.trim()
            if (text.isEmpty()) return null
            val parsed = parse(text)
            if (parsed != null && parsed.isWeb) return parsed
            if (parsed != null && parsed.scheme.isNotEmpty()) return null
            return parse("https://$text")?.takeIf { it.host.isNotEmpty() }
        }

        private fun authorityHost(authority: String?): String? =
            authority?.substringAfterLast('@')?.let { if (it.startsWith('[')) it.substringBefore(']') + "]" else it.substringBefore(':') }

        private fun decodeSegment(segment: String): String =
            runCatching { URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(segment)

        private fun decodeQuery(text: String): String = runCatching { URLDecoder.decode(text, Charsets.UTF_8) }.getOrDefault(text)
    }
}
