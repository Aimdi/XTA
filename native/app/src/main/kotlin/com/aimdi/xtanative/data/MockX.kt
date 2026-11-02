package com.aimdi.xtanative.data

import com.aimdi.xtanative.BuildConfig
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor

/**
 * A build made with `-PmockX=<url>` reads XTA's stand-in for X (tool/mock_x,
 * docs/mock-x.md) instead of x.com, so CI can open every screen on an
 * emulator with no account and no live request. Off in every other build.
 */
object MockX {
    val server: String? = BuildConfig.MOCK_X.takeIf { it.isNotEmpty() }

    private val xHosts = setOf("x.com", "api.x.com", "twitter.com", "api.twitter.com")

    /** `https://x.com/i/api/…` to `<server>/x.com/i/api/…`, as XTA's lib/utils/mock_x_urls.dart maps it. */
    fun interceptor(server: String) = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        if (url.host !in xHosts) return@Interceptor chain.proceed(request)
        val mapped = server.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment(url.host)
            .addEncodedPathSegments(url.encodedPath.removePrefix("/"))
            .encodedQuery(url.encodedQuery)
            .build()
        chain.proceed(request.newBuilder().url(mapped).build())
    }

    /** The stand-in does not check signatures. */
    val signer = TransactionSigner(IntArray(48) { it }, "mock")

    /** A stand-in account following the two accounts the stand-in has posts for, one of them in a group. */
    val session = Session(
        groups = listOf(FollowGroup("mock-tests", "Tests", listOf("2095914123905118211"), emoji = "🧪")),
        accounts = listOf(XAccount("mock", "mock", mapOf("Cookie" to "auth_token=mock; ct0=mock", "x-csrf-token" to "mock"))),
        following = listOf(
            FollowedAccount("2095909295913103360", "quax_tests", "QuaX Tests", null, verified = false, inFeed = true, followedAt = 1),
            FollowedAccount("2095914123905118211", "quax_tests_2", "Moluyts", null, verified = false, inFeed = true, followedAt = 2),
        ),
    )
}
