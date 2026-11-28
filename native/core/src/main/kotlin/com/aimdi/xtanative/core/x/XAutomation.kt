package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** X's own error codes that change how the transport treats an account, whatever the HTTP status they came with. */
object XErrorCodes {
    /** "Rate limit exceeded": a 429 by another name. */
    const val RATE_LIMITED = 88

    /** The session's csrf cookie and header do not match: its sign-in is broken, like a 401. */
    const val CSRF_MISMATCH = 353

    /** X took the requests for automation (226), locked the account (326), or ended its day of reading (344). */
    val AUTOMATION = setOf(226, 326, 344)

    /** The codes in X's `errors` list, empty when the body carries none. */
    fun of(response: XResponse): Set<Int> {
        if (!response.body.contains("\"errors\"")) return emptySet()
        val parsed = runCatching { J.parse(response.body) }.getOrNull() ?: return emptySet()
        return parsed["errors"].list.mapNotNull { it["code"].int }.toSet()
    }
}

/**
 * The pause on every X read: one account X calls automated rests on its
 * own ([XAccountPolicy.AUTOMATION_PAUSE_MILLIS]); two within
 * [XAccountPolicy.AUTOMATION_WINDOW_MILLIS] stop every read for
 * [XAccountPolicy.GLOBAL_PAUSE_MILLIS], since X is looking at all of them.
 * Kept in memory: each account's own pause is in its health and outlives a
 * restart.
 */
class XReadPause {
    private val reports = ArrayList<Pair<String, Long>>()
    private val until = MutableStateFlow<Long?>(null)

    /** Until when every X read is paused; null while none is. Its end is not cleared: compare with the clock. */
    val pausedUntil: StateFlow<Long?> = until.asStateFlow()

    fun activeUntil(now: Long): Long? = until.value?.takeIf { it > now }

    /** [account] was called automated at [now]; the global pause's end once that makes two accounts. */
    @Synchronized
    fun report(account: String, now: Long): Long? {
        reports.removeAll { (_, at) -> now - at > XAccountPolicy.AUTOMATION_WINDOW_MILLIS }
        reports += account to now
        if (reports.map { it.first }.distinct().size < 2) return activeUntil(now)
        reports.clear()
        return (now + XAccountPolicy.GLOBAL_PAUSE_MILLIS).also { until.value = it }
    }
}
