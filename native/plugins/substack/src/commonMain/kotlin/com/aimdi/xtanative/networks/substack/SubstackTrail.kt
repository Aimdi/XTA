package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import okhttp3.HttpUrl

/**
 * What each address one read asked answered, in the order first asked. A
 * read that fails lists it after its verdict, so a failure in the field (CI's
 * live check) says which route gave what, as the X signing bootstrap lists
 * what each page lacked.
 */
internal class SubstackTrail {
    private val answers = LinkedHashMap<String, String>()

    @Synchronized
    fun note(url: HttpUrl, outcome: String) {
        answers[where(url)] = outcome
    }

    /** [verdict] with the trail after its message; [verdict] itself when nothing was asked. */
    @Synchronized
    fun explained(verdict: NetworkRequestException): NetworkRequestException {
        if (answers.isEmpty()) return verdict
        val shown = answers.entries.take(LIMIT).joinToString("; ") { "${it.key}: ${it.value}" }
        val more = if (answers.size > LIMIT) "; ${answers.size - LIMIT} more" else ""
        return NetworkRequestException(verdict.network, verdict.code, "${verdict.message} ($shown$more)", verdict.retryAt)
    }

    companion object {
        /** At most this many addresses are listed: a failure stays a line in a log. */
        const val LIMIT = 24
        private val title = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)

        /** Where [url] is, short: its host and path. */
        fun where(url: HttpUrl): String = url.host + url.encodedPath.trimEnd('/').ifEmpty { "/" }

        /** A response's status, with the title of the page a refusal came as (a block page names itself). */
        fun status(response: XResponse): String = "HTTP ${response.code}" + if (response.code == 200) "" else titled(response.body)

        /** An answer that came but did not count, and why, with the title of the page it was instead. */
        fun rejected(why: String, body: String? = null): String = "HTTP 200, $why" + titled(body)

        private fun titled(body: String?): String {
            val text = body?.let { title.find(it) }?.groupValues?.get(1)?.let(SubstackText::collapse)?.trim().orEmpty()
            return if (text.isEmpty()) "" else " \"${text.take(40)}\""
        }
    }
}
