package com.aimdi.xtanative.networks.instagram

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live instagram.com as a guest, as XTA's
 * test/live/instagram_search_smoke_test.dart reads it. Instagram answers
 * datacenter addresses with 429 or its login wall, so the test only asks
 * that the answer is either @instagram's profile or one of those refusals,
 * never a crash. Skipped unless RUN_LIVE=true.
 */
class InstagramLiveTest {
    @Test
    fun aGuestProfileIsReadOrInstagramsBlockIsNamed() = runBlocking<Unit> {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live instagram.com")
        val client = InstagramClient(OkHttpXHttp())
        try {
            val profile = client.profile("instagram")
            assertEquals("instagram", profile.username.lowercase())
            assertTrue(profile.id.isNotEmpty())
            println("LIVE @instagram ${profile.displayName}, ${profile.followerCount} followers")
        } catch (refused: NetworkRequestException) {
            assertTrue(refused.code in setOf(401, 403, 429), "Instagram refused with ${refused.code}: ${refused.message}")
            println("LIVE Instagram refused a guest: ${refused.code}")
        } catch (offline: IOException) {
            println("LIVE Instagram could not be reached: ${offline.message}")
        }
    }
}
