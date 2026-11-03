package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The Android app Pixiv's API believes it is talking to. Pixiv may change any
 * of these, and XTA repairs them without a release (its plugin repairs), so
 * they travel together and can be replaced as one.
 */
data class PixivApp(
    val clientId: String = "MOBrBDS8blbauoSck0ZfDbtuzpyT",
    val clientSecret: String = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj",
    val version: String = "5.0.234",
    val userAgent: String = "PixivAndroidApp/5.0.234 (Android 11; Pixel 5)",
)

/** Whose a token is, as a token response's `user` names them. */
data class PixivAuthUser(
    val id: Long,
    val name: String,
    val account: String,
    val isPremium: Boolean = false,
    val avatarUrl: String? = null,
) {
    val displayName: String get() = name.ifEmpty { account }

    companion object {
        /** A missing or reshaped `user` is an unknown free account, with id 0. */
        fun parse(user: J): PixivAuthUser {
            val images = user["profile_image_urls"]
            val avatar = images["px_170x170"].string ?: images["px_50x50"].string ?: images["medium"].string
            return PixivAuthUser(
                id = user["id"].long ?: 0,
                name = user["name"].string?.trim().orEmpty(),
                account = user["account"].string?.trim().orEmpty(),
                isPremium = user["is_premium"].boolean == true,
                avatarUrl = avatar?.ifEmpty { null },
            )
        }
    }
}

/** What the sign-in's code exchange answers: the tokens, how long the access token lasts, and whose they are. */
data class PixivLoginTokens(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long, val user: PixivAuthUser) {
    override fun toString() = "PixivLoginTokens(user=${user.id}, expiresInSeconds=$expiresInSeconds)"
}

/**
 * The account the client reads as: the refresh token that stands for it, and
 * the access token last traded for that. A credential, so it never prints its tokens.
 */
data class PixivCredentials(
    val refreshToken: String = "",
    val accessToken: String = "",
    /** Epoch milliseconds from which [accessToken] is no longer sent: a minute before Pixiv's own expiry. */
    val accessExpiresAt: Long = 0,
    /** 0 until a token response names the account. */
    val userId: Long = 0,
    val isPremium: Boolean = false,
) {
    val signedIn: Boolean get() = refreshToken.isNotBlank()

    override fun toString() = "PixivCredentials(userId=$userId, signedIn=$signedIn, isPremium=$isPremium)"

    companion object {
        /** Signed out: no account at all. */
        val NONE = PixivCredentials()

        /** A stored account, without an access token: the first read refreshes one. */
        fun account(userId: Long, refreshToken: String, isPremium: Boolean = false) =
            PixivCredentials(refreshToken = refreshToken, userId = userId, isPremium = isPremium)

        /** A pasted refresh token, whose account is not known yet. */
        fun pasted(refreshToken: String) = PixivCredentials(refreshToken = refreshToken)
    }
}

/**
 * Where the client keeps the account it reads as. XTA keeps it in its
 * preferences; XTA Native keeps it on the device too. The client reads it at
 * each request and writes it after each token refresh.
 */
interface PixivCredentialStore {
    var credentials: PixivCredentials
}

/** Credentials kept in memory alone: for tests, and for reading without keeping anything. */
class MemoryPixivCredentials(initial: PixivCredentials = PixivCredentials.NONE) : PixivCredentialStore {
    @Volatile
    override var credentials: PixivCredentials = initial
}

/** One sign-in attempt's PKCE pair: the [challenge] goes to the login page, the [verifier] to the code exchange. */
data class PixivPkce(val verifier: String, val challenge: String) {
    override fun toString() = "PixivPkce(challenge=$challenge)"
}

/**
 * Pixiv's browser sign-in (OAuth with PKCE), as community clients such as
 * Pixez do it: the reader signs in on Pixiv's own page, and the redirect
 * carries a one-time code that [PixivClient.exchangeCode] trades for tokens.
 * Only the authorization-code and refresh-token grants are used; reading needs no more.
 */
object PixivLogin {
    const val TOKEN_URL = "https://oauth.secure.pixiv.net/auth/token"
    const val LOGIN_URL = "https://app-api.pixiv.net/web/v1/login"
    const val REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"

    private val base64 = Base64.getUrlEncoder().withoutPadding()

    /** A fresh pair for one attempt; the challenge is the verifier's SHA-256 (`S256`). */
    fun pkce(random: SecureRandom = SecureRandom()): PixivPkce {
        val verifier = base64.encodeToString(ByteArray(32).also(random::nextBytes))
        val challenge = base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        return PixivPkce(verifier, challenge)
    }

    /** Pixiv's login page for [codeChallenge]: name, password and two-step checks all happen there. */
    fun loginUrl(codeChallenge: String): HttpUrl = LOGIN_URL.toHttpUrl().newBuilder()
        .addQueryParameter("code_challenge", codeChallenge)
        .addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("client", "pixiv-android")
        .build()

    /** The code a redirect to Pixiv's callback (or its `pixiv://` scheme) carries; null for any other address. */
    fun codeFrom(url: String): String? = callback(url)?.query("code")?.ifEmpty { null }

    /** Whether such a redirect says the sign-in was refused or failed. */
    fun deniedIn(url: String): Boolean = !callback(url)?.query("error").isNullOrEmpty()

    private fun callback(url: String): PixivUri? =
        PixivUri.parse(url)?.takeIf { it.scheme == "pixiv" || url.startsWith(REDIRECT_URI) }
}
