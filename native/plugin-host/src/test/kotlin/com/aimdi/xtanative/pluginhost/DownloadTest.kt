package com.aimdi.xtanative.pluginhost

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DownloadTest {
    private val bytes = Random(7).nextBytes(300_000)
    private val url = "https://plugins.example/xta-plugin-reddit-1.0.0.jar"
    private val artifact = PluginArtifact(url, sha256(bytes), bytes.size.toLong())
    private val part = File(tempDir("download"), ".downloads/reddit/1.0.0.jar.part")

    private fun progress() = CopyOnWriteArrayList<Pair<Long, Long>>()

    @Test
    fun aFileArrivesWholeAndSaysHowFarItGot() = runBlocking {
        val seen = progress()
        val result = PluginDownloader(FakeArtifacts(mapOf(url to bytes))).download(artifact, part) { done, total -> seen += done to total }
        assertEquals(Downloaded.Whole, result)
        assertContentEquals(bytes, part.readBytes())
        assertEquals(0L to bytes.size.toLong(), seen.first())
        assertEquals(bytes.size.toLong() to bytes.size.toLong(), seen.last())
        assertEquals(seen.map { it.first }.sorted(), seen.map { it.first })
    }

    @Test
    fun aDownloadResumesFromWhatAnEarlierAttemptLeft() = runBlocking {
        part.parentFile.mkdirs()
        part.writeBytes(bytes.copyOf(100_000))
        val source = FakeArtifacts(mapOf(url to bytes))
        assertEquals(Downloaded.Resumed, PluginDownloader(source).download(artifact, part) { _, _ -> })
        assertEquals(listOf(100_000L), source.asked)
        assertContentEquals(bytes, part.readBytes())
    }

    @Test
    fun aServerThatIgnoresTheRangeSendsTheFileWholeAndItIsTakenWhole() = runBlocking {
        part.parentFile.mkdirs()
        part.writeBytes(ByteArray(100_000))
        val source = FakeArtifacts(mapOf(url to bytes), ranges = false)
        assertEquals(Downloaded.Whole, PluginDownloader(source).download(artifact, part) { _, _ -> })
        assertContentEquals(bytes, part.readBytes())
    }

    @Test
    fun morePartThanTheFileHoldsIsNotResumedFrom() = runBlocking {
        val shorter = bytes.copyOf(50_000)
        val source = FakeArtifacts(mapOf(url to shorter))
        part.parentFile.mkdirs()
        part.writeBytes(ByteArray(60_000))
        val result = PluginDownloader(source).download(artifact.copy(size = 50_000, sha256 = sha256(shorter)), part) { _, _ -> }
        assertEquals(Downloaded.Whole, result)
        assertEquals(listOf(0L), source.asked)
        assertContentEquals(shorter, part.readBytes())
    }

    @Test
    fun aRangePastTheEndOfTheFileStartsOver() = runBlocking {
        part.parentFile.mkdirs()
        part.writeBytes(ByteArray(1_000))
        val shorter = bytes.copyOf(800)
        val source = FakeArtifacts(mapOf(url to shorter))
        val result = PluginDownloader(source).download(artifact.copy(size = 2_000), part) { _, _ -> }
        assertEquals(listOf(1_000L, 0L), source.asked)
        assertEquals(PluginFailure.DownloadFailed(null, "the transfer ended after 800 of 2000 bytes"), (result as Downloaded.Failed).reason)
    }

    @Test
    fun moreBytesThanTheCatalogueSaysAreRefusedAndNotKept() = runBlocking {
        val result = PluginDownloader(FakeArtifacts(mapOf(url to bytes))).download(artifact.copy(size = 1_000), part) { _, _ -> }
        assertTrue((result as Downloaded.Failed).reason is PluginFailure.WrongSize)
        assertFalse(part.exists())
    }

    @Test
    fun anErrorAnswerFailsWithItsCode() = runBlocking {
        val result = PluginDownloader(FakeArtifacts(mapOf(url to bytes), status = 503)).download(artifact, part) { _, _ -> }
        assertEquals(PluginFailure.DownloadFailed(503, null), (result as Downloaded.Failed).reason)
        val missing = PluginDownloader(FakeArtifacts(emptyMap())).download(artifact, part) { _, _ -> }
        assertEquals(PluginFailure.DownloadFailed(404, null), (missing as Downloaded.Failed).reason)
    }

    @Test
    fun aTransferThatBreaksOffKeepsWhatArrivedAndTheNextOneResumesThere() = runBlocking {
        val source = FakeArtifacts(mapOf(url to bytes), breakAt = 120_000)
        val downloader = PluginDownloader(source)
        val first = downloader.download(artifact, part) { _, _ -> }
        assertEquals(PluginFailure.DownloadFailed(null, "connection reset"), (first as Downloaded.Failed).reason)
        assertEquals(120_000, part.length())
        assertEquals(Downloaded.Resumed, downloader.download(artifact, part) { _, _ -> })
        assertEquals(listOf(0L, 120_000L), source.asked)
        assertContentEquals(bytes, part.readBytes())
    }

    @Test
    fun cancellingStopsABlockedTransferAtOnceAndKeepsWhatArrived() = runBlocking {
        val source = FakeArtifacts(mapOf(url to bytes), holdAt = 70_000)
        val seen = progress()
        val download = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            PluginDownloader(source).download(artifact, part) { done, total -> seen += done to total }
        }
        withTimeout(10_000) { while (seen.lastOrNull()?.first != 70_000L) delay(5) }
        withTimeout(10_000) { download.cancelAndJoin() }
        assertTrue(download.isCancelled)
        assertEquals(1, source.cancels.get(), "the transfer itself was called off, not only waited out")
        assertEquals(70_000, part.length())
    }

    @Test
    fun theOkHttpSourceStreamsTheFileAsItIsAndFromAnOffset() = runBlocking {
        val asked = CopyOnWriteArrayList<Pair<String?, String?>>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/plugin.jar") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            asked += range to exchange.requestHeaders.getFirst("Accept-Encoding")
            val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            if (from > 0) exchange.responseHeaders.add("Content-Range", "bytes $from-${bytes.size - 1}/${bytes.size}")
            exchange.sendResponseHeaders(if (from > 0) 206 else 200, (bytes.size - from).toLong())
            exchange.responseBody.use { it.write(bytes, from, bytes.size - from) }
        }
        server.start()
        try {
            val local = artifact.copy(url = "http://127.0.0.1:${server.address.port}/plugin.jar")
            assertEquals(Downloaded.Whole, PluginDownloader(OkHttpArtifactSource()).download(local, part) { _, _ -> })
            assertContentEquals(bytes, part.readBytes())

            part.writeBytes(bytes.copyOf(123_456))
            assertEquals(Downloaded.Resumed, PluginDownloader(OkHttpArtifactSource()).download(local, part) { _, _ -> })
            assertContentEquals(bytes, part.readBytes())
            assertEquals(listOf<Pair<String?, String?>>(null to "identity", "bytes=123456-" to "identity"), asked.toList())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aContentRangeSaysWhereTheBytesStart() {
        assertEquals(100, OkHttpArtifactSource.rangeStart(206, "bytes 100-199/200"))
        assertEquals(-1, OkHttpArtifactSource.rangeStart(206, null))
        assertEquals(-1, OkHttpArtifactSource.rangeStart(206, "bytes */200"))
        assertEquals(0, OkHttpArtifactSource.rangeStart(200, "bytes 100-199/200"))
    }
}
