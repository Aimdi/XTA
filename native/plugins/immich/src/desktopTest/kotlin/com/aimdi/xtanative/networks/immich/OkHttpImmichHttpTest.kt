package com.aimdi.xtanative.networks.immich

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** The real transport against a server on this machine: bytes must arrive as they were sent, in both directions. */
class OkHttpImmichHttpTest {
    private class Received(val method: String, val contentType: String?, val apiKey: String?, val body: ByteArray)

    private val received = CopyOnWriteArrayList<Received>()
    private val everyByte = ByteArray(256) { it.toByte() }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            received += Received(
                exchange.requestMethod,
                exchange.requestHeaders.getFirst("Content-Type"),
                exchange.requestHeaders.getFirst("x-api-key"),
                exchange.requestBody.readBytes(),
            )
            val (type, answer) = if (exchange.requestURI.path == "/file.mp4") "video/mp4" to everyByte else "application/json" to """{"id":"a1"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", type)
            exchange.sendResponseHeaders(201, answer.size.toLong())
            exchange.responseBody.use { it.write(answer) }
        }
        start()
    }

    private fun url(path: String): HttpUrl = "http://127.0.0.1:${server.address.port}$path".toHttpUrl()

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun aMultipartUploadArrivesByteForByteWithItsType() = runBlocking {
        val form = Multipart(listOf("deviceId" to "xta"), FilePart("assetData", "all.bin", everyByte))
        val response = OkHttpImmichHttp().send("POST", url("/api/assets"), mapOf("x-api-key" to "key"), form.bytes(), form.contentType)

        val request = received.single()
        assertEquals("POST", request.method)
        assertEquals(form.contentType, request.contentType)
        assertEquals("key", request.apiKey)
        assertContentEquals(form.bytes(), request.body)
        assertEquals(201, response.code)
        assertEquals("""{"id":"a1"}""", response.body)
        assertEquals("application/json", response.header("content-type"))
    }

    @Test
    fun aPutCarriesItsJson() = runBlocking {
        OkHttpImmichHttp().send("PUT", url("/api/albums/a/assets"), emptyMap(), """{"ids":["x"]}""".toByteArray(), "application/json; charset=utf-8")
        val request = received.single()
        assertEquals("PUT", request.method)
        assertEquals("application/json; charset=utf-8", request.contentType)
        assertEquals("""{"ids":["x"]}""", request.body.decodeToString())
    }

    @Test
    fun aDownloadReadsTheFileWholeAsBytes() = runBlocking {
        val file = OkHttpImmichHttp().download(url("/file.mp4"))
        assertEquals(201, file.code)
        assertContentEquals(everyByte, file.bytes)
    }
}
