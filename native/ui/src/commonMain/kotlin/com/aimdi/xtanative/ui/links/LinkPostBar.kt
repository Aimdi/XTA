package com.aimdi.xtanative.ui.links

import com.aimdi.xtanative.services.links.LinkPostContext
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings

/** One of the bar's counts; [shown] is its figure, empty when there is none to show. */
data class BarCount(val kind: Kind, val shown: String) {
    enum class Kind { LIKES, REPLIES, REPOSTS }
}

/** The figures on the bar under XTA's browser screen (link_post_context_bar.dart). Read only: nothing here acts on a network. */
object LinkPostBar {
    /**
     * Likes, replies and reposts, in the bar's order. A count the network did
     * not report has no figure, nor does any count while [hidden]; the icon
     * still shows, so the bar keeps its shape without inventing a zero.
     */
    fun counts(post: LinkPostContext, hidden: Boolean, format: (Long) -> String): List<BarCount> = listOf(
        BarCount.Kind.LIKES to post.likes,
        BarCount.Kind.REPLIES to post.replies,
        BarCount.Kind.REPOSTS to post.reposts,
    ).map { (kind, count) -> BarCount(kind, if (hidden || count == null) "" else format(count)) }

    /** Zen and calm modes hide counts on every card; the bar follows them. */
    fun countsHidden(settings: Settings?): Boolean = settings != null && (settings[PostsPrefs.zenMode] || settings[PostsPrefs.calmMode])

    /** The letter the bar's stand-in picture shows when the author has none. */
    fun monogram(post: LinkPostContext): String {
        val name = post.author.trim().ifEmpty { post.network.trim() }
        if (name.isEmpty()) return ""
        return String(Character.toChars(name.codePointAt(0))).uppercase()
    }
}
