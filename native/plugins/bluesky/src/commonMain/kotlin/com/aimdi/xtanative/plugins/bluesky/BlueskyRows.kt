package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.bluesky.BlueskyFacet
import com.aimdi.xtanative.networks.bluesky.BlueskyFacets
import com.aimdi.xtanative.networks.bluesky.BlueskyMedia
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.LinkCard
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostCard
import com.aimdi.xtanative.ui.plugins.TextRun
import com.aimdi.xtanative.ui.plugins.currentLanguage

/** A Bluesky post as the app's shared post row draws it; [pinned] marks the one its author pinned. */
internal fun BlueskyPost.toNetworkPost(language: String, pinned: Boolean = false): NetworkPost = NetworkPost(
    id = uri,
    authorId = did.ifEmpty { handle },
    authorName = authorName,
    authorHandle = handle,
    avatarUrl = avatarUrl,
    createdAt = publishedAt,
    text = runs(text, facets),
    media = media.map { it.toMediaItem(url) },
    card = linkCard?.let { LinkCard(it.url, it.title, it.description, it.imageUrl) },
    quoted = quotedPost?.toNetworkPost(language),
    replies = replyCount,
    reposts = repostCount,
    quotes = quoteCount,
    likes = likeCount,
    repostedBy = if (isRepost) repostedByName?.ifBlank { null } ?: repostedByHandle else null,
    pinned = pinned,
    replyingTo = replyToHandle?.takeIf { isReply && it.isNotBlank() && !it.equals(handle, ignoreCase = true) },
    warning = Strings.content_warning.get(language).takeIf { isSensitive || warnsContent },
    url = url,
)

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
private fun BlueskyMedia.toMediaItem(postUrl: String): MediaItem = MediaItem(
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

/** A post in a list, with the line under it. */
@Composable
internal fun PostRow(app: BlueskyApp, post: BlueskyPost, pinned: Boolean = false, indent: Int = 0, continues: Boolean = false) {
    val language = currentLanguage()
    val shown = remember(post, language, pinned) { post.toNetworkPost(language, pinned) }
    NetworkPostCard(shown, app.actions, indent = indent, continues = continues)
    if (!continues) Hairline()
}

/** An account in a list: its picture, name, handle and the start of its description; it opens the profile. */
@Composable
internal fun PersonRow(app: BlueskyApp, person: BlueskyProfile) {
    Row(
        Modifier.fillMaxWidth().clickable { app.host.open(BlueskyRoutes.profile(person.actor)) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(person.avatarUrl, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(person.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@" + person.handle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (person.description.isNotBlank()) {
                Text(
                    person.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    Hairline()
}
