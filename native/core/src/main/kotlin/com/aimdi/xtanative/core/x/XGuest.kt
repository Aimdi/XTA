package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Guest tokens, for reading without an account, as XTA's
 * client_unauthenticated.dart takes them: from `activate.json` with the guest
 * bearer, kept until X refuses one or it grows old.
 */
class XGuest(
    private val http: XHttp,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lifetimeMillis: Long = 2 * 60 * 60 * 1000L,
) {
    private val mutex = Mutex()
    private var token: String? = null
    private var takenAt = 0L

    /** A token: the kept one unless [fresh], else a new one from X. */
    suspend fun token(fresh: Boolean = false): String = mutex.withLock {
        val kept = token
        if (!fresh && kept != null && clock() - takenAt in 0 until lifetimeMillis) return@withLock kept
        token = null
        val response = http.post(ACTIVATE, mapOf("authorization" to BEARER))
        val issued = J.parse(response.body)["guest_token"].string?.takeIf { response.isSuccessful }
            ?: throw XRequestException(response.code, "X gave no guest token (${response.code})")
        token = issued
        takenAt = clock()
        issued
    }

    companion object {
        val ACTIVATE = "https://api.x.com/1.1/guest/activate.json".toHttpUrl()

        /** The logged-out bearer, not the signed-in one (XTA's constants.dart). */
        const val BEARER =
            "Bearer AAAAAAAAAAAAAAAAAAAAAGHtAgAAAAAA%2Bx7ILXNILCqkSGIzy6faIHZ9s3Q%3DQy97w6SIrzE7lQwPJEYQBsArEE2fC25caFwRBvAGi456G09vGR"
    }
}
