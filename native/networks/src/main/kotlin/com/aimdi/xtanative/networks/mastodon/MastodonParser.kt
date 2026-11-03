package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads Mastodon's REST answers, and the Misskey notes XTA falls back to, as
 * XTA's mastodon_models.dart does: nothing throws, a missing field is null or
 * empty, and a status with nothing to show is dropped instead of failing its
 * page. [homeDomain] is the host of the instance that answered: its local
 * accounts get it as their domain.
 */
object MastodonParser {
    // ---- statuses ----

    /** A list of statuses (an account's, a timeline, half a context); anything but a list reads as none. */
    fun statuses(json: J, homeDomain: String? = null): List<MastodonPost> = json.list.mapNotNull { status(it, homeDomain) }

    /**
     * One status, a boost unwrapped to the post it shares. Null without an
     * id or an address, or with nothing to show: no warning, text, media,
     * audio, link preview or poll.
     */
    fun status(json: J, homeDomain: String? = null, includeQuote: Boolean = true): MastodonPost? {
        val boosted = json["reblog"].exists
        val status = if (boosted) json["reblog"] else json
        val id = idOf(status["id"]).orEmpty().ifEmpty { return null }
        val spoiler = status["spoiler_text"].string?.trim().orEmpty()
        val body = MastodonText.ofHtml(status["content"].string)
        val media = media(status)
        val audio = audio(status)
        val card = card(status)
        val poll = poll(status)
        if (spoiler.isEmpty() && body.isEmpty() && media.isEmpty() && audio.isEmpty() && card == null && poll == null) return null
        val url = (status["url"].string?.trim() ?: status["uri"].string?.trim()).orEmpty().ifEmpty { return null }
        val author = profile(status["account"], homeDomain)
        val booster = if (boosted) profile(json["account"], homeDomain) else null
        return MastodonPost(
            id = id,
            acct = author.acct,
            authorName = author.displayName,
            text = body,
            url = url,
            timelineId = idOf(json["id"]),
            timelineAt = instant(json["created_at"].string),
            avatarUrl = author.avatarUrl,
            authorEmojis = author.emojis,
            spoilerText = spoiler,
            sensitive = status["sensitive"].boolean ?: spoiler.isNotEmpty(),
            media = media,
            audio = audio,
            publishedAt = instant(status["created_at"].string),
            boosted = boosted,
            boostedBy = booster?.displayName?.trim()?.ifEmpty { null },
            boostedByAcct = booster?.acct,
            replyToAcct = replyToAcct(status),
            replyToId = status["in_reply_to_id"].string,
            mentionAccts = mentionAccts(status),
            emojis = emojis(status["emojis"]),
            editedAt = instant(status["edited_at"].string),
            quote = if (includeQuote) quote(status, homeDomain) else null,
            repliesCount = count(status["replies_count"]),
            reblogsCount = count(status["reblogs_count"]),
            favouritesCount = count(status["favourites_count"]),
            linkCard = card,
            poll = poll,
        )
    }

    /** The two halves of `/statuses/:id/context`. */
    fun context(json: J, homeDomain: String? = null): MastodonContext =
        MastodonContext(statuses(json["ancestors"], homeDomain), statuses(json["descendants"], homeDomain))

    /** A reply names the first account it mentions: one answering its own author's post mentions nobody. */
    private fun replyToAcct(status: J): String? =
        if (!status["in_reply_to_id"].exists && !status["in_reply_to_account_id"].exists) null else mentionAccts(status).firstOrNull()

    private fun mentionAccts(status: J): List<String> =
        status["mentions"].list.mapNotNull { (it["acct"].string ?: it["username"].string)?.trim()?.ifEmpty { null } }

    /** The quoted status, whichever of the shapes Mastodon has used for quotes it comes in. */
    private fun quote(status: J, homeDomain: String?): MastodonPost? {
        val quoted = listOf(status["quote"]["quoted_status"], status["quoted_status"]).firstOrNull { it.exists }
            ?: status["quote"].takeIf { it["id"].exists && it["account"].exists }
            ?: return null
        return status(quoted, homeDomain, includeQuote = false)
    }

