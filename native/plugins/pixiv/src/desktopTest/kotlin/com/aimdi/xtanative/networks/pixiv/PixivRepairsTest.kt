package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.repairs.Repairs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Pixiv's repairable values against XTA's published `repairs.json` at the
 * repository's root, as :networks checks the networks still there: the file
 * names exactly the Pixiv values this plugin knows, each of its slot's kind
 * and each the value this build ships.
 */
class PixivRepairsTest {
    @Test
    fun repairsJsonNamesOnlyThePixivValuesThisPluginKnowsAsItShipsThem() {
        val slots = PixivApp.SLOTS.associateBy { it.key }
        val published = Repairs.parse(repairsJson().readText()).filterKeys { it.startsWith("pixiv.") }

        assertEquals(slots.keys, published.keys)
        published.forEach { (key, value) ->
            val slot = slots.getValue(key)
            assertTrue(slot.kind.fits(value), key)
            assertEquals(slot.shipped, value, "$key: change repairs.json and PixivApp's slot together")
        }
    }

    @Test
    fun everyShippedValueFitsItsKindAndIsDeclaredOnce() {
        PixivApp.SLOTS.forEach { assertTrue(it.kind.fits(it.shipped), it.key) }
        assertEquals(PixivApp.SLOTS.size, PixivApp.SLOTS.map { it.key }.toSet().size)
    }

    @Test
    fun theAppTakesWhatFitsFromThePublishedEntriesAndKeepsTheRest() {
        val app = PixivApp.published(mapOf("pixiv.app_version" to "6.1.0", "pixiv.client_secret" to "not/a/token"))

        assertEquals("6.1.0", app.version)
        assertEquals(PixivApp().clientSecret, app.clientSecret)
        assertEquals(PixivApp().userAgent, app.userAgent)
        assertEquals(PixivApp(), PixivApp.published(emptyMap()), "a withdrawn repair goes back to the shipped value")
    }

    @Test
    fun aRepairPublishedMeanwhileReachesTheNextRequest(): Unit = runBlocking {
        var published = emptyMap<String, String>()
        val http = FakePixivHttp { answer("""{"illusts": []}""") }
        val client = PixivClient(http, signedIn(), clock = { NOW }, appInForce = { PixivApp.published(published) })

        client.following()
        published = mapOf("pixiv.app_version" to "6.2.0", "pixiv.user_agent" to "PixivAndroidApp/6.2.0 (Android 14; Pixel 8)")
        client.following()

        assertEquals(listOf("5.0.234", "6.2.0"), http.reads.map { it.headers["App-Version"] })
        assertEquals("PixivAndroidApp/6.2.0 (Android 14; Pixel 8)", http.reads.last().headers["User-Agent"])
    }

    /** The repository's `repairs.json`, found from wherever the tests run (native/plugins/pixiv under Gradle). */
    private fun repairsJson(): File {
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "repairs.json") }
            .firstOrNull { it.isFile && File(it.parentFile, "native").isDirectory }
        return assertNotNull(found, "repairs.json beside native/")
    }
}
