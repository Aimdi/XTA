package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.x.http.XHttp

/**
 * What reads Reddit for one website agent: the client, the listing cache the
 * surfaces share, the merged feed of followed communities and the gallery
 * loader, all through that client.
 */
class RedditReading internal constructor(val agent: String, http: XHttp) {
    val client = RedditClient(http, agent)
    val cache = RedditListingCache()
    val feed = RedditFollowingFeed(client, cache)
    val galleries = RedditGalleryLoader(client)
}

/**
 * Reddit's readers under XTA's published repairs in force ([published], as
 * the app hands them on): the website is asked as `reddit.web_user_agent`
 * says when it holds a fitting value, else as this build ships it. They are
 * made anew only when that agent changes, which is rare, since a new client
 * starts without the cookies and the token the old one kept.
 */
class RedditReaders(private val http: XHttp, private val published: () -> Map<String, String>) {
    @Volatile private var made: RedditReading? = null

    fun current(): RedditReading {
        val agent = RedditClient.WEB_USER_AGENT_SLOT.valueIn(published())
        made?.takeIf { it.agent == agent }?.let { return it }
        return synchronized(this) { made?.takeIf { it.agent == agent } ?: RedditReading(agent, http).also { made = it } }
    }
}
