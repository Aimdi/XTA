package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Guest tokens, for reading without an account, as XTA's
 * client_unauthenticated.dart takes them: from `activate.json` with the guest
 * bearer, kept until X refuses one, its quota is spent, or it grows old.
 */
class XGuest(
    private val http: XHttp,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lifetimeMillis: Long = 2 * 60 * 60 * 1000L,
) {
    private val mutex = Mutex()
    private var token: String? = null
    private var takenAt = 0L

    /** The token's rate-limit window as X last reported it: when it resets, and what is left. -1 until X says. */
    private var resetAt = -1L
    private var remaining = -1

    /** A token: the kept one unless [fresh], else a new one from X. */
    suspend fun token(fresh: Boolean = false): String = mutex.withLock {
        val kept = token
        val now = clock()
        if (!fresh && kept != null && now - takenAt in 0 until lifetimeMillis && usable(now)) return@withLock kept
        token = null
        val response = http.post(ACTIVATE, mapOf("authorization" to BEARER))
        val issued = J.parse(response.body)["guest_token"].string?.takeIf { response.isSuccessful }
            ?: throw XRequestException(response.code, "X gave no guest token (${response.code})")
        token = issued
        takenAt = clock()
        resetAt = -1
        remaining = -1
        issued
    }

    /**
     * What X said of the token's quota with an answer, when it said all of it,
     * so a spent token is replaced before X refuses it.
     */
    suspend fun observe(header: (String) -> String?) = mutex.withLock {
        val reset = header("x-rate-limit-reset")?.toLongOrNull()
        val left = header("x-rate-limit-remaining")?.toIntOrNull()
        if (reset != null && left != null && header("x-rate-limit-limit") != null) {
            resetAt = reset * 1000
            remaining = left
        }
    }

    /**
     * Whether the kept token is still worth sending (XTA's guestTokenUsable):
     * yes while X has said nothing, once its window has rolled over, and while
     * it has requests left.
     */
    private fun usable(now: Long): Boolean = resetAt < 0 || now >= resetAt || remaining > 0

    companion object {
        val ACTIVATE = "https://api.x.com/1.1/guest/activate.json".toHttpUrl()

        /** The logged-out bearer, not the signed-in one (XTA's constants.dart). */
        const val BEARER =
            "Bearer AAAAAAAAAAAAAAAAAAAAAGHtAgAAAAAA%2Bx7ILXNILCqkSGIzy6faIHZ9s3Q%3DQy97w6SIrzE7lQwPJEYQBsArEE2fC25caFwRBvAGi456G09vGR"
    }
}
