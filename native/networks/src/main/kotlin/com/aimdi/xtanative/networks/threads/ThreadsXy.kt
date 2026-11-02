package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import java.net.SocketTimeoutException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * An Xy server failed to answer: [serverMessage] is the sentence it wrote for
 * a person, when it wrote one, and [upstream] which upstream it was talking
 * to, for diagnostics only. [code] is the HTTP status when it answered.
 */
class ThreadsXyException(
    val kind: Kind,
    val serverMessage: String? = null,
    val upstream: String? = null,
    val detail: String = "",
    val code: Int? = null,
) : Exception(serverMessage ?: detail) {
    enum class Kind {
        /** No server address, or no token, has been set. */
        NOT_CONFIGURED,

        /** The server did not answer with JSON. */
        UNREACHABLE,

        /** The token was missing, wrong or rotated. */
        UNAUTHORIZED,

        /** No such profile, or one Threads will not serve to the server. */
        NOT_FOUND,

        /** The server answered with an error of its own. */
        UPSTREAM,
    }
}

/**
 * The reader's own Xy server, the profile half of Threads, as XTA's
 * threads_api.dart asks it. Nothing here talks to Meta: the server holds the
 * doc ids, cookies and geography a guest profile lookup needs, and this only
 * asks it and reads JSON.
 */
class ThreadsXy(private val http: XHttp) {
    /** Whether the server is up: the one endpoint without a token, telling a wrong address from a wrong token. */
    suspend fun health(base: String): Boolean = read(base, "", "/health", authenticated = false)["ok"].boolean ?: false

    /** [username]'s profile card, as the server relays it from Meta. */
    suspend fun profile(base: String, token: String, username: String): ThreadsProfile =
        profileOf(read(base, token, "/profile/${ThreadsLinks.componentEncode(username)}"))

    /** The numeric id behind [username]. */
    suspend fun userId(base: String, token: String, username: String): String? =
        read(base, token, "/user-id/${ThreadsLinks.componentEncode(username)}")["user_id"].string

    /** The numeric id behind a short code or a post link. */
    suspend fun postId(base: String, token: String, shortcodeOrUrl: String): String? =
        read(base, token, "/post-id/${ThreadsLinks.componentEncode(shortcodeOrUrl)}")["post_id"].string

    /** The id of the thread [postId] belongs to. */
    suspend fun threadId(base: String, token: String, postId: String): String? =
        read(base, token, "/thread-id/${ThreadsLinks.componentEncode(postId)}")["thread_id"].string

    private suspend fun read(base: String, token: String, path: String, authenticated: Boolean = true): J {
        if (base.isBlank() || (authenticated && token.isBlank())) throw ThreadsXyException(ThreadsXyException.Kind.NOT_CONFIGURED, detail = path)
        val url = endpoint(base, path) ?: throw ThreadsXyException(ThreadsXyException.Kind.UNREACHABLE, detail = "$base$path: not a web address")
        val headers = buildMap {
            put("Accept", "application/json")
            if (authenticated) put("Authorization", "Bearer ${token.trim()}")
        }
        val response = withTimeoutOrNull(TIMEOUT_MILLIS) { http.get(url, headers, followRedirects = true) }
            ?: throw SocketTimeoutException("$url: no answer in ${TIMEOUT_MILLIS / 1000} s")
        if (response.code != 200) throw errorOf(response.code, response.body, "$url: ${response.code}")
        return ThreadsJson.decode(response.body)?.let { J(it) }
            ?: throw ThreadsXyException(ThreadsXyException.Kind.UNREACHABLE, detail = "$url: not JSON", code = response.code)
    }

    companion object {
        /** The server XTA offers until the reader names their own. */
        const val DEFAULT_BASE = "https://xy-threads.fly.dev"
        private const val TIMEOUT_MILLIS = 20_000L

        /** [path] under [base], whatever trailing slashes the base was given. */
        fun endpoint(base: String, path: String): HttpUrl? = "${base.trim().trimEnd('/')}$path".toHttpUrlOrNull()

        /** A profile as the server relays it from Meta: every field defended, counts zero when missing. */
        fun profileOf(data: J): ThreadsProfile = ThreadsProfile(
            pk = data["pk"].string.orEmpty(),
            id = data["id"].string.orEmpty(),
            username = data["username"].string.orEmpty(),
            fullName = data["full_name"].string.orEmpty(),
            isVerified = data["is_verified"].boolean ?: false,
            isPrivate = data["is_private"].boolean ?: false,
            profilePicUrl = data["profile_pic_url"].string.orEmpty(),
            biography = data["biography"].string.orEmpty(),
            followerCount = data["follower_count"].integer ?: 0,
            followingCount = data["following_count"].integer ?: 0,
            mediaCount = data["media_count"].integer ?: 0,
            externalUrl = data["external_url"].string?.trim()?.ifEmpty { null },
        )

        /** The server's error envelope (`{error, message, upstream}`), read whatever else came with it. */
        private fun errorOf(code: Int, body: String, detail: String): ThreadsXyException {
            val kind = when (code) {
                401, 403 -> ThreadsXyException.Kind.UNAUTHORIZED
                404 -> ThreadsXyException.Kind.NOT_FOUND
                else -> ThreadsXyException.Kind.UPSTREAM
            }
            val envelope = J(ThreadsJson.decode(body))
            return ThreadsXyException(kind, envelope["message"].string, envelope["upstream"].string, detail, code)
        }
    }
}
