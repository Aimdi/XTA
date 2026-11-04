package com.aimdi.xtanative.core.x.model

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Posts written to disk and read back, so the feed opens with what it showed
 * last before X has answered. Its own small format, versioned: a file of an
 * older shape is dropped rather than misread. Fields added since version 1
 * are optional, so a file without them still reads. What a row does not draw
 * (a profile's header, an article's body) is not kept.
 */
object XCodec {
    private const val VERSION = 1

    fun encode(threads: List<XThread>): String = buildJsonObject {
        put("v", VERSION)
        put("threads", JsonArray(threads.map(::threadJson)))
    }.toString()

    /** The threads in [text]; empty when it is unreadable or of another version. */
    fun decode(text: String): List<XThread> {
        val root = J.parse(text)
        if (root["v"].int != VERSION) return emptyList()
        return root["threads"].list.mapNotNull(::threadOf)
    }

    internal fun threadJson(thread: XThread) = buildJsonObject {
        put("id", thread.id)
        put("posts", JsonArray(thread.posts.map(::post)))
        if (thread.withheld.isNotEmpty()) put("withheld", JsonArray(thread.withheld.map(::withheld)))
    }

    internal fun threadOf(json: J): XThread? {
        val id = json["id"].string ?: return null
        val posts = json["posts"].list.mapNotNull(::post)
        val withheld = json["withheld"].list.mapNotNull(::withheld)
        return if (posts.isEmpty() && withheld.isEmpty()) null else XThread(id, posts, withheld)
    }

    private fun post(post: XPost): JsonObject = buildJsonObject {
        put("id", post.id)
        put("author", user(post.author))
        post.createdAt?.let { put("at", it.toEpochMilli()) }
        put("text", post.text)
        put("ds", post.displayStart)
        post.displayEnd?.let { put("de", it) }
        put("entities", JsonArray(post.entities.map(::entity)))
        put("media", JsonArray(post.media.map(::media)))
        post.card?.let { put("card", card(it)) }
        post.quoted?.let { put("quoted", post(it)) }
        post.reposted?.let { put("reposted", post(it)) }
        put("replies", post.replyCount)
        put("reposts", post.repostCount)
        put("quotes", post.quoteCount)
        put("likes", post.likeCount)
        put("bookmarks", post.bookmarkCount)
        post.viewCount?.let { put("views", it) }
        post.replyToScreenName?.let { put("replyTo", it) }
        post.conversationId?.let { put("conversation", it) }
        extras(post)
    }

    private fun JsonObjectBuilder.extras(post: XPost) {
        post.lang?.let { put("lang", it) }
        if (post.sensitive) put("sensitive", true)
        post.replyToPostId?.let { put("replyToId", it) }
        post.communityId?.let { put("community", it) }
        if (post.subscriberPreview) put("preview", true)
        post.note?.let { put("note", note(it)) }
        post.article?.let { put("article", article(it)) }
        post.live?.let { put("live", live(it)) }
        post.grokShare?.let { put("grok", grokShare(it)) }
        post.quotedWithheld?.let { put("quotedWithheld", withheld(it)) }
    }

    private fun post(json: J): XPost? {
        val id = json["id"].string ?: return null
        val author = user(json["author"]) ?: return null
        return XPost(
            id = id,
            author = author,
            createdAt = json["at"].long?.let(Instant::ofEpochMilli),
            text = json["text"].string.orEmpty(),
            displayStart = json["ds"].int ?: 0,
            displayEnd = json["de"].int,
            entities = json["entities"].list.mapNotNull(::entity),
            media = json["media"].list.mapNotNull(::media),
            card = json["card"].takeIf { it.exists }?.let(::card),
            quoted = json["quoted"].takeIf { it.exists }?.let(::post),
            reposted = json["reposted"].takeIf { it.exists }?.let(::post),
            replyCount = json["replies"].long ?: 0,
            repostCount = json["reposts"].long ?: 0,
            quoteCount = json["quotes"].long ?: 0,
            likeCount = json["likes"].long ?: 0,
            bookmarkCount = json["bookmarks"].long ?: 0,
            viewCount = json["views"].long,
            replyToScreenName = json["replyTo"].string,
            conversationId = json["conversation"].string,
            lang = json["lang"].string,
            sensitive = json["sensitive"].boolean ?: false,
            replyToPostId = json["replyToId"].string,
            communityId = json["community"].string,
            subscriberPreview = json["preview"].boolean ?: false,
            note = json["note"].takeIf { it.exists }?.let(::note),
            article = json["article"].takeIf { it.exists }?.let(::article),
            live = json["live"].takeIf { it.exists }?.let(::live),
            grokShare = json["grok"].takeIf { it.exists }?.let(::grokShare),
            quotedWithheld = json["quotedWithheld"].takeIf { it.exists }?.let(::withheld),
        )
    }

