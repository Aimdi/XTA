package com.aimdi.xtanative.store.published

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.store.StoreDir
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** The rules of XTA's PluginRepairsRegistry tests: the kept copy first, a read a day, nothing lost on a failure. */
class PublishedDocumentTest {
    private val dir = StoreDir(Files.createTempDirectory("published").toFile())
    private var now = 1_800_000_000_000L
    private val asked = mutableListOf<String>()

    private fun file(vararg repairs: Pair<String, String>) =
        """{"format": 1, "repairs": {${repairs.joinToString { (k, v) -> "\"$k\": \"$v\"" }}}}"""

    private fun registry(answer: () -> XResponse) = RepairsRegistry(
        PublishedDocument(
            XHttp { url, _, _ -> asked += url.toString(); answer() },
            PublishedDocument.REPAIRS_URL,
            dir.file("published/repairs.json"),
            clock = { now },
        ),
    )

    @Test
    fun theCopyLastReadIsInForceBeforeAnyRead() = runBlocking {
        registry { XResponse(200, file("tiktok.aid" to "2000")) }.refresh()
        val later = registry { error("not asked") }

        assertEquals(1, later.applyKept())
        assertEquals(mapOf("tiktok.aid" to "2000"), later.published.value)
    }

    @Test
    fun theFileIsReadAtMostOnceADayAndKept() = runBlocking {
        val repairs = registry { XResponse(200, file("pixiv.app_version" to "6.1.0")) }

        assertEquals(1, repairs.refresh())
        now += 3 * 60 * 60 * 1000L
        assertNull(repairs.refresh())
        assertEquals(listOf(PublishedDocument.REPAIRS_URL.toString()), asked)
        assertEquals(mapOf("pixiv.app_version" to "6.1.0"), repairs.published.value)

        now += PublishedDocument.DAY_MILLIS
        assertEquals(1, repairs.refresh())
        assertEquals(2, asked.size)
    }

    @Test
    fun whatIsInForceStaysWhenTheFileCannotBeRead() = runBlocking {
        registry { XResponse(200, file("tiktok.aid" to "2000")) }.refresh()
        now += PublishedDocument.DAY_MILLIS
        val refused = registry { XResponse(503, "") }
        refused.applyKept()

        assertNull(refused.refresh())
        assertEquals(mapOf("tiktok.aid" to "2000"), refused.published.value)

        val offline = registry { throw IOException("no connection") }
        offline.applyKept()
        assertNull(offline.refresh())
        assertEquals(mapOf("tiktok.aid" to "2000"), offline.published.value)
    }
}
