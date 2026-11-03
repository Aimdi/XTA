package com.aimdi.xtanative.networks

import com.aimdi.xtanative.core.repairs.Repairs
import com.aimdi.xtanative.networks.instagram.InstagramRepairs
import com.aimdi.xtanative.networks.pixiv.PixivApp
import com.aimdi.xtanative.networks.reddit.RedditClient
import com.aimdi.xtanative.networks.stocks.TickerClient
import com.aimdi.xtanative.networks.tiktok.TikTokRepairs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every network's repairable values against XTA's published `repairs.json`
 * at the repository's root, which both apps read: the file names exactly the
 * values the networks here know, each of its slot's kind and each the value
 * this build ships, so a repair is made in the file and in the code together.
 */
class NetworkRepairsTest {
    @Test
    fun repairsJsonNamesOnlyKnownValuesThatFitEachTheOneThisBuildShips() {
        val published = Repairs.parse(repairsJson().readText())
        val slots = NetworkRepairs.SLOTS.associateBy { it.key }

        assertEquals(slots.keys, published.keys)
        published.forEach { (key, value) ->
            val slot = slots.getValue(key)
            assertTrue(slot.kind.fits(value), key)
            assertEquals(slot.shipped, value, "$key: change repairs.json and the network's slot together")
        }
    }

    @Test
    fun everyShippedValueFitsItsOwnKind() {
        NetworkRepairs.SLOTS.forEach { assertTrue(it.kind.fits(it.shipped), it.key) }
    }

    @Test
    fun everyKeyIsDeclaredOnce() {
        assertEquals(NetworkRepairs.SLOTS.size, NetworkRepairs.SLOTS.map { it.key }.toSet().size)
    }

    @Test
    fun instagramAndTikTokTakeWhatFitsAndKeepTheRest() {
        val published = mapOf(
            "instagram.app_id" to "111111111111111",
            "instagram.user_agent" to "Repaired/1.0\r\nX-Evil: 1",
            "tiktok.aid" to "2000",
        )

        assertEquals(InstagramRepairs(appId = "111111111111111"), InstagramRepairs.published(published))
        assertEquals(TikTokRepairs(aid = "2000"), TikTokRepairs.published(published))
    }

    @Test
    fun pixivTakesItsAppFromThePublishedEntries() {
        val app = PixivApp.published(mapOf("pixiv.app_version" to "6.1.0", "pixiv.client_secret" to "not/a/token"))

        assertEquals("6.1.0", app.version)
        assertEquals(PixivApp().clientSecret, app.clientSecret)
        assertEquals(PixivApp().userAgent, app.userAgent)
    }

    @Test
    fun aWithdrawnRepairGoesBackToTheShippedValue() {
        assertEquals(TikTokRepairs(), TikTokRepairs.published(emptyMap()))
        assertEquals(RedditClient.WEB_USER_AGENT, RedditClient.WEB_USER_AGENT_SLOT.valueIn(emptyMap()))
        assertEquals("Repaired/2.0", TickerClient.USER_AGENT_SLOT.valueIn(mapOf("stocks.user_agent" to "Repaired/2.0")))
    }

    /** The repository's `repairs.json`, found from wherever the tests run (native/networks under Gradle). */
    private fun repairsJson(): File {
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "repairs.json") }
            .firstOrNull { it.isFile && File(it.parentFile, "native").isDirectory }
        return assertNotNull(found, "repairs.json beside native/")
    }
}
