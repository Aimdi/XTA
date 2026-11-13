package com.aimdi.xtanative.core.x.model

import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A live video (a broadcast, or its recording) or a Space, as XTA's
 * broadcasts.dart finds one in a post or a link: what the player needs to ask
 * X for its stream. A card's [mediaKey] is enough; otherwise the
 * [broadcastId] or [spaceId] is turned into one first.
 */
data class XLive(
    val mediaKey: String? = null,
    val broadcastId: String? = null,
    val spaceId: String? = null,
    val imageUrl: String? = null,
    val aspectRatio: Float = 16f / 9f,
) {
    val isSpace: Boolean get() = !spaceId.isNullOrEmpty()

    /** Whether there is anything to ask X with. */
    val canResolve: Boolean get() = !mediaKey.isNullOrEmpty() || !broadcastId.isNullOrEmpty() || !spaceId.isNullOrEmpty()

    /** The page to watch or listen on: the Space if there is one, else the broadcast. */
    val watchUrl: String?
        get() = when {
            isSpace -> XLiveLinks.spaceUrl(spaceId!!)
            !broadcastId.isNullOrEmpty() -> XLiveLinks.broadcastUrl(broadcastId)
            else -> null
        }

    companion object {
        /** The live room a link opens: a Space, else a broadcast. */
        fun fromUrl(url: String): XLive =
            XLiveLinks.spaceId(url)?.let { XLive(spaceId = it) } ?: XLive(broadcastId = XLiveLinks.broadcastId(url))
    }
}

/**
 * Broadcast and Space links, as XTA's urls.dart reads them: on x.com and
 * twitter.com (`/i/broadcasts/…`, `/i/spaces/…`, either in the singular) and
 * Periscope's `/w/…`. A display address X cut short (`…`) is no link: its id
 * would be wrong.
 */
object XLiveLinks {
    private val xHosts = setOf("x.com", "www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")
    private val periscopeHosts = setOf("pscp.tv", "www.pscp.tv", "periscope.tv", "www.periscope.tv")
    private val trailingPunctuation = Regex("[.,);]+$")
    private val broadcastText = Regex(
        """(?:https?://)?(?:(?:www|mobile)\.)?(?:x\.com|twitter\.com)/i/broadcasts?/\S+|https?://(?:www\.)?(?:pscp|periscope)\.tv/w/\S+""",
        RegexOption.IGNORE_CASE,
    )
    private val spaceText = Regex("""(?:https?://)?(?:(?:www|mobile)\.)?(?:x\.com|twitter\.com)/i/spaces?/\S+""", RegexOption.IGNORE_CASE)

    private data class Link(val id: String, val kind: String, val periscope: Boolean = false)

    fun broadcastUrl(id: String): String = "https://x.com/i/broadcasts/$id"

    fun spaceUrl(id: String): String = "https://x.com/i/spaces/$id"

    /** The broadcast id in [url], or null when it is no broadcast link. */
    fun broadcastId(url: String?): String? {
        val link = link(url) ?: return null
        return link.id.takeIf { link.periscope || link.kind == "broadcasts" || link.kind == "broadcast" }
    }

    /** The Space id in [url], or null when it is no Space link. */
    fun spaceId(url: String?): String? {
        val link = link(url) ?: return null
        return link.id.takeIf { !link.periscope && (link.kind == "spaces" || link.kind == "space") }
    }

    /** The first broadcast id in free text. */
    fun broadcastIdInText(text: String?): String? = firstIn(text, broadcastText, ::broadcastId)

    /** The first Space id in free text. */
    fun spaceIdInText(text: String?): String? = firstIn(text, spaceText, ::spaceId)

    /** Whether [url] opens a live room or a Space. */
    fun isLive(url: String?): Boolean = spaceId(url) != null || broadcastId(url) != null

    private fun firstIn(text: String?, pattern: Regex, parse: (String) -> String?): String? =
        if (text.isNullOrEmpty()) null else pattern.findAll(text).firstNotNullOfOrNull { parse(it.value) }

    private fun link(url: String?): Link? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty() || raw.contains('…') || raw.lowercase().contains("%e2%80%a6") || raw.contains("...")) return null
        val trimmed = raw.replace(trailingPunctuation, "")
        val parsed = (if (trimmed.contains("://")) trimmed else "https://$trimmed").toHttpUrlOrNull() ?: return null
        val host = parsed.host.lowercase()
        val parts = parsed.pathSegments.filter { it.isNotEmpty() }
        return when {
            host in periscopeHosts -> if (parts.size >= 2 && parts[0] == "w") Link(parts[1], "pscp", periscope = true) else null
            host in xHosts && parts.size >= 3 && parts[0] == "i" -> Link(parts[2], parts[1])
            else -> null
        }
    }
}

/**
 * A Space as AudioSpaceById describes it. [mediaKey] is what its stream is
 * asked for with; the rest is what a Space's header shows.
 */
data class XAudioSpace(
    val id: String,
    val mediaKey: String?,
    val title: String?,
    /** X's state: `Running`, `Ended`, `NotStarted` and the like. */
    val state: String?,
    val creator: XUser?,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val scheduledStart: Instant?,
    val liveListeners: Long?,
    val replayWatched: Long?,
    val replayAvailable: Boolean,
)
