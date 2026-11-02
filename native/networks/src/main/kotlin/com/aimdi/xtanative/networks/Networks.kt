package com.aimdi.xtanative.networks

/**
 * One page of a list a network hands out page by page, and where the next
 * page starts: null when there is none.
 */
data class Page<T>(val items: List<T>, val next: String?)

/**
 * A network answered a read with [code] instead of what was asked for. Each
 * network says which [network] it is, so a screen can name it.
 */
class NetworkRequestException(val network: String, val code: Int, message: String) : Exception(message)

/**
 * The networks XTA Native reads besides X, one package each (reddit, bluesky,
 * mastodon, threads, substack, booru, ...). Each only reads: nothing is
 * posted, liked, followed or voted on the network itself.
 */
object Networks
