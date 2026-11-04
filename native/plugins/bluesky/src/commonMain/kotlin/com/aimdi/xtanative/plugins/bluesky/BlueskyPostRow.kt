package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.bluesky.BlueskyFacet
import com.aimdi.xtanative.networks.bluesky.BlueskyFacets
import com.aimdi.xtanative.networks.bluesky.BlueskyMedia
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.LinkCard
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkBookmark
import com.aimdi.xtanative.ui.plugins.NetworkCount
import com.aimdi.xtanative.ui.plugins.NetworkHeaderButton
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.NetworkPostCard
import com.aimdi.xtanative.ui.plugins.NetworkPostExtras
import com.aimdi.xtanative.ui.plugins.TextRun
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.search.DiscoverIcons

/**
 * A Bluesky post as the app's shared row draws it; [pinned] marks the one its
 * author pinned. As XTA shows one (bluesky_post_card.dart, bluesky_content_warning.dart):
 * a post its author keeps from readers without an account, or moderation
 * hides, says only that; one labelled `!warn` keeps all of it behind the
 * content warning; one with a sensitive label, its media.
 */
internal fun BlueskyPost.toNetworkPost(language: String, pinned: Boolean = false): NetworkPost {
    val hidden = isUnavailable
    return NetworkPost(
        id = uri,
        authorId = did.ifEmpty { handle },
        authorName = authorName,
        authorHandle = handle,
        avatarUrl = avatarUrl,
        createdAt = publishedAt,
        text = if (hidden) listOf(TextRun.Plain(Strings.content_unavailable.get(language))) else runs(text, facets),
        media = if (hidden) emptyList() else media.map { it.toMediaItem(url) },
        card = linkCard?.takeUnless { hidden }?.let { LinkCard(it.url, it.title, it.description, it.imageUrl) },
        quoted = quotedPost?.takeUnless { hidden }?.toNetworkPost(language),
        replies = replyCount,
        reposts = repostCount,
        quotes = quoteCount,
        likes = likeCount,
        repostedBy = if (isRepost) repostedByName?.ifBlank { null } ?: repostedByHandle else null,
        pinned = pinned,
        replyingTo = replyToHandle?.takeIf { isReply && it.isNotBlank() && !it.equals(handle, ignoreCase = true) },
        warning = Strings.content_warning.get(language).takeIf { !hidden && (isSensitive || warnsContent) },
        url = url,
        coversText = warnsContent && !hidden,
    )
}

/** A post's text cut at its facets: links, mentions (by DID) and tags (searched with their #). */
internal fun runs(text: String, facets: List<BlueskyFacet>): List<TextRun> = BlueskyFacets.runs(text, facets).map { run ->
    val facet = run.facet ?: return@map TextRun.Plain(run.text)
    when (facet.kind) {
        BlueskyFacet.Kind.LINK -> TextRun.Link(run.text, facet.value)
        BlueskyFacet.Kind.MENTION -> TextRun.Mention(run.text, facet.value)
        BlueskyFacet.Kind.TAG -> TextRun.Tag(run.text, "#" + facet.value)
    }
}

/** A profile's description with its bare links made links, as bsky.app shows it. */
internal fun BlueskyProfile.descriptionRuns(): List<TextRun> = runs(description, BlueskyFacets.profileLinks(description))

/** A picture or video; a video opens on the web where the desktop plays nothing itself. */
internal fun BlueskyMedia.toMediaItem(postUrl: String): MediaItem = MediaItem(
    imageUrl = url,
    kind = when {
        !isVideo -> MediaItem.Kind.PHOTO
        isGif -> MediaItem.Kind.GIF
        else -> MediaItem.Kind.VIDEO
    },
    videoUrl = videoUrl,
    aspectRatio = aspectRatio?.toFloat(),
    alt = alt,
    webUrl = postUrl.takeIf { isVideo && it.isNotEmpty() },
)

