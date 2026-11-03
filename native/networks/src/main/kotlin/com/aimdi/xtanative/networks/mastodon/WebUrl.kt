package com.aimdi.xtanative.networks.mastodon

import java.net.URLDecoder

/**
 * An address taken apart the way Dart's `Uri.tryParse` takes it apart, for
 * the checks XTA makes on the addresses statuses carry: a scheme, a host, any
 * credentials, an explicit port. Lenient like Dart, unlike OkHttp's HttpUrl,
 * which reads `https:///x` as the host `x` where Dart sees no host at all.
 */
internal class WebUrl private constructor(
    /** Lower case. */
    val scheme: String,
    val hasAuthority: Boolean,
    val userInfo: String,
    /** As Dart keeps it: ASCII in lower case, anything else escaped; empty when there is none. */
    val host: String,
    /** Only a port given explicitly and not the scheme's default, as Dart keeps it. */
    val port: Int?,
    /** As written. */
    val path: String,
    val query: String?,
    val fragment: String?,
) {
    val origin: String get() = "$scheme://$host" + (port?.let { ":$it" } ?: "")

    /** An http(s) address with a host and no credentials in it: one that is safe to open. */
    val isWeb: Boolean get() = (scheme == "http" || scheme == "https") && host.isNotEmpty() && userInfo.isEmpty()

    /** The path's segments, decoded, as Dart's `pathSegments` gives them: none for `` or `/`. */
    val segments: List<String> get() = path.removePrefix("/").let { if (it.isEmpty()) emptyList() else it.split('/').map(::decode) }

    override fun toString(): String = buildString {
        append(scheme).append(':')
        if (hasAuthority) {
            append("//")
            if (userInfo.isNotEmpty()) append(userInfo).append('@')
            append(host)
            port?.let { append(':').append(it) }
        }
        append(path)
        query?.let { append('?').append(it) }
        fragment?.let { append('#').append(it) }
    }

    companion object {
        private val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
        private val defaultPorts = mapOf("http" to 80, "https" to 443)

        /** Null without a scheme, or with a port that is not a number, where Dart has no absolute address either. */
        fun parse(text: String): WebUrl? {
            val match = scheme.find(text) ?: return null
            val name = match.groupValues[1].lowercase()
            val (beforeFragment, fragment) = cut(text.substring(match.value.length), '#')
            val (beforeQuery, query) = cut(beforeFragment, '?')
            if (!beforeQuery.startsWith("//")) return WebUrl(name, false, "", "", null, beforeQuery, query, fragment)
            val rest = beforeQuery.substring(2)
            val slash = rest.indexOf('/').let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, slash)
            val at = authority.lastIndexOf('@')
            val (host, portText) = hostAndPort(authority.substring(at + 1))
            val port = when {
                portText.isNullOrEmpty() -> null
                portText.all { it in '0'..'9' } -> portText.toIntOrNull() ?: return null
                else -> return null
            }
            val userInfo = if (at < 0) "" else authority.substring(0, at)
            val normalHost = if (authority.substring(at + 1).startsWith("[")) host.lowercase() else regName(host) ?: return null
            return WebUrl(name, true, userInfo, normalHost, port.takeIf { it != defaultPorts[name] }, rest.substring(slash), query, fragment)
        }

        /**
         * A host name as Dart's Uri keeps it: ASCII in lower case, escapes of
         * unreserved characters decoded, other escapes in upper case, any other
         * character escaped as UTF-8. Null when it holds a URI delimiter.
         */
        private fun regName(host: String): String? {
            val out = StringBuilder()
            var index = 0
            while (index < host.length) {
                val char = host[index]
                index += when {
                    char == '%' -> out.escape(host, index)
                    char in regNameChars -> 1.also { out.append(char.lowercaseChar()) }
                    char in delimiters -> return null
                    else -> host.codePointAt(index).let { point ->
                        String(Character.toChars(point)).toByteArray(Charsets.UTF_8).forEach { out.append("%%%02X".format(it.toInt() and 0xFF)) }
                        Character.charCount(point)
                    }
                }
            }
            return out.toString()
        }

        /** Appends the escape at [index] as Dart normalises it, and says how many characters it took. */
        private fun StringBuilder.escape(host: String, index: Int): Int {
            val digits = host.substring(index + 1, minOf(index + 3, host.length))
            if (digits.length < 2 || !digits.all { it in hexDigits }) {
                append("%25")
                return 1
            }
            val decoded = digits.toInt(16).toChar()
            if (decoded in unreserved) append(decoded.lowercaseChar()) else append('%').append(digits.uppercase())
            return 3
        }

        private val hexDigits = ('0'..'9').toSet() + ('a'..'f') + ('A'..'F')
        private val unreserved = ('a'..'z').toSet() + ('A'..'Z') + ('0'..'9') + "-._~".toSet()
        private val regNameChars = unreserved + "!$&'()*+,;=".toSet()
        private val delimiters = ":/?#[]@".toSet()

        private fun cut(text: String, mark: Char): Pair<String, String?> {
            val at = text.indexOf(mark)
            return if (at < 0) text to null else text.substring(0, at) to text.substring(at + 1)
        }

        private fun hostAndPort(text: String): Pair<String, String?> {
            if (text.startsWith("[")) {
                val close = text.indexOf(']')
                if (close < 0) return text to null
                return text.substring(1, close) to text.substring(close + 1).removePrefix(":").takeIf { close + 1 < text.length }
            }
            val colon = text.lastIndexOf(':')
            return if (colon < 0) text to null else text.substring(0, colon) to text.substring(colon + 1)
        }

        private fun decode(segment: String): String =
            if ('%' !in segment) segment else runCatching { URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(segment)
    }
}
