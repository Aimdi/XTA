package com.aimdi.xtanative.mockx

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Answers the requests sent to [port] of this machine as [mock] would, and
 * reports each answer to [onAnswer]: X's JSON for what it holds, plain text
 * saying what is missing for the rest.
 */
fun serveMockX(mock: MockX, port: Int = 8787, onAnswer: (method: String, request: XRequest, answer: MockAnswer) -> Unit): HttpServer {
    val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0)
    server.executor = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }
    server.createContext("/") { exchange ->
        exchange.use {
            exchange.requestBody.readBytes()
            val request = XRequest.of(exchange.requestURI.rawPath, exchange.requestURI.rawQuery)
            val answer = runCatching { mock.answer(exchange.requestMethod, request) }
                .getOrElse { MockAnswer(500, "The stand-in for X failed on ${request.shown}: $it") }
            onAnswer(exchange.requestMethod, request, answer)
            val body = answer.body.toByteArray()
            exchange.responseHeaders["Content-Type"] = if (answer.answered) "application/json; charset=utf-8" else "text/plain; charset=utf-8"
            if (exchange.requestMethod == "HEAD") {
                exchange.sendResponseHeaders(answer.status, -1)
            } else {
                exchange.sendResponseHeaders(answer.status, body.size.toLong())
                exchange.responseBody.write(body)
            }
        }
    }
    server.start()
    return server
}

/** `mock-x [--port 8787]`: serves the stand-in for X on http://localhost:8787 and logs each answer. */
fun main(args: Array<String>) {
    val at = args.indexOf("--port")
    val port = if (at >= 0 && at + 1 < args.size) args[at + 1].toIntOrNull() else 8787
    if (port == null) {
        System.err.println("Usage: mock-x [--port 8787]")
        exitProcess(64)
    }
    val server = serveMockX(MockX(), port) { method, request, answer ->
        println("${answer.status} $method ${request.path}${answer.fixture?.let { " <- $it" }.orEmpty()}")
        if (!answer.answered) println(answer.body)
    }
    println("The stand-in for X is listening on http://localhost:${server.address.port}")
}