/**
 * A post in a list, as XTA's Bluesky card: Follow at the end of its author
 * line while its author is not followed here, and under it the thread, who
 * reposted it (a long press: its quotes), its quotes, the like kept on this
 * device, its bookmark in Saved, Open in browser and the app's post sheet
 * (a long press on the post opens that too). [actions] is where its parts
 * lead; the line under it closes it off unless it [continues], or the list
 * draws its own ([divider] off).
 */
@Composable
internal fun PostRow(
    app: BlueskyApp,
    post: BlueskyPost,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    indent: Int = 0,
    continues: Boolean = false,
    actions: NetworkPostActions = app.actions,
    divider: Boolean = true,
) {
    val language = currentLanguage()
    val shown = remember(post, language, pinned) { post.toNetworkPost(language, pinned) }
    val extras = remember(app, post, shown, actions) { extrasOf(app, post) { actions.open(shown) } }
    NetworkPostCard(shown, actions, extras, modifier = modifier, indent = indent, continues = continues)
    if (!continues && divider) Hairline()
}

/**
 * What XTA's Bluesky card adds to the shared row, here and where the app
 * draws [post] among others' (Home's timelines, the groups): Follow at the
 * end of its author line while its author is not followed here, its footer
 * ([onOpen] opens its thread), and the app's post sheet on a long press.
 */
internal fun extrasOf(app: BlueskyApp, post: BlueskyPost, onOpen: () -> Unit): NetworkPostExtras = NetworkPostExtras(
    trailing = { FollowGlyph(app, post) },
    footer = { PostFooter(app, post, onOpen) { app.openMenu(post) } },
    onLongPress = { app.openMenu(post) },
)

/** Follow, while [post]'s author is not followed here. */
@Composable
private fun FollowGlyph(app: BlueskyApp, post: BlueskyPost) {
    val follows by app.follows.state.collectAsState()
    val followed = follows.orEmpty().any { it.isOf(post.did) || it.isOf(post.handle) }
    if (follows != null && !followed) NetworkHeaderButton(DiscoverIcons.PersonAdd, pluginString(Strings.follow)) { app.toggleFollow(post.author()) }
}

/** The author of [this] as a profile to follow from it. */
internal fun BlueskyPost.author(): BlueskyProfile = BlueskyProfile(did = did, handle = handle, displayName = authorName, avatarUrl = avatarUrl)

/**
 * Replies and reposts come from the AppView; the like is this device's own,
 * added to Bluesky's count while it is there; the bookmark files the post in
 * the app's Saved. In zen or calm mode the glyphs stay, without numbers.
 */
@Composable
private fun PostFooter(app: BlueskyApp, post: BlueskyPost, onOpen: () -> Unit, onMore: () -> Unit) {
    val likes by app.likes.state.collectAsState()
    val hidden by app.prefs.hideCounts.collectAsState()
    val liked = likes.orEmpty().any { it.uri == post.uri }
    val archive = remember(post) { BlueskyArchive.of(post) }
    val outside = rememberOpenOutside()
    val activity = { quotes: Boolean -> app.host.open(BlueskyRoutes.activity(post.uri, post.url, quotes)) }
    val count = { n: Long -> if (hidden) 0L else n }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkCount(XIcons.Reply, count(post.replyCount), pluginString(Strings.thread)) { onOpen() }
        NetworkCount(XIcons.Repost, count(post.repostCount), pluginString(Strings.reposted_by), onLongClick = { activity(true) }) { activity(false) }
        NetworkCount(PostIcons.Quote, count(post.quoteCount), pluginString(Strings.quotes)) { activity(true) }
        NetworkCount(
            if (liked) Icons.Filled.Favorite else XIcons.Like,
            count(post.likeCount + if (liked) 1 else 0),
            pluginString(if (liked) Strings.unlike_on_this_device else Strings.like_on_this_device),
            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        ) { app.toggleLike(post) }
        NetworkBookmark(archive, Modifier.size(32.dp))
        NetworkCount(PostIcons.OpenInNew, 0, pluginString(Strings.open_in_browser)) { outside(post.url) }
        NetworkCount(PostIcons.MoreHoriz, 0, pluginString(Strings.more_options), onClick = onMore)
    }
}