    private fun withheld(withheld: XWithheld) = buildJsonObject {
        put("id", withheld.id)
        withheld.reason?.let { put("reason", it) }
        if (withheld.subscribersOnly) put("subscribers", true)
        withheld.screenName?.let { put("sn", it) }
    }

    private fun withheld(json: J): XWithheld? =
        json["id"].string?.let { XWithheld(it, json["reason"].string, json["subscribers"].boolean ?: false, json["sn"].string) }

    private fun note(note: XCommunityNote) = buildJsonObject {
        put("text", note.text)
        put("links", JsonArray(note.links.map(::entity)))
        note.title?.let { put("title", it) }
        note.url?.let { put("url", it) }
    }

    private fun note(json: J): XCommunityNote? = json["text"].string?.let { text ->
        XCommunityNote(text, json["links"].list.mapNotNull(::entity).filterIsInstance<XEntity.Link>(), json["title"].string, json["url"].string)
    }

    private fun article(article: XArticle) = buildJsonObject {
        article.id?.let { put("id", it) }
        put("title", article.title)
        put("preview", article.previewText)
        article.coverUrl?.let { put("cover", it) }
    }

    private fun article(json: J): XArticle =
        XArticle(json["id"].string, json["title"].string.orEmpty(), json["preview"].string.orEmpty(), json["cover"].string)

    private fun live(live: XLive) = buildJsonObject {
        live.mediaKey?.let { put("key", it) }
        live.broadcastId?.let { put("broadcast", it) }
        live.spaceId?.let { put("space", it) }
        live.imageUrl?.let { put("image", it) }
        put("aspect", live.aspectRatio)
    }

    private fun live(json: J): XLive = XLive(
        mediaKey = json["key"].string,
        broadcastId = json["broadcast"].string,
        spaceId = json["space"].string,
        imageUrl = json["image"].string,
        aspectRatio = json["aspect"].double?.toFloat() ?: (16f / 9f),
    )

    private fun grokShare(share: XGrokShare) = buildJsonObject {
        put("url", share.url)
        put("question", share.question)
        put("answer", share.answer)
        put("grok", share.grokScreenName)
        share.grokImageUrl?.let { put("image", it) }
    }

    private fun grokShare(json: J): XGrokShare? {
        val url = json["url"].string ?: return null
        val question = json["question"].string ?: return null
        return XGrokShare(url, question, json["answer"].string.orEmpty(), json["grok"].string ?: "grok", json["image"].string)
    }

    private fun card(card: XCard): JsonObject = when (card) {
        is XCard.Link -> buildJsonObject {
            put("k", "link")
            put("url", card.url)
            put("title", card.title)
            card.description?.let { put("description", it) }
            card.domain?.let { put("domain", it) }
            card.imageUrl?.let { put("image", it) }
            card.imageWidth?.let { put("w", it) }
            card.imageHeight?.let { put("h", it) }
            put("large", card.large)
            if (card.media.isNotEmpty()) put("media", JsonArray(card.media.map(::media)))
            card.kind?.let { put("kind", it) }
        }
        is XCard.Poll -> buildJsonObject {
            put("k", "poll")
            put(
                "choices",
                JsonArray(
                    card.choices.map { choice ->
                        buildJsonObject {
                            put("label", choice.label)
                            put("votes", choice.votes)
                            choice.imageUrl?.let { put("image", it) }
                            choice.fullImageUrl?.let { put("full", it) }
                        }
                    },
                ),
            )
            card.endsAt?.let { put("ends", it.toEpochMilli()) }
            put("final", card.final)
        }
    }

    private fun card(json: J): XCard? = when (json["k"].string) {
        "link" -> linkCard(json)
        "poll" -> XCard.Poll(
            choices = json["choices"].list.mapNotNull { choice ->
                XCard.Poll.Choice(choice["label"].string ?: return@mapNotNull null, choice["votes"].long ?: 0, choice["image"].string, choice["full"].string)
            },
            endsAt = json["ends"].long?.let(Instant::ofEpochMilli),
            final = json["final"].boolean ?: false,
        )
        else -> null
    }

