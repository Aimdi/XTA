package com.aimdi.xtanative.networks.threads

/** What a pasted cookie value is, before anything is asked of Meta. */
sealed interface ThreadsCookiePaste {
    data object Empty : ThreadsCookiePaste

    /** Every cookie the cookie API needs; [header] holds those alone, the rest of what was pasted left out. */
    data class Complete(val header: String) : ThreadsCookiePaste

    /** An `IGT:2` Bearer pasted where the cookies go. */
    data object Bearer : ThreadsCookiePaste

    /** No `name=value` pair in it at all. */
    data object NotCookies : ThreadsCookiePaste

    /** Cookies, but without [names]: often a header copied from a tab that was not signed in, or cut short. */
    data class Missing(val names: List<String>) : ThreadsCookiePaste
}

/**
 * Reads what the reader pasted as a session before it is spent: a Cookie
 * header as a browser's Network panel shows it (with or without its
 * `Cookie:` name), or the rows of its cookie table (name, tab, value), and a
 * Bearer token. Only the cookies the cookie API sends are kept.
 */
object ThreadsSessionCheck {
    fun cookies(raw: String): ThreadsCookiePaste {
        val text = raw.trim()
        if (text.isEmpty()) return ThreadsCookiePaste.Empty
        if (ThreadsSession.normaliseBearer(text) != null) return ThreadsCookiePaste.Bearer
        val cookies = ThreadsSession.parseCookieHeader(header(text))
        if (cookies.isEmpty()) return ThreadsCookiePaste.NotCookies
        val missing = ThreadsSession.REQUIRED_COOKIES.filter { cookies[it].isNullOrEmpty() }
        if (missing.isNotEmpty()) return ThreadsCookiePaste.Missing(missing)
        return ThreadsCookiePaste.Complete(ThreadsSession.REQUIRED_COOKIES.joinToString("; ") { "$it=${cookies.getValue(it)}" })
    }

    /** What is kept of [raw]: the needed cookies alone when it holds them all, else what was typed, trimmed. */
    fun kept(raw: String): String = (cookies(raw) as? ThreadsCookiePaste.Complete)?.header ?: raw.trim()

    /** Whether [raw] is no Bearer: something typed that is not an `IGT:2` token. */
    fun badBearer(raw: String): Boolean = raw.isNotBlank() && ThreadsSession.normaliseBearer(raw) == null

    /** [text] as one `name=value; …` header: its `Cookie:` name dropped, a cookie table's rows read as name and value. */
    private fun header(text: String): String {
        val bare = text.replaceFirst(COOKIE_NAME, "")
        if ('\t' !in bare) return bare.replace('\n', ';')
        return bare.lines().mapNotNull { row ->
            val cells = row.split('\t').map(String::trim)
            cells.getOrNull(1)?.takeIf { cells[0].isNotEmpty() && it.isNotEmpty() }?.let { "${cells[0]}=$it" }
        }.joinToString("; ")
    }

    private val COOKIE_NAME = Regex("^cookie:\\s*", RegexOption.IGNORE_CASE)
}
