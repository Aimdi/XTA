package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.mastodon.MastodonFailure
import com.aimdi.xtanative.networks.mastodon.MastodonMedia
import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.networks.mastodon.MastodonProfile
import com.aimdi.xtanative.networks.mastodon.MastodonText
import com.aimdi.xtanative.networks.mastodon.MastodonTextPart
import com.aimdi.xtanative.ui.plugins.LinkCard
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.TextRun

/**
 * A Mastodon post as the shared row draws it, as XTA's Ink card lays it out:
 * while its content warning is not [open], the text, media, card and quote
 * stay back; media marked sensitive waits behind XTA's notice. [id] is the
 * post's address, [authorId] its author's `user@domain`.
 */
internal fun MastodonPost.toNetworkPost(language: String, pinned: Boolean = false, open: Boolean = false): NetworkPost {
    val shown = open || !hasSpoiler
    return NetworkPost(
        id = url,
        authorId = acct,
        authorName = authorName,
        authorHandle = acct,
        avatarUrl = avatarUrl,
        createdAt = publishedAt,
        text = if (shown) runs(this) else emptyList(),
        media = if (shown) media.map { it.toMediaItem(url) } else emptyList(),
        card = linkCard?.takeIf { shown }?.let { LinkCard(it.url, it.title, it.description, it.imageUrl) },
        quoted = quote?.takeIf { shown }?.toQuoted(language),
        replies = repliesCount,
        reposts = reblogsCount,
        likes = favouritesCount,
        pinned = pinned,
        replyingTo = replyToAcct,
        warning = Strings.sensitive.get(language).takeIf { sensitive && !hasSpoiler },
        url = url,
    )
}

/**
 * A quoted post: behind its content warning (XTA's "Content warning" over
 * the author's words) all of it waits for Show; media marked sensitive
 * alone waits blurred.
 */
private fun MastodonPost.toQuoted(language: String): NetworkPost = NetworkPost(
    id = url,
    authorId = acct,
    authorName = authorName,
    authorHandle = acct,
    avatarUrl = avatarUrl,
    createdAt = publishedAt,
    text = runs(this),
    media = media.map { it.toMediaItem(url) },
    warning = when {
        hasSpoiler -> Strings.content_warning.get(language) + "\n" + spoilerText.trim()
        sensitive -> Strings.content_warning.get(language)
        else -> null
    },
    url = url,
    coversText = hasSpoiler,
)

/** A post's text cut at its links, mentions (by `user@domain`) and tags (searched with their #). */
internal fun runs(post: MastodonPost): List<TextRun> = MastodonText.parts(post).map(::run)

/** A profile's bio, its links, mentions and tags as in a post. */
internal fun runs(profile: MastodonProfile): List<TextRun> = MastodonText.parts(profile.note.trim(), emptyList(), profile.emojis).map(::run)

private fun run(part: MastodonTextPart): TextRun = when (part) {
    is MastodonTextPart.Text -> TextRun.Plain(part.text)
    is MastodonTextPart.Link -> TextRun.Link(part.text, part.url)
    is MastodonTextPart.Mention -> TextRun.Mention(part.text, part.acct)
    is MastodonTextPart.Tag -> TextRun.Tag(part.text, "#" + part.name)
    is MastodonTextPart.Emoji -> TextRun.Plain(part.text)
}

/** A picture, video or GIF: the full picture opens, its preview fills the tile, and a video opens on the web where the desktop plays nothing itself. */
internal fun MastodonMedia.toMediaItem(postUrl: String): MediaItem = MediaItem(
    imageUrl = if (isVideo) url else originalUrl ?: url,
    kind = when (kind) {
        MastodonMedia.Kind.IMAGE -> MediaItem.Kind.PHOTO
        MastodonMedia.Kind.VIDEO -> MediaItem.Kind.VIDEO
        MastodonMedia.Kind.GIFV -> MediaItem.Kind.GIF
    },
    videoUrl = videoUrl,
    aspectRatio = aspectRatio?.toFloat(),
    alt = description,
    durationMs = durationSeconds?.let { (it * 1000).toLong() },
    thumbUrl = url,
    webUrl = postUrl.takeIf { isVideo && it.isNotEmpty() },
)

/** What went wrong with a read, in XTA's words for Mastodon (mastodonErrorMessage). */
internal fun problemText(error: Throwable): PluginText = when (MastodonFailure.of(error)) {
    MastodonFailure.NOT_CONFIGURED -> Strings.not_configured
    MastodonFailure.NOT_FOUND -> Strings.error_not_found
    MastodonFailure.RATE_LIMITED -> Strings.error_rate_limited
    MastodonFailure.UNAUTHORIZED -> Strings.error_unauthorized
    MastodonFailure.BAD_RESPONSE -> Strings.error_response
    MastodonFailure.NETWORK, null -> Strings.error_network
}

/**
 * What went wrong with a page of a list, from what the shared paged list
 * keeps of it; who boosted or quoted a post is a list some servers keep to
 * signed-in readers ([activity]), which is said so, as XTA says it.
 */
internal fun problemText(problem: Problem, activity: Boolean = false): PluginText = when (problem) {
    is Problem.Refused -> when (problem.code) {
        401, 403, 422 -> if (activity) Strings.activity_unavailable else Strings.error_unauthorized
        404 -> if (activity) Strings.activity_unavailable else Strings.error_not_found
        429 -> Strings.error_rate_limited
        else -> Strings.error_response
    }
    Problem.RateLimited -> Strings.error_rate_limited
    else -> Strings.error_network
}
