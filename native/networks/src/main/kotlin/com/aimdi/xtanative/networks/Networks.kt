package com.aimdi.xtanative.networks

import java.time.Instant

/** The desktop browser XTA's Instagram and TikTok reads present themselves as, as XTA ships it. */
const val DESKTOP_CHROME =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/**
 * One page of a list a network hands out page by page, and where the next
 * page starts: null when there is none.
 */
data class Page<T>(val items: List<T>, val next: String?)

/**
 * What any network's failed read says to a screen: which [network] it was,
 * the HTTP status when the server answered with one, and when to try again
 * when the network said so.
 */
interface NetworkFailure {
    val network: String
    val code: Int?
    val retryAt: Instant?
}

/**
 * A network answered a read with [code] instead of what was asked for. Each
 * network says which [network] it is, so a screen can name it.
 */
class NetworkRequestException(
    override val network: String,
    override val code: Int,
    message: String,
    override val retryAt: Instant? = null,
) : Exception(message), NetworkFailure

/**
 * The networks XTA Native reads besides X, one package each (reddit, bluesky,
 * mastodon, threads, substack, booru, ...). Each only reads: nothing is
 * posted, liked, followed or voted on the network itself.
 */
object Networks