    private fun linkCard(json: J): XCard.Link? {
        val url = json["url"].string ?: return null
        val title = json["title"].string ?: return null
        return XCard.Link(
            url = url,
            title = title,
            description = json["description"].string,
            domain = json["domain"].string,
            imageUrl = json["image"].string,
            imageWidth = json["w"].int,
            imageHeight = json["h"].int,
            large = json["large"].boolean ?: false,
            media = json["media"].list.mapNotNull(::media),
            kind = json["kind"].string,
        )
    }

    private fun user(user: XUser) = buildJsonObject {
        put("id", user.id)
        put("sn", user.screenName)
        put("name", user.name)
        user.avatarUrl?.let { put("avatar", it) }
        put("verified", user.verified)
        put("protected", user.protected)
        if (user.verifiedType != plainCheck(user.verified)) put("check", user.verifiedType.name)
        user.affiliation?.let { affiliation ->
            put(
                "badge",
                buildJsonObject {
                    put("url", affiliation.badgeUrl)
                    affiliation.name?.let { put("name", it) }
                    affiliation.profileUrl?.let { put("profile", it) }
                    affiliation.labelType?.let { put("type", it) }
                },
            )
        }
    }

    private fun user(json: J): XUser? {
        val id = json["id"].string ?: return null
        val screenName = json["sn"].string ?: return null
        val verified = json["verified"].boolean ?: false
        val badge = json["badge"]
        return XUser(
            id = id,
            screenName = screenName,
            name = json["name"].string ?: screenName,
            avatarUrl = json["avatar"].string,
            verified = verified,
            protected = json["protected"].boolean ?: false,
            verifiedType = json["check"].string?.let { runCatching { XVerifiedType.valueOf(it) }.getOrNull() } ?: plainCheck(verified),
            affiliation = badge["url"].string?.let { XAffiliation(it, badge["name"].string, badge["profile"].string, badge["type"].string) },
        )
    }

    /** The check a bare `verified` flag stands for: only another one is written. */
    private fun plainCheck(verified: Boolean) = if (verified) XVerifiedType.BLUE else XVerifiedType.NONE

    private fun entity(entity: XEntity): JsonElement = buildJsonObject {
        put("s", entity.start)
        put("e", entity.end)
        when (entity) {
            is XEntity.Link -> { put("k", "link"); put("url", entity.url); put("expanded", entity.expanded); put("display", entity.display) }
            is XEntity.Mention -> { put("k", "mention"); put("value", entity.screenName) }
            is XEntity.Hashtag -> { put("k", "hashtag"); put("value", entity.tag) }
            is XEntity.Cashtag -> { put("k", "cashtag"); put("value", entity.symbol) }
        }
    }

    private fun entity(json: J): XEntity? {
        val start = json["s"].int ?: return null
        val end = json["e"].int ?: return null
        val value = json["value"].string
        return when (json["k"].string) {
            "link" -> XEntity.Link(start, end, json["url"].string ?: return null, json["expanded"].string.orEmpty(), json["display"].string.orEmpty())
            "mention" -> XEntity.Mention(start, end, value ?: return null)
            "hashtag" -> XEntity.Hashtag(start, end, value ?: return null)
            "cashtag" -> XEntity.Cashtag(start, end, value ?: return null)
            else -> null
        }
    }

    private fun media(media: XMedia) = buildJsonObject {
        put("kind", media.kind.name)
        put("url", media.url)
        media.width?.let { put("w", it) }
        media.height?.let { put("h", it) }
        media.alt?.let { put("alt", it) }
        put(
            "variants",
            buildJsonArray {
                media.variants.forEach { variant ->
                    add(
                        buildJsonObject {
                            put("url", variant.url)
                            variant.bitrate?.let { put("bitrate", it) }
                            put("type", variant.contentType)
                        },
                    )
                }
            },
        )
        media.durationMs?.let { put("ms", it) }
        media.shortUrl?.let { put("short", it) }
    }

    private fun media(json: J): XMedia? {
        val url = json["url"].string ?: return null
        val kind = runCatching { XMedia.Kind.valueOf(json["kind"].string.orEmpty()) }.getOrNull() ?: return null
        return XMedia(
            kind = kind,
            url = url,
            width = json["w"].int,
            height = json["h"].int,
            alt = json["alt"].string,
            variants = json["variants"].list.mapNotNull { variant ->
                XVideoVariant(variant["url"].string ?: return@mapNotNull null, variant["bitrate"].int, variant["type"].string.orEmpty())
            },
            durationMs = json["ms"].long,
            shortUrl = json["short"].string,
        )
    }
}
