package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import java.util.Collections
import okhttp3.HttpUrl

/** A response under test resources' `bluesky/`. */
internal fun fixture(name: String): String = FakeAppView::class.java.getResource("/bluesky/$name")!!.readText()

internal fun fixtureJson(name: String): J = J.parse(fixture(name))

internal fun ok(body: String) = XResponse(200, body)

/** A stand-in AppView: answers every GET with [answer], and keeps what was asked and how. */
internal class FakeAppView(private val answer: (HttpUrl) -> XResponse) : XHttp {
    val asked: MutableList<HttpUrl> = Collections.synchronizedList(ArrayList())
    var headers: Map<String, String> = emptyMap()
    var followedRedirects: Boolean? = null

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
        asked += url
        this.headers = headers
        followedRedirects = followRedirects
        return answer(url)
    }

    /** The XRPC methods asked for, in order. */
    val methods: List<String> get() = asked.map { it.encodedPath.substringAfterLast('/') }
}

internal fun at(text: String): Instant = Instant.parse(text)

/** A post URI as XTA's tests write them: every post under maya's DID, so a parent is named by its id alone. */
internal fun uriOf(id: String) = "at://did:plc:maya/app.bsky.feed.post/$id"

/** A post as XTA's test harness builds one: by Maya unless said otherwise. */
internal fun bluePost(
    id: String,
    parent: String? = null,
    did: String = "did:plc:maya",
    handle: String = if (did == "did:plc:maya") "maya.bsky.social" else "${did.substringAfterLast(':')}.example",
    authorName: String = "Maya Chen",
    text: String = id,
    likes: Long = 0,
    publishedAt: Instant? = null,
    repostedByHandle: String? = null,
    repostedByDid: String? = null,
    repostedAt: Instant? = null,
    media: List<BlueskyMedia> = emptyList(),
    labels: List<String> = emptyList(),
    isReply: Boolean = parent != null,
    quote: BlueskyPost? = null,
    linkCard: BlueskyLinkCard? = null,
    facets: List<BlueskyFacet> = emptyList(),
) = BlueskyPost(
    uri = uriOf(id),
    cid = "cid-$id",
    handle = handle,
    did = did,
    authorName = authorName,
    text = text,
    url = "https://bsky.app/profile/$handle/post/$id",
    media = media,
    facets = facets,
    publishedAt = publishedAt,
    repostedAt = repostedAt,
    likeCount = likes,
    repostedByHandle = repostedByHandle,
    repostedByDid = repostedByDid,
    quotedPost = quote,
    linkCard = linkCard,
    isReply = isReply,
    replyToUri = parent?.let(::uriOf),
    labels = labels,
)

/** A minimal PostView of [id] by [handle], as an AppView answers it. */
internal fun postView(id: String, handle: String = "alice.bsky.social", did: String = "did:plc:alice", text: String = id): String =
    """{"uri":"at://$did/app.bsky.feed.post/$id","cid":"cid-$id","author":{"did":"$did","handle":"$handle"},""" +
        """"record":{"text":"$text","createdAt":"2026-09-20T10:00:00Z"}}"""
