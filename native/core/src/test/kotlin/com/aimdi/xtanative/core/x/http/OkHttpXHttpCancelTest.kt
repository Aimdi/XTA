package com.aimdi.xtanative.core.x.http

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl

class OkHttpXHttpCancelTest {
    @Test
    fun aCallerThatLeavesCancelsTheRequestInsteadOfWaitingOutItsTimeout() = runBlocking {
        val arrived = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val hanging = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool()
            createContext("/") { exchange ->
                arrived.complete(Unit)
                release.await(60, TimeUnit.SECONDS)
                exchange.close()
            }
            start()
        }
        try {
            val url = "http://127.0.0.1:${hanging.address.port}/slow".toHttpUrl()
            val reads = listOf(
                launch(Dispatchers.Default) { OkHttpXHttp().get(url, emptyMap(), followRedirects = false) },
                launch(Dispatchers.Default) { OkHttpXHttp().post(url, emptyMap(), "{}", "application/json") },
            )
            withTimeout(10_000) { arrived.await() }
            val left = async { reads.forEach { it.cancelAndJoin() } }
            withTimeout(3_000) { left.await() }
            assertTrue(reads.all { it.isCancelled })
        } finally {
            release.countDown()
            hanging.stop(0)
            (hanging.executor as java.util.concurrent.ExecutorService).shutdownNow()
        }
    }
}