    // ---- attachments ----

    /**
     * Pictures, videos and GIFs. A `video` or `gifv` plays the MP4 under `url`
     * (`remote_url` while the instance has not cached it), its `preview_url` as
     * the poster; one without an MP4 is left out. Each picture once.
     */
    fun media(status: J): List<MastodonMedia> = status["media_attachments"].list.mapNotNull(::attachment).distinctBy { it.url }

    /** The `audio` attachments, which a card cannot draw and XTA leaves out of [media]. */
    fun audio(status: J): List<MastodonAudio> = status["media_attachments"].list
        .filter { it["type"].string == "audio" }
        .mapNotNull { audio ->
            val file = nonEmpty(audio["url"].string) ?: nonEmpty(audio["remote_url"].string) ?: return@mapNotNull null
            MastodonAudio(file, nonEmpty(audio["preview_url"].string), nonEmpty(audio["description"].string?.trim()), number(audio["meta"]["original"]["duration"]))
        }
        .distinctBy { it.url }

    private fun attachment(media: J): MastodonMedia? {
        val kind = when (media["type"].string) {
            "image" -> MastodonMedia.Kind.IMAGE
            "video" -> MastodonMedia.Kind.VIDEO
            "gifv" -> MastodonMedia.Kind.GIFV
            else -> return null
        }
        val video = kind != MastodonMedia.Kind.IMAGE
        val original = nonEmpty(media["url"].string) ?: nonEmpty(media["remote_url"].string)
        val shown = nonEmpty(media["preview_url"].string) ?: original.takeIf { !video } ?: return null
        if (video && original == null) return null
        val meta = media["meta"]["original"]
        return MastodonMedia(
            kind = kind,
            url = shown,
            originalUrl = original,
            description = nonEmpty(media["description"].string?.trim()),
            aspectRatio = aspectOf(meta),
            width = number(meta["width"])?.toInt(),
            height = number(meta["height"])?.toInt(),
            durationSeconds = number(meta["duration"]),
            blurhash = nonEmpty(media["blurhash"].string),
            focus = focusOf(media["meta"]["focus"]),
        )
    }

    /** `aspect` when it is a positive number, else `width` over `height`. */
    private fun aspectOf(original: J): Double? {
        if (original.raw !is JsonObject) return null
        number(original["aspect"])?.takeIf { it > 0 && it.isFinite() }?.let { return it }
        val width = number(original["width"]) ?: return null
        val height = number(original["height"]) ?: return null
        return if (width > 0 && height > 0) width / height else null
    }

    private fun focusOf(focus: J): MastodonMedia.Focus? {
        val x = number(focus["x"]) ?: return null
        val y = number(focus["y"]) ?: return null
        return MastodonMedia.Focus(x, y)
    }

    /** The preview card, or null when Mastodon sent no title, description or picture for it. */
    fun card(status: J): MastodonLinkCard? {
        val card = status["card"]
        val url = card["url"].string?.trim().orEmpty().ifEmpty { return null }
        fun text(key: String) = card[key].string?.trim()?.ifEmpty { null }
        val title = text("title")
        val description = text("description")
        val image = text("image")
        if (title == null && description == null && image == null) return null
        return MastodonLinkCard(url, title, description, image, text("provider_name"), text("type"))
    }

    /** The poll, its options without a title left out. Results count as held back when any option's count is missing. */
    fun poll(status: J): MastodonPoll? {
        val poll = status["poll"]
        if (!poll.exists) return null
        val options = poll["options"].list.mapNotNull { option ->
            option["title"].string?.trim()?.ifEmpty { null }?.let { MastodonPoll.Option(it, count(option["votes_count"])) }
        }
        if (options.isEmpty()) return null
        return MastodonPoll(
            options = options,
            votesCount = count(poll["votes_count"]),
            expired = poll["expired"].boolean ?: false,
            multiple = poll["multiple"].boolean ?: false,
            votersCount = integer(poll["voters_count"]),
            expiresAt = instant(poll["expires_at"].string),
            resultsAvailable = poll["options"].list.all { integer(it["votes_count"]) != null },
        )
    }

