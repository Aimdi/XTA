package com.aimdi.xtanative.networks.tiktok

import com.aimdi.xtanative.core.repairs.Repairs
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/**
 * TikTok's repairable values against XTA's published `repairs.json` at the
 * repository's root, as :networks checks the networks still there: the file
 * names exactly the TikTok values this plugin knows, each of its slot's kind
 * and each the value this build ships.
 */
class TikTokRepairsTest {
    @Test
    fun repairsJsonNamesOnlyTheTikTokValuesThisPluginKnowsAsItShipsThem() {
        val slots = TikTokRepairs.SLOTS.associateBy { it.key }
        val published = Repairs.parse(repairsJson().readText()).filterKeys { it.startsWith("tiktok.") }

        assertEquals(slots.keys, published.keys)
        published.forEach { (key, value) ->
            val slot = slots.getValue(key)
            assertTrue(slot.kind.fits(value), key)
            assertEquals(slot.shipped, value, "$key: change repairs.json and TikTokRepairs' slot together")
        }
    }

    @Test
    fun everyShippedValueFitsItsKindAndIsDeclaredOnce() {
        TikTokRepairs.SLOTS.forEach { assertTrue(it.kind.fits(it.shipped), it.key) }
        assertEquals(TikTokRepairs.SLOTS.size, TikTokRepairs.SLOTS.map { it.key }.toSet().size)
    }

    @Test
    fun whatFitsIsTakenAndAWithdrawnRepairGoesBackToTheShippedValue() {
        val published = mapOf("tiktok.aid" to "2000", "tiktok.user_agent" to "Repaired/1.0\r\nX-Evil: 1")

        assertEquals(TikTokRepairs(aid = "2000"), TikTokRepairs.published(published))
        assertEquals(TikTokRepairs(), TikTokRepairs.published(emptyMap()))
    }

    @Test
    fun aRepairPublishedMeanwhileReachesTheNextRequest(): Unit = runBlocking {
        var published = emptyMap<String, String>()
        val asked = mutableListOf<HttpUrl>()
        val http = object : XHttp {
            override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
                asked += url
                return XResponse(200, """{"data": []}""")
            }
        }
        val client = TikTokClient(http, session = TikTokSession(deviceId = "7300000000000000000"), repairs = { TikTokRepairs.published(published) })

        client.trendingQueries()
        published = mapOf("tiktok.aid" to "2001")
        client.trendingQueries()

        assertEquals(listOf("1988", "2001"), asked.map { it.queryParameter("aid") })
    }

    /** The repository's `repairs.json`, found from wherever the tests run (native/plugins/tiktok under Gradle). */
    private fun repairsJson(): File {
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "repairs.json") }
            .firstOrNull { it.isFile && File(it.parentFile, "native").isDirectory }
        return assertNotNull(found, "repairs.json beside native/")
    }
}
