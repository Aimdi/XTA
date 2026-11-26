package com.aimdi.xtanative.core.log

/**
 * Takes credentials out of a log line before it is written: an
 * `Authorization` or `Cookie` header's value, a bearer token, and the
 * session's cookies and tokens by name (X's `ct0`, `auth_token`, …), in a
 * header, a query, a cookie string or JSON.
 */
object LogRedaction {
    private const val HIDDEN = "[redacted]"

    /** Cookie and token names whose values are a session. */
    private const val SECRET_NAMES =
        "ct0|auth_token|auth_multi|kdt|twid|att|sessionid|sid|csrftoken|csrf_token|x-csrf-token|x-guest-token|guest_token|gt|" +
            "access_token|refresh_token|id_token|oauth_token|oauth_token_secret|api_key|apikey|client_secret|password|passwd|token"

    private val rules: List<Pair<Regex, String>> = listOf(
        // A whole header's value: everything to the end of the line.
        Regex("""(?i)\b((?:proxy-)?authorization|set-cookie|cookie)("?\s*[:=]\s*"?)[^\r\n"]*""") to "$1$2$HIDDEN",
        Regex("""(?i)\b(bearer)\s+[A-Za-z0-9._~+/%=-]{8,}""") to "$1 $HIDDEN",
        // name=value; name: value; "name":"value".
        Regex("""(?i)(?<![A-Za-z0-9_-])($SECRET_NAMES)("?\s*[:=]\s*"?)[^\s;&",}]+""") to "$1$2$HIDDEN",
    )

    fun redact(line: String): String = rules.fold(line) { text, (pattern, replacement) -> pattern.replace(text, replacement) }
}