    /** Custom emoji (`emojis` of a status or an account) with a shortcode and an http(s) picture. */
    fun emojis(json: J): List<MastodonEmoji> = json.list.mapNotNull { emoji ->
        val shortcode = emoji["shortcode"].string?.trim().orEmpty()
        val still = MastodonUrls.web(emoji["static_url"].string)
        val url = MastodonUrls.web(emoji["url"].string) ?: still
        if (shortcode.isEmpty() || url == null) null else MastodonEmoji(shortcode, url, still)
    }

    // ---- accounts, tags, search ----

    /** An account; a local one gets [homeDomain] in its `acct`, and a missing display name reads as the `acct`. */
    fun profile(json: J, homeDomain: String? = null): MastodonProfile {
        val username = json["username"].string?.trim().orEmpty()
        val acct = MastodonAcct.canonical(json["acct"].string?.trim() ?: username, homeDomain)
        val name = json["display_name"].string?.trim()
        val avatar = json["avatar"].string?.trim() ?: json["avatar_static"].string?.trim()
        return MastodonProfile(
            id = idOf(json["id"]).orEmpty(),
            acct = acct,
            username = username.ifEmpty { acct.substringBefore('@') },
            displayName = if (name.isNullOrEmpty()) acct else name,
            note = MastodonText.ofHtml(json["note"].string),
            url = json["url"].string?.trim().orEmpty(),
            avatarUrl = avatar?.ifEmpty { null },
            headerUrl = MastodonUrls.web(json["header_static"].string) ?: MastodonUrls.web(json["header"].string),
            createdAt = instant(json["created_at"].string),
            followersCount = count(json["followers_count"]),
            followingCount = count(json["following_count"]),
            statusesCount = count(json["statuses_count"]),
            locked = json["locked"].boolean ?: false,
            bot = json["bot"].boolean ?: false,
            fields = fields(json),
            emojis = emojis(json["emojis"]),
        )
    }

    private fun fields(json: J): List<MastodonField> = json["fields"].list.mapNotNull { field ->
        val name = field["name"].string?.trim().orEmpty()
        val value = field["value"].string
        if (name.isEmpty()) null else MastodonField(name, MastodonText.ofHtml(value), MastodonText.singleLink(value), instant(field["verified_at"].string))
    }

    /** Hashtags (`/trends/tags`, search's `hashtags`), each with the uses of its days added up. */
    fun tags(json: J): List<MastodonTag> = json.list.mapNotNull { tag ->
        val name = tag["name"].string?.trim().orEmpty()
        val uses = tag["history"].list.sumOf { day -> day["uses"].string?.toLongOrNull() ?: integer(day["uses"]) ?: 0L }
        if (name.isEmpty()) null else MastodonTag(name, tag["url"].string, uses)
    }

    /** A guest search's accounts, statuses and hashtags. */
    fun search(json: J, homeDomain: String? = null): MastodonSearchPage = MastodonSearchPage(
        accounts = json["accounts"].list.map { profile(it, homeDomain) },
        posts = statuses(json["statuses"], homeDomain),
        tags = tags(json["hashtags"]),
    )

    // ---- Misskey ----

    /** A list of Misskey or Sharkey notes (`/api/notes/featured` and the like), read onto the same posts. */
    fun misskeyNotes(json: J, instance: String): List<MastodonPost> = json.list.mapNotNull { misskeyNote(it, instance) }

