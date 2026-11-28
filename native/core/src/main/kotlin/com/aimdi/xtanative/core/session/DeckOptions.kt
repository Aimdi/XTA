package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XThread
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One deck column's own choices, kept beside the deck's plain list of ids
 * ([Session.deckOptions]) so the list still reads as XTA wrote it: its own
 * width ([width], a DeckWidth's stored name; null takes the deck's), reposts
 * and replies left out, its media alone, and its unread count by its name.
 */
data class DeckColumnOptions(
    val width: String? = null,
    val hideReposts: Boolean = false,
    val hideReplies: Boolean = false,
    val mediaOnly: Boolean = false,
    val unreadCount: Boolean = false,
) {
    val isDefault: Boolean get() = this == DeckColumnOptions()

    /** Whether the column hides any thread at all, so a feed with none of these choices is shown untouched. */
    val filters: Boolean get() = hideReposts || hideReplies

    /**
     * Whether [thread] stays in the column: a repost goes with [hideReposts];
     * with [hideReplies], an answer to someone else (a thread its author
     * continues stays, as the profile filter keeps it).
     */
    fun keeps(thread: XThread): Boolean {
        val first = thread.posts.firstOrNull() ?: return true
        if (hideReposts && first.isRepost) return false
        val shown = first.shown
        val answersOther = shown.replyToPostId != null && !shown.replyToScreenName.equals(shown.author.screenName, ignoreCase = true)
        return !(hideReplies && answersOther)
    }

    companion object {
        internal fun json(options: DeckColumnOptions): JsonObject = buildJsonObject {
            options.width?.let { put("width", it) }
            if (options.hideReposts) put("hideReposts", true)
            if (options.hideReplies) put("hideReplies", true)
            if (options.mediaOnly) put("mediaOnly", true)
            if (options.unreadCount) put("unreadCount", true)
        }

        internal fun of(row: J): DeckColumnOptions? = DeckColumnOptions(
            width = row["width"].string?.takeIf { it.isNotBlank() },
            hideReposts = row["hideReposts"].boolean ?: false,
            hideReplies = row["hideReplies"].boolean ?: false,
            mediaOnly = row["mediaOnly"].boolean ?: false,
            unreadCount = row["unreadCount"].boolean ?: false,
        ).takeIf { !it.isDefault }
    }
}
