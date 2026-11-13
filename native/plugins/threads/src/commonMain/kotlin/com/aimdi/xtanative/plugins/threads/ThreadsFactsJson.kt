package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsCommunities
import com.aimdi.xtanative.networks.threads.ThreadsGhost
import com.aimdi.xtanative.networks.threads.ThreadsMusic
import com.aimdi.xtanative.networks.threads.ThreadsPoll
import com.aimdi.xtanative.networks.threads.ThreadsPostFacts
import com.aimdi.xtanative.networks.threads.ThreadsReplyAudience
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsTextAttachment
import com.aimdi.xtanative.networks.threads.ThreadsTrend
import com.aimdi.xtanative.store.backup.XtaTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A post's [ThreadsPostFacts] where a post outlives its page, under `facts`
 * beside XTA's keys (which XTA does not read): only what is set, so a post
 * Meta said nothing more about keeps nothing more.
 */
internal object ThreadsFactsJson {
    fun json(facts: ThreadsPostFacts): JsonElement {
        if (facts.isEmpty) return JsonNull
        val fields = listOfNotNull(
            facts.topicEmoji?.let { "topicEmoji" to JsonPrimitive(it) },
            facts.topicIconUrl?.let { "topicIconUrl" to JsonPrimitive(it) },
            flag("community", facts.community),
            facts.communityId?.let { "communityId" to JsonPrimitive(it) },
            facts.communityUrl?.let { "communityUrl" to JsonPrimitive(it) },
            facts.shareCount?.let { "shareCount" to JsonPrimitive(it) },
            flag("aiLabel", facts.aiLabel),
            flag("pinnedToProfile", facts.pinnedToProfile),
            flag("pinnedToParent", facts.pinnedToParent),
            flag("sharingFriction", facts.sharingFriction),
            facts.poll?.let { "poll" to poll(it) },
            flag("edited", facts.edited),
            flag("likedByAuthor", facts.likedByAuthor),
            facts.replyAudience?.let { "replyAudience" to JsonPrimitive(it.name) },
            facts.music?.let { "music" to music(it) },
            facts.language?.let { "language" to JsonPrimitive(it) },
            facts.canTranslate?.let { "canTranslate" to JsonPrimitive(it) },
            facts.captionId?.let { "captionId" to JsonPrimitive(it) },
            facts.viewCount?.let { "viewCount" to JsonPrimitive(it) },
            facts.ghost?.let { ghost -> "ghost" to JsonObject(listOfNotNull(ghost.endsAt?.let { "endsAt" to JsonPrimitive(XtaTime.iso(it)) }).toMap()) },
            facts.attachment?.let { "attachment" to attachment(it) },
            facts.trends.takeIf { it.isNotEmpty() }?.let { trends -> "trends" to JsonArray(trends.map(::trend)) },
        )
        return JsonObject(fields.toMap())
    }

    fun facts(row: J): ThreadsPostFacts {
        if (row.raw !is JsonObject) return ThreadsPostFacts.NONE
        return ThreadsPostFacts(
            topicEmoji = row["topicEmoji"].string,
            topicIconUrl = row["topicIconUrl"].string,
            community = row["community"].boolean == true,
            communityId = row["communityId"].string,
            communityUrl = row["communityUrl"].string?.let(ThreadsCommunities::threadsPage),
            shareCount = row["shareCount"].long,
            aiLabel = row["aiLabel"].boolean == true,
            pinnedToProfile = row["pinnedToProfile"].boolean == true,
            pinnedToParent = row["pinnedToParent"].boolean == true,
            sharingFriction = row["sharingFriction"].boolean == true,
            poll = poll(row["poll"]),
            edited = row["edited"].boolean == true,
            likedByAuthor = row["likedByAuthor"].boolean == true,
            replyAudience = ThreadsReplyAudience.entries.firstOrNull { it.name == row["replyAudience"].string },
            music = music(row["music"]),
            language = row["language"].string,
            canTranslate = row["canTranslate"].boolean,
            captionId = row["captionId"].string,
            viewCount = row["viewCount"].long?.takeIf { it >= 0 },
            ghost = row["ghost"].takeIf { it.raw is JsonObject }?.let { ThreadsGhost(XtaTime.parse(it["endsAt"].string)) },
            attachment = attachment(row["attachment"]),
            trends = row["trends"].list.mapNotNull(::trend),
        )
    }

    private fun attachment(attachment: ThreadsTextAttachment): JsonObject = JsonObject(
        listOfNotNull(
            "text" to JsonPrimitive(attachment.text),
            attachment.title?.let { "title" to JsonPrimitive(it) },
            attachment.linkUrl?.let { "linkUrl" to JsonPrimitive(it) },
        ).toMap(),
    )

    private fun attachment(row: J): ThreadsTextAttachment? = row["text"].string?.takeIf { it.isNotBlank() }?.let {
        ThreadsTextAttachment(it, row["title"].string, row["linkUrl"].string?.takeIf { url -> url.startsWith("https://") })
    }

    private fun trend(trend: ThreadsTrend): JsonObject = JsonObject(
        listOfNotNull(
            "title" to JsonPrimitive(trend.title),
            "query" to JsonPrimitive(trend.terms.query),
            flag("tag", trend.terms.tag),
            trend.context?.let { "context" to JsonPrimitive(it) },
            trend.summary?.let { "summary" to JsonPrimitive(it) },
        ).toMap(),
    )

    private fun trend(row: J): ThreadsTrend? {
        val title = row["title"].string?.ifBlank { null } ?: return null
        val query = row["query"].string?.ifBlank { null } ?: return null
        return ThreadsTrend(title, ThreadsSearchTerms(query, tag = row["tag"].boolean == true), row["context"].string, row["summary"].string)
    }

    private fun flag(name: String, on: Boolean): Pair<String, JsonElement>? = (name to JsonPrimitive(true)).takeIf { on }

    private fun poll(poll: ThreadsPoll): JsonObject = JsonObject(
        listOfNotNull(
            "options" to JsonArray(poll.options.map { JsonObject(mapOf("text" to JsonPrimitive(it.text), "votes" to JsonPrimitive(it.votes))) }),
            poll.endsAt?.let { "endsAt" to JsonPrimitive(XtaTime.iso(it)) },
        ).toMap(),
    )

    private fun music(music: ThreadsMusic): JsonObject = JsonObject(
        listOfNotNull(
            "title" to JsonPrimitive(music.title),
            music.artist?.let { "artist" to JsonPrimitive(it) },
            music.imageUrl?.let { "imageUrl" to JsonPrimitive(it) },
        ).toMap(),
    )

    private fun music(row: J): ThreadsMusic? = row["title"].string?.let {
        ThreadsMusic(it, row["artist"].string, row["imageUrl"].string?.takeIf { url -> url.startsWith("https://") })
    }

    private fun poll(row: J): ThreadsPoll? {
        val options = row["options"].list.mapNotNull { option ->
            val text = option["text"].string ?: return@mapNotNull null
            option["votes"].long?.let { ThreadsPoll.Option(text, it) }
        }
        return if (options.size < 2) null else ThreadsPoll(options, XtaTime.parse(row["endsAt"].string))
    }
}
