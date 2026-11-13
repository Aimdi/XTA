package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The parser against XTA's: xta_parsed.json is what XTA's own
 * mastodon_models.dart (run unchanged) read from this folder's fixtures. Every
 * field XTA has is compared; what XTA Native reads beyond it (audio, custom
 * emoji, media meta) has tests of its own.
 */
class MastodonXtaParsingTest {
    private fun resource(name: String) = javaClass.getResource("/mastodon/$name")!!.readText()

    private val xta = J.parse(resource("xta_parsed.json"))

    private fun fixture(name: String) = J.parse(resource(name))

    private fun assertSame(xtas: J, ours: JsonElement) {
        val wrong = differences(ours, xtas.raw ?: JsonNull, "")
        assertTrue(wrong.isEmpty(), "${wrong.size} fields differ from XTA:\n" + wrong.joinToString("\n"))
    }

    private fun differences(ours: JsonElement, xtas: JsonElement, path: String): List<String> = when {
        ours is JsonObject && xtas is JsonObject ->
            (ours.keys + xtas.keys).flatMap { differences(ours[it] ?: JsonNull, xtas[it] ?: JsonNull, "$path.$it") }
        ours is JsonArray && xtas is JsonArray && ours.size == xtas.size -> ours.indices.flatMap { differences(ours[it], xtas[it], "$path[$it]") }
        ours == xtas -> emptyList()
        else -> listOf("$path\n  ours: $ours\n  XTA:  $xtas")
    }

    private fun media(media: List<MastodonMedia>) = buildJsonArray {
        media.forEach { item ->
            add(
                buildJsonObject {
                    put("url", item.url)
                    put("downloadUrl", item.originalUrl)
                    put("aspect", item.aspectRatio)
                    put("alt", item.description)
                    put("isVideo", item.isVideo)
                    put("videoUrl", item.videoUrl)
                    put("isGif", item.isGif)
                },
            )
        }
    }

    private fun quote(quote: MastodonPost) = buildJsonObject {
        put("id", quote.id)
        put("acct", quote.acct)
        put("authorName", quote.authorName)
        put("text", quote.text)
        put("spoilerText", quote.spoilerText)
        put("sensitive", quote.sensitive)
        put("url", quote.url)
        put("media", media(quote.media))
        put("avatarUrl", quote.avatarUrl)
        put("publishedAt", quote.publishedAt?.toEpochMilli())
    }

    private fun post(post: MastodonPost): JsonObject = buildJsonObject {
        put("id", post.id)
        put("timelineId", post.timelineId)
        put("timelineAt", post.timelineAt?.toEpochMilli())
        put("acct", post.acct)
        put("authorName", post.authorName)
        put("avatarUrl", post.avatarUrl)
        put("text", post.text)
        put("spoilerText", post.spoilerText)
        put("sensitive", post.sensitive)
        put("media", media(post.media))
        put("publishedAt", post.publishedAt?.toEpochMilli())
        put("url", post.url)
        put("boosted", post.boosted)
        put("boostedBy", post.boostedBy)
        put("boostedByAcct", post.boostedByAcct)
        put("replyToAcct", post.replyToAcct)
        put("replyToId", post.replyToId)
        put("isReply", post.isReply)
        put("pagingId", post.pagingId)
        putJsonArray("mentionAccts") { post.mentionAccts.forEach { add(it) } }
        put("editedAt", post.editedAt?.toEpochMilli())
        put("quote", post.quote?.let(::quote) ?: JsonNull)
        put("repliesCount", post.repliesCount)
        put("reblogsCount", post.reblogsCount)
        put("favouritesCount", post.favouritesCount)
        put("linkCard", post.linkCard?.let { card(it) } ?: JsonNull)
        put("poll", post.poll?.let { poll(it) } ?: JsonNull)
    }

    private fun card(card: MastodonLinkCard) = buildJsonObject {
        put("url", card.url)
        put("title", card.title)
        put("description", card.description)
        put("imageUrl", card.imageUrl)
        put("providerName", card.providerName)
        put("type", card.type)
    }

    private fun poll(poll: MastodonPoll) = buildJsonObject {
        putJsonArray("options") {
            poll.options.forEach {
                add(
                    buildJsonObject {
                        put("title", it.title)
                        put("votes", it.votes)
                    },
                )
            }
        }
        put("votesCount", poll.votesCount)
        put("expired", poll.expired)
        put("multiple", poll.multiple)
        put("votersCount", poll.votersCount)
        put("expiresAt", poll.expiresAt?.toEpochMilli())
        put("resultsAvailable", poll.resultsAvailable)
    }

    private fun profile(profile: MastodonProfile) = buildJsonObject {
        put("id", profile.id)
        put("acct", profile.acct)
        put("username", profile.username)
        put("displayName", profile.displayName)
        put("avatarUrl", profile.avatarUrl)
        put("headerUrl", profile.headerUrl)
        put("createdAt", profile.createdAt?.toEpochMilli())
        put("note", profile.note)
        put("url", profile.url)
        put("followersCount", profile.followersCount)
        put("followingCount", profile.followingCount)
        put("statusesCount", profile.statusesCount)
        put("locked", profile.locked)
        put("bot", profile.bot)
        putJsonArray("fields") {
            profile.fields.forEach { field ->
                add(
                    buildJsonObject {
                        put("name", field.name)
                        put("value", field.value)
                        put("url", field.url)
                        put("verifiedAt", field.verifiedAt?.toEpochMilli())
                    },
                )
            }
        }
    }

    private fun tags(tags: List<MastodonTag>) = buildJsonArray {
        tags.forEach {
            add(
                buildJsonObject {
                    put("name", it.name)
                    put("url", it.url)
                    put("uses", it.uses)
                },
            )
        }
    }

    private fun posts(posts: List<MastodonPost>) = JsonArray(posts.map(::post))

    @Test
    fun anAccountsStatusesReadAsXtaReadsThem() =
        assertSame(xta["account_statuses"], posts(MastodonParser.statuses(fixture("account_statuses.json"), "mastodon.social")))

    @Test
    fun aLookedUpAccountReadsAsXtaReadsIt() =
        assertSame(xta["account_lookup"], profile(MastodonParser.profile(fixture("account_lookup.json"), "mastodon.social")))

    @Test
    fun aConversationReadsAsXtaReadsIt() {
        val context = MastodonParser.context(fixture("status_context.json"), "social.example")
        assertSame(xta["status_context"], JsonObject(mapOf("ancestors" to posts(context.ancestors), "descendants" to posts(context.descendants))))
    }

    @Test
    fun aSearchReadsAsXtaReadsIt() {
        val page = MastodonParser.search(fixture("search.json"), "mastodon.social")
        val ours = JsonObject(mapOf("accounts" to JsonArray(page.accounts.map(::profile)), "posts" to posts(page.posts), "tags" to tags(page.tags)))
        assertSame(xta["search"], ours)
    }

    @Test
    fun trendingTagsReadAsXtaReadsThem() = assertSame(xta["trends_tags"], tags(MastodonParser.tags(fixture("trends_tags.json"))))

    @Test
    fun misskeyNotesReadAsXtaReadsThem() =
        assertSame(xta["misskey_notes"], posts(MastodonParser.misskeyNotes(fixture("misskey_notes.json"), "https://misskey.io")))
}