    /**
     * One note: a renote with no text of its own is a boost of the original,
     * one with text is a quote of it. [instance] is where it was read, which
     * is also where its address points.
     */
    fun misskeyNote(json: J, instance: String): MastodonPost? {
        val outerText = json["text"].string?.trim().orEmpty()
        val renote = json["renote"]
        val isRenote = renote.exists && outerText.isEmpty()
        val source = if (isRenote) renote else json
        val id = source["id"].string?.trim().orEmpty().ifEmpty { return null }
        val user = source["user"]
        val username = user["username"].string?.trim().orEmpty().ifEmpty { return null }
        val instanceHost = MastodonInstances.domain(instance)
        val host = user["host"].string?.trim()?.lowercase().orEmpty().ifEmpty { instanceHost.orEmpty() }
        val spoiler = source["cw"].string?.trim().orEmpty()
        val body = source["text"].string?.trim().orEmpty()
        val images = misskeyImages(source)
        if (spoiler.isEmpty() && body.isEmpty() && images.isEmpty() && !isRenote) return null
        val origin = instanceHost ?: host
        val booster = json["user"]
        val boosterUser = booster["username"].string?.trim().orEmpty()
        val boosterHost = booster["host"].string?.trim().orEmpty()
        return MastodonPost(
            id = id,
            acct = if (host.isEmpty()) username else "$username@$host",
            authorName = user["name"].string?.trim().orEmpty().ifEmpty { username },
            text = body,
            url = "https://$origin/notes/$id",
            timelineId = json["id"].string,
            timelineAt = instant(json["createdAt"].string),
            avatarUrl = user["avatarUrl"].string,
            spoilerText = spoiler,
            sensitive = spoiler.isNotEmpty(),
            media = images,
            publishedAt = instant(source["createdAt"].string),
            boosted = isRenote,
            boostedBy = if (isRenote) booster["name"].string?.trim().orEmpty().ifEmpty { boosterUser } else null,
            boostedByAcct = if (!isRenote || boosterUser.isEmpty()) null else MastodonAcct.canonical(boosterUser, boosterHost.ifEmpty { origin }),
            replyToAcct = misskeyReplyTo(source),
            replyToId = source["replyId"].string,
            quote = if (renote.exists && outerText.isNotEmpty()) misskeyNote(renote, instance)?.copy(quote = null) else null,
            repliesCount = count(source["repliesCount"]),
            reblogsCount = count(source["renoteCount"]),
            favouritesCount = reactions(source),
        )
    }

    private fun misskeyImages(note: J): List<MastodonMedia> = note["files"].list
        .mapNotNull { file -> (file["thumbnailUrl"].string ?: file["url"].string)?.takeIf { it.startsWith("http") } }
        .map { MastodonMedia(MastodonMedia.Kind.IMAGE, it) }

    private fun misskeyReplyTo(note: J): String? {
        val user = note["reply"]["user"]
        val username = user["username"].string?.trim().orEmpty().ifEmpty { return null }
        val host = user["host"].string?.trim().orEmpty()
        return if (host.isEmpty()) username else "$username@$host"
    }

    /** All reactions added up: what Misskey has in place of favourites. */
    private fun reactions(note: J): Long =
        (note["reactions"].raw as? JsonObject)?.values?.sumOf { number(J(it))?.toLong() ?: 0L } ?: 0L

    // ---- values ----

    /** The id of a timeline entry as sent: a boost's own id, a renote's own: what the next page starts below. */
    internal fun entryId(entry: J): String? = idOf(entry["id"])

    /** An id written as a string or a number. */
    private fun idOf(json: J): String? = json.string ?: integer(json)?.toString()

    /** A whole number as XTA's `Json.integer` reads one: a number, cut to whole, or a string of digits. */
    private fun integer(json: J): Long? {
        val value = json.raw as? JsonPrimitive ?: return null
        return if (value.isString) value.content.toLongOrNull() else value.longOrNull ?: value.doubleOrNull?.toLong()
    }

    private fun count(json: J): Long = integer(json) ?: 0L

    /** A JSON number; a string holding one is not, as in XTA. */
    private fun number(json: J): Double? = (json.raw as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    private fun nonEmpty(value: String?): String? = value?.ifEmpty { null }

    /** An ISO 8601 time, with or without a zone (then UTC), or a bare date; null for anything else. */
    internal fun instant(text: String?): Instant? {
        val value = text?.trim()?.ifEmpty { null } ?: return null
        return runCatching { Instant.parse(value) }
            .recoverCatching { OffsetDateTime.parse(value).toInstant() }
            .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
            .recoverCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant() }
            .getOrNull()
    }
}
