package com.aimdi.xtanative.ui.plugins

import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A piece of a post's text as the shared text draws it, whatever network it comes from. */
sealed interface TextRun {
    val text: String

    data class Plain(override val text: String) : TextRun

    /** Opens [url]: in a plugin that opens such links, else in the browser. */
    data class Link(override val text: String, val url: String) : TextRun

    /** Names an account: [who] is what its network finds it by (a handle, a DID). */
    data class Mention(override val text: String, val who: String) : TextRun

    /** A hashtag or cashtag: [query] is what a search for it asks ("#kotlin", "$TSLA"). */
    data class Tag(override val text: String, val query: String) : TextRun
}

/** A link preview under a post: the page's picture, title and description, opening [url]. */
data class LinkCard(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
) {
    /** The site, as the card names it: "example.com". */
    val domain: String? get() = url.toHttpUrlOrNull()?.host?.removePrefix("www.")
}

/**
 * A post from any network as the shared row and the shared opened post draw
 * it, and as a plugin hands its posts to the app's timelines ([PluginFeed]).
 * [id] tells rows apart in a list and is what the plugin opens it by;
 * [authorId] is what it opens the author by (a DID, a user id), and
 * [authorHandle] is shown as "@handle".
 */
data class NetworkPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorHandle: String,
    val avatarUrl: String? = null,
    /** When it was written: where it sits in a timeline mixing networks, which leaves out a post without one. */
    val createdAt: Instant? = null,
    val text: List<TextRun> = emptyList(),
    val media: List<MediaItem> = emptyList(),
    val card: LinkCard? = null,
    val quoted: NetworkPost? = null,
    val replies: Long = 0,
    val reposts: Long = 0,
    val quotes: Long = 0,
    val likes: Long = 0,
    /** Views, where the network counts them. */
    val views: Long? = null,
    /** Who reposted it into the list it is shown in. */
    val repostedBy: String? = null,
    /** Pinned by its author, at the top of their profile. */
    val pinned: Boolean = false,
    /** The handle of who it answers, for "Replying to @…". */
    val replyingTo: String? = null,
    /** Why its media is hidden until the reader opens it: a content warning, in the reader's language. */
    val warning: String? = null,
    /** Where it lives on the web. */
    val url: String? = null,
    /** The [warning] keeps its text, link card and quoted post back too, not only its media. */
    val coversText: Boolean = false,
    /** Its title above its text, where the network gives posts one: a Reddit post, a story, an article. */
    val title: String? = null,
)

/** Where a network post's parts lead: its plugin decides. */
class NetworkPostActions(
    val open: (NetworkPost) -> Unit,
    val openAuthor: (NetworkPost) -> Unit,
    /** A mention, with whom it names ([TextRun.Mention.who]); "Replying to" gives the handle. */
    val openMention: (String) -> Unit,
    /** A tag, with its query ([TextRun.Tag.query]). */
    val openTag: (String) -> Unit,
    val openMedia: (NetworkPost, Int) -> Unit,
    /**
     * How the app's archive keeps a post ([PluginPostArchive]): given, the
     * shared row ends its counts with the bookmark that saves it on this
     * device (a long press picks its folder); null for a post it does not keep.
     */
    val archive: ((NetworkPost) -> PluginPostArchive?)? = null,
    /** What a long press on the shared row opens: the post's menu, [PluginHost.openPostMenu]. */
    val more: ((NetworkPost) -> Unit)? = null,
)
