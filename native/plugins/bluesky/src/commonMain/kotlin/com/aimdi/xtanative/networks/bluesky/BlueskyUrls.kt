package com.aimdi.xtanative.networks.bluesky

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Bluesky's names and links as XTA accepts them: AppView addresses,
 * handles and DIDs, bsky.app links to posts, lists, feeds and starter
 * packs, and `at://` URIs.
 */
object BlueskyUrls {
    private val bskyHosts = setOf("bsky.app", "www.bsky.app")
    private val leadingAts = Regex("^@+")
    private val plcDid = Regex("did:plc:[a-z2-7]+")
    private val anyDid = Regex("did:[a-z0-9]+:[A-Za-z0-9._:%-]+")
    private val dnsHandle = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
    private val recordKey = Regex("[A-Za-z0-9._~:-]+")
    private val postAtUri = Regex("at://([^/?#]+)/app\\.bsky\\.feed\\.post/([A-Za-z0-9._~:-]{1,512})")
    private val postKey = Regex("[A-Za-z0-9._~:-]{1,512}")
    private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    /**
     * An AppView address as the reader typed it, made usable: `https://`
     * added to a bare host, and query, fragment and trailing slashes dropped.
     * Null when it is no http(s) address.
     */
    fun appView(input: String): String? {
        val value = input.trim().ifEmpty { return null }
        val url = (if ("://" in value) value else "https://$value").toHttpUrlOrNull() ?: return null
        val host = if (':' in url.host) "[${url.host}]" else url.host
        val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
        return "${url.scheme}://$host$port${url.encodedPath.trimEnd('/')}"
    }

    /** The AppView a setting names, or Bluesky's public one when it names none that works. */
    fun appViewOrDefault(setting: String?): String = appView(setting.orEmpty()) ?: Bluesky.DEFAULT_APP_VIEW

    /**
     * A handle or DID as typed: `@alice.bsky.social`, a bare dotted handle,
     * a bsky.app profile link, or a `did:plc:`. Lower case; null when it is
     * none of these (a bare name without a dot is not a handle).
     */
    fun handle(input: String): String? {
        val trimmed = input.trim().ifEmpty { return null }
        val fromLink = trimmed.toHttpUrlOrNull()?.takeIf { it.host in bskyHosts }?.pathSegments?.filter { it.isNotEmpty() }
            ?.takeIf { it.size >= 2 && it[0] == "profile" }?.get(1)
        val value = (fromLink ?: trimmed).replace(leadingAts, "").trim().lowercase()
        return value.takeIf { plcDid.matches(it) || dnsHandle.matches(it) }
    }

    /** Where a post lives on bsky.app, from its author's handle (else the DID in its URI) and its record key. */
    fun webUrl(handle: String, atUri: String): String? {
        val parts = atUri.removePrefix("at://").takeIf { atUri.startsWith("at://") }?.split('/') ?: return null
        if (parts.size != 3 || parts[1] != "app.bsky.feed.post") return null
        val actor = this.handle(handle) ?: this.handle(parts[0]) ?: return null
        val rkey = rkeyOf(atUri) ?: return null
        return "https://bsky.app/profile/$actor/post/$rkey"
    }

    /** The record key of an `at://did/collection/rkey` URI. */
    fun rkeyOf(atUri: String): String? {
        val parts = atUri.removePrefix("at://").takeIf { atUri.startsWith("at://") }?.split('/') ?: return null
        val rkey = parts.takeIf { it.size == 3 }?.last()?.trim() ?: return null
        return rkey.takeIf { recordKey.matches(it) && it != "." && it != ".." }
    }

    /** A public list's link (`bsky.app/profile/<actor>/lists/<rkey>`) or `at://…/app.bsky.graph.list/…`. */
    fun listRef(input: String): BlueskyRef? = ref(input, "/app.bsky.graph.list/", "lists")

    /** A custom feed's link (`bsky.app/profile/<actor>/feed/<rkey>`) or `at://…/app.bsky.feed.generator/…`. */
    fun feedRef(input: String): BlueskyRef? = ref(input, "/app.bsky.feed.generator/", "feed")

    /**
     * A starter pack's link (`bsky.app/starter-pack/<actor>/<rkey>`) or
     * `at://…/app.bsky.graph.starterpack/…`. Short `go.bsky.app` links are
     * not followed.
     */
    fun starterPackRef(input: String): BlueskyRef? {
        val value = input.trim().ifEmpty { return null }
        if (value.startsWith("at://") && "/app.bsky.graph.starterpack/" in value) return BlueskyRef.AtUri(value)
        val path = bskyPath(value)?.takeIf { it.size >= 3 && it[0] == "starter-pack" } ?: return null
        return web(path[1], path[2])
    }

    private fun ref(input: String, collection: String, section: String): BlueskyRef? {
        val value = input.trim().ifEmpty { return null }
        if (value.startsWith("at://") && collection in value) return BlueskyRef.AtUri(value)
        val path = bskyPath(value)?.takeIf { it.size >= 4 && it[0] == "profile" && it[2] == section } ?: return null
        return web(path[1], path[3])
    }

    private fun bskyPath(value: String): List<String>? =
        value.toHttpUrlOrNull()?.takeIf { it.host in bskyHosts }?.pathSegments?.filter { it.isNotEmpty() }

    private fun web(actor: String, rkey: String): BlueskyRef? =
        BlueskyRef.Web(actor.trim(), rkey.trim()).takeIf { it.actor.isNotEmpty() && it.rkey.isNotEmpty() }

    /**
     * The one post a search query names: a `https://bsky.app/profile/<actor>/post/<rkey>`
     * link (a query string allowed, nothing after the key) or a post's
     * `at://` URI. A link with a user, a port or another host names none.
     */
    fun searchPostTarget(input: String): BlueskyPostTarget? {
        val value = input.trim()
        postAtUri.matchEntire(value)?.let { match ->
            val (actor, rkey) = match.destructured
            return BlueskyPostTarget(actor, rkey).takeIf { searchActor(actor) != null && rkey != "." && rkey != ".." }
        }
        val segments = strictBskyUrl(value)?.pathSegments ?: return null
        val path = if (segments.lastOrNull() == "") segments.dropLast(1) else segments
        if (path.size != 4 || path[0] != "profile" || path[2] != "post") return null
        val (actor, rkey) = path[1] to path[3]
        return BlueskyPostTarget(actor, rkey).takeIf { searchActor(actor) != null && rkey != "." && rkey != ".." && postKey.matches(rkey) }
    }

    /**
     * The account a search query names: any DID, a `https://bsky.app/profile/<actor>`
     * link, or a handle as [handle] takes it. A link to anything else names none.
     */
    fun searchActor(input: String): String? {
        val value = input.trim()
        if (anyDid.matches(value)) return value
        if (!scheme.containsMatchIn(value)) return handle(value)
        val path = strictBskyUrl(value)?.pathSegments?.filter { it.isNotEmpty() } ?: return null
        return if (path.size == 2 && path[0] == "profile") searchActor(path[1]) else null
    }

    /** [value] as an https bsky.app link with no user and no port of its own. */
    private fun strictBskyUrl(value: String): HttpUrl? = value.toHttpUrlOrNull()?.takeIf {
        it.scheme == "https" && it.host in bskyHosts && it.username.isEmpty() && it.password.isEmpty() && it.port == 443
    }
}
