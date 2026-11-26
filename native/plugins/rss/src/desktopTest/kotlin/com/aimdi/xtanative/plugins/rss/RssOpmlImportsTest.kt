package com.aimdi.xtanative.plugins.rss

import com.aimdi.xtanative.networks.rss.RssOpmlImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/** One OPML import at a time: a second drop while the first file is still read starts none. */
class RssOpmlImportsTest {
    @Test
    fun aSecondImportWhileTheFirstIsReadReadsNothing() = runBlocking {
        val imports = RssOpmlImports()
        val reading = CompletableDeferred<Unit>()
        val file = CompletableDeferred<List<String>>()
        val first = async { imports.alone(read = { reading.complete(Unit); file.await() }) { RssOpmlImport(added = it.size, total = it.size) } }
        reading.await()
        var readAgain = false
        assertNull(imports.alone(read = { readAgain = true; listOf("https://example.com/a") }) { error("a second import ran") })
        assertFalse(readAgain)
        file.complete(listOf("https://example.com/a", "https://example.com/b"))
        assertEquals(2, first.await()?.added)
    }

    @Test
    fun anImportOnceTheLastEndedRunsAndAnEmptyFileFollowsNothing() = runBlocking {
        val imports = RssOpmlImports()
        assertEquals(0, imports.alone(read = { emptyList() }) { error("nothing to follow") }?.total)
        assertEquals(1, imports.alone(read = { listOf("https://example.com/a") }) { RssOpmlImport(added = 1, total = 1) }?.total)
    }
}
