package com.aimdi.xtanative.core.x.live

import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XGuest
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.signing.SigningBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live x.com, as XTA's test/live/x_bootstrap_smoke_test.dart reads it: the
 * request signer derived from x.com's own pages with no session, then @X's
 * profile read as a guest. Skipped unless RUN_LIVE=true.
 */
class XLiveTest {
    @Test
    fun aSignatureDerivedFromLiveXComReadsAPublicProfile() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live x.com")
        val http = OkHttpXHttp()
        val signers = XSigners(SigningBootstrap(http))
        val client = XClient(http, accounts = { emptyList() }, signers = signers, guest = XGuest(http))
        client.refreshEndpoints()

        val signer = signers.forSession(null)
        assertEquals(48, signer.keyBytes.size, "the key comes from x.com's twitter-site-verification meta")
        assertTrue(signer.animationKey.isNotEmpty())

        val (user, _) = client.profile("X")
        assertEquals("X", user.screenName)
        assertTrue((user.followers ?: 0) > 1_000_000, "@X has its followers: ${user.followers}")
        println("LIVE @X ${user.name}, ${user.followers} followers, key ${signer.keyBytes.size} bytes")
    }
}
