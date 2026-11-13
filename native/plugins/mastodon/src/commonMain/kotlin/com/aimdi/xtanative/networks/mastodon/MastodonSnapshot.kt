package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A post as XTA keeps it on the device (mastodon_snapshot.dart, version 1):
 * what Saved files and what the reading places paint at once on the next
 * start. Its media is written as XTA's parallel lists, so a post saved in
 * one app opens in the other.
 */
object MastodonSnapshot {
    private const val VERSION = 1

    fun encode(post: MastodonPost): JsonObject = buildJsonObject {
        put("version", VERSION)
        put("id", post.id)
        put("timelineId", post.timelineId)
        put("timelineAt", post.timelineAt?.toString())
        put("acct", post.acct)
        put("name", post.authorName)
        put("avatar", post.avatarUrl)
        put("text", post.text)
        put("url", post.url)
        put("spoiler", post.spoilerText)
        put("sensitive", post.sensitive)
        put("images", array(post.media.map { JsonPrimitive(it.url) }))
        put("imageAspects", array(post.media.map { JsonPrimitive(it.aspectRatio) }))
        put("imageAlts", array(post.media.map { JsonPrimitive(it.description) }))
        put("imageDownloadUrls", array(post.media.map { JsonPrimitive(it.originalUrl) }))
        put("imageIsVideo", array(post.media.map { JsonPrimitive(it.isVideo) }))
        put("imageIsGif", array(post.media.map { JsonPrimitive(it.isGif) }))
        put("published", post.publishedAt?.toString())
        put("edited", post.editedAt?.toString())
        put("boosted", post.boosted)
        put("boostedBy", post.boostedBy)
        put("boostedByAcct", post.boostedByAcct)
        put("replyToAcct", post.replyToAcct)
        put("replyToId", post.replyToId)
        put("mentions", array(post.mentionAccts.map(::JsonPrimitive)))
        put("replies", post.repliesCount)
        put("boosts", post.reblogsCount)
        put("favourites", post.favouritesCount)
        post.quote?.let { put("quote", encode(it)) }
        post.linkCard?.let { put("card", card(it)) }
        post.poll?.let { put("poll", poll(it)) }
    }

    /** The post [value] holds; null for anything that is no version 1 snapshot with an id, an author and a web address. */
    fun decode(value: JsonElement?, includeQuote: Boolean = true): MastodonPost? {
        val json = J(value)
        val id = json["id"].string.orEmpty()
        val acct = json["acct"].string.orEmpty()
        val url = json["url"].string.orEmpty()
        val web = WebUrl.parse(url)
        if (json["version"].int != VERSION || id.isEmpty() || acct.isEmpty() || web == null || !web.isWeb) return null
        return MastodonPost(
            id = id,
            acct = acct,
            authorName = json["name"].string ?: acct,
            text = json["text"].string.orEmpty(),
            url = url,
            timelineId = json["timelineId"].string,
            timelineAt = instant(json["timelineAt"]),
            avatarUrl = json["avatar"].string,
            spoilerText = json["spoiler"].string.orEmpty(),
            sensitive = json["sensitive"].boolean ?: false,
            media = media(json),
            publishedAt = instant(json["published"]),
            editedAt = instant(json["edited"]),
            boosted = json["boosted"].boolean ?: false,
            boostedBy = json["boostedBy"].string,
            boostedByAcct = json["boostedByAcct"].string,
            replyToAcct = json["replyToAcct"].string,
            replyToId = json["replyToId"].string,
            mentionAccts = json["mentions"].list.mapNotNull { it.string },
            repliesCount = json["replies"].long ?: 0,
            reblogsCount = json["boosts"].long ?: 0,
            favouritesCount = json["favourites"].long ?: 0,
            quote = if (includeQuote) decode(json["quote"].raw, includeQuote = false) else null,
            linkCard = json["card"].takeIf { it.exists }?.let(::card),
            poll = json["poll"].takeIf { it.exists }?.let(::poll),
        )
    }

    // ---- Saved ----

    /** What Saved keeps the post under: status ids are local to an instance, its address is not. */
    fun archiveId(post: MastodonPost): String = "mastodon:${post.url}"

    /** What Saved keeps of the post, as XTA's mastodonArchiveBlob writes it. */
    fun archive(post: MastodonPost): JsonObject = buildJsonObject {
        put("xtaPlugin", "mastodon")
        put("post", encode(post))
    }

    /** The post a saved row holds; null for one of another network. */
    fun fromArchive(value: JsonElement?): MastodonPost? {
        val json = J(value)
        return if (json["xtaPlugin"].string == "mastodon") decode(json["post"].raw) else null
    }

    // ---- parts ----

    private fun media(json: J): List<MastodonMedia> {
        val aspects = json["imageAspects"].list
        val alts = json["imageAlts"].list
        val originals = json["imageDownloadUrls"].list
        val videos = json["imageIsVideo"].list
        val gifs = json["imageIsGif"].list
        return json["images"].list.mapIndexedNotNull { i, image ->
            val url = image.string ?: return@mapIndexedNotNull null
            val video = videos.getOrNull(i)?.boolean ?: false
            val gif = gifs.getOrNull(i)?.boolean ?: false
            MastodonMedia(
                kind = when {
                    gif -> MastodonMedia.Kind.GIFV
                    video -> MastodonMedia.Kind.VIDEO
                    else -> MastodonMedia.Kind.IMAGE
                },
                url = url,
                originalUrl = originals.getOrNull(i)?.string,
                description = alts.getOrNull(i)?.string,
                aspectRatio = aspects.getOrNull(i)?.double,
            )
        }
    }

    private fun card(card: MastodonLinkCard): JsonObject = buildJsonObject {
        put("url", card.url)
        put("title", card.title)
        put("description", card.description)
        put("image", card.imageUrl)
        put("provider", card.providerName)
        put("type", card.type)
    }

    private fun card(json: J): MastodonLinkCard = MastodonLinkCard(
        url = json["url"].string.orEmpty(),
        title = json["title"].string,
        description = json["description"].string,
        imageUrl = json["image"].string,
        providerName = json["provider"].string,
        type = json["type"].string,
    )

    private fun poll(poll: MastodonPoll): JsonObject = buildJsonObject {
        put("votes", poll.votesCount)
        put("expired", poll.expired)
        put("multiple", poll.multiple)
        put("voters", poll.votersCount)
        put("expires", poll.expiresAt?.toString())
        put("resultsAvailable", poll.resultsAvailable)
        put("options", array(poll.options.map { buildJsonObject { put("title", it.title); put("votes", it.votes) } }))
    }

    private fun poll(json: J): MastodonPoll = MastodonPoll(
        options = json["options"].list.map { MastodonPoll.Option(it["title"].string.orEmpty(), it["votes"].long ?: 0) },
        votesCount = json["votes"].long ?: 0,
        expired = json["expired"].boolean ?: false,
        multiple = json["multiple"].boolean ?: false,
        votersCount = json["voters"].long,
        expiresAt = instant(json["expires"]),
        resultsAvailable = json["resultsAvailable"].boolean ?: true,
    )

    private fun array(items: List<JsonElement>): JsonArray = JsonArray(items)

    private fun instant(json: J): Instant? = MastodonParser.instant(json.string)
}
