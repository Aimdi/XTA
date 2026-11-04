package com.aimdi.xtanative.networks.mastodon

import java.time.Instant

/**
 * One public status as a card shows it, as XTA's MastodonPost holds it. A
 * boost is unwrapped to the post it shares: [id], [url] and [publishedAt] are
 * the original's, while [timelineId] and [timelineAt] are the boost's own,
 * which is where a timeline pages from and how it is dated.
 */
data class MastodonPost(
    val id: String,
    /** `user@domain`, the author's address. */
    val acct: String,
    val authorName: String,
    /** The body as plain text; [MastodonText.parts] finds its links, mentions and tags. */
    val text: String,
    val url: String,
    val timelineId: String? = null,
    val timelineAt: Instant? = null,
    val avatarUrl: String? = null,
    /** The custom emoji in the author's name. */
    val authorEmojis: List<MastodonEmoji> = emptyList(),
    /** The content warning: while it is not blank, the post shows only it until the reader asks for the rest. */
    val spoilerText: String = "",
    /** Media that waits for a tap: behind the warning when there is one, else behind a notice of its own. */
    val sensitive: Boolean = false,
    /** Pictures, videos and GIFs, as XTA shows them. */
    val media: List<MastodonMedia> = emptyList(),
    /** Audio attachments, which XTA itself leaves out. */
    val audio: List<MastodonAudio> = emptyList(),
    val publishedAt: Instant? = null,
    val boosted: Boolean = false,
    /** Who boosted it, by display name, when [boosted]. */
    val boostedBy: String? = null,
    val boostedByAcct: String? = null,
    /** The first account the reply mentions, as XTA names the account a reply answers. */
    val replyToAcct: String? = null,
    val replyToId: String? = null,
    /** The `acct` of every mention, so a bare `@name` in [text] resolves to its instance. */
    val mentionAccts: List<String> = emptyList(),
    /** The custom emoji in [text] and [spoilerText]. */
    val emojis: List<MastodonEmoji> = emptyList(),
    val editedAt: Instant? = null,
    /** The status this one quotes: one level, a quote carries no quote of its own. */
    val quote: MastodonPost? = null,
    val repliesCount: Long = 0,
    val reblogsCount: Long = 0,
    val favouritesCount: Long = 0,
    val linkCard: MastodonLinkCard? = null,
    val poll: MastodonPoll? = null,
) {
    /** Whether it answers another status, the author's own included, which [replyToAcct] alone cannot say. */
    val isReply: Boolean get() = !replyToId.isNullOrEmpty() || replyToAcct != null

    val hasMedia: Boolean get() = media.isNotEmpty()

    val hasSpoiler: Boolean get() = spoilerText.isNotBlank()

    val edited: Boolean get() = editedAt != null

    /** The id a timeline goes on below: the boost's own for a boost. */
    val pagingId: String get() = timelineId ?: id

    /** When it reached the timeline: a boost is dated by the boost. */
    val timelineDate: Instant? get() = timelineAt ?: publishedAt
}

/**
 * A picture, video or GIF attached to a status. [url] is what a card draws
 * (the picture, or a video's poster) and [originalUrl] the file itself: the
 * full picture, or the MP4 a video or `gifv` plays.
 */
data class MastodonMedia(
    val kind: Kind,
    val url: String,
    val originalUrl: String? = null,
    /** The alt text. */
    val description: String? = null,
    /** Width over height, from `meta.original`. */
    val aspectRatio: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Double? = null,
    val blurhash: String? = null,
    /** Where a crop should centre, -1 to 1 on each axis (`meta.focus`). */
    val focus: Focus? = null,
) {
    /** A `gifv` is a silent looping clip, played the way X plays a GIF. */
    enum class Kind { IMAGE, VIDEO, GIFV }

    data class Focus(val x: Double, val y: Double)

    val isVideo: Boolean get() = kind != Kind.IMAGE

    val isGif: Boolean get() = kind == Kind.GIFV

    /** The MP4 a player opens: a video's original file. */
    val videoUrl: String? get() = if (isVideo) originalUrl else null

    val isPlayable: Boolean get() = !videoUrl.isNullOrEmpty()
}

/** An audio attachment: [url] is the file, [previewUrl] its cover when it has one. */
data class MastodonAudio(
    val url: String,
    val previewUrl: String? = null,
    val description: String? = null,
    val durationSeconds: Double? = null,
)

/** A custom emoji a status or a name writes as `:shortcode:`. */
data class MastodonEmoji(val shortcode: String, val url: String, val staticUrl: String? = null)

/** A link preview (`card`), kept only when it has a title, a description or a picture. */
data class MastodonLinkCard(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val providerName: String? = null,
    /** `link`, `photo`, `video` or `rich`. */
    val type: String? = null,
) {
    val hasImage: Boolean get() = !imageUrl.isNullOrEmpty()
}

/** A poll as counted when it was read. Read only: XTA Native never votes. */
data class MastodonPoll(
    val options: List<Option>,
    val votesCount: Long = 0,
    val expired: Boolean = false,
    val multiple: Boolean = false,
    val votersCount: Long? = null,
    val expiresAt: Instant? = null,
    /** False when the server holds the counts back, so no option is shown as having no votes. */
    val resultsAvailable: Boolean = true,
) {
    data class Option(val title: String, val votes: Long = 0)

    /**
     * The share [option] has, 0 to 1. A poll with several choices counts its
     * voters, since one person can pick more than one; null when the server
     * gave no such total, or keeps the results back.
     */
    fun fraction(option: Option): Double? {
        if (!resultsAvailable) return null
        val total = (if (multiple) votersCount else votesCount) ?: return null
        if (total <= 0) return 0.0
        return (option.votes.toDouble() / total).coerceIn(0.0, 1.0)
    }

    fun closed(now: Instant): Boolean = expired || expiresAt?.isBefore(now) == true
}

/**
 * A Fediverse account as the instance that answered describes it. [id] is
 * that instance's own number for it and means nothing on another one; [acct]
 * is `user@domain` whenever the answering instance is known.
 */
data class MastodonProfile(
    val id: String,
    val acct: String,
    val username: String,
    val displayName: String,
    /** The bio as plain text. */
    val note: String,
    val url: String,
    val avatarUrl: String? = null,
    val headerUrl: String? = null,
    val createdAt: Instant? = null,
    val followersCount: Long = 0,
    val followingCount: Long = 0,
    val statusesCount: Long = 0,
    val locked: Boolean = false,
    val bot: Boolean = false,
    val fields: List<MastodonField> = emptyList(),
    /** The custom emoji in the name, the bio and the fields. */
    val emojis: List<MastodonEmoji> = emptyList(),
) {
    /** The profile's page, when it is a plain web address that is safe to open. */
    val webUrl: String? get() = MastodonUrls.web(url)
}

/** A row of a profile's metadata; [url] is its link when the value is exactly one safe link. */
data class MastodonField(val name: String, val value: String, val url: String? = null, val verifiedAt: Instant? = null)

/** A hashtag from trends or search; [uses] adds up the days of its history. */
data class MastodonTag(val name: String, val url: String? = null, val uses: Long = 0)

/** What one guest search (`/api/v2/search`) found. */
data class MastodonSearchPage(
    val accounts: List<MastodonProfile> = emptyList(),
    val posts: List<MastodonPost> = emptyList(),
    val tags: List<MastodonTag> = emptyList(),
) {
    val isEmpty: Boolean get() = accounts.isEmpty() && posts.isEmpty() && tags.isEmpty()
}

/** The public conversation around a status (`/context`), as one instance knows it. */
data class MastodonContext(val ancestors: List<MastodonPost> = emptyList(), val descendants: List<MastodonPost> = emptyList())

/** A status with the public conversation around it; [homeDomain] is the instance that served it. */
data class MastodonThread(
    val status: MastodonPost,
    val ancestors: List<MastodonPost> = emptyList(),
    val descendants: List<MastodonPost> = emptyList(),
    val homeDomain: String? = null,
)

/** What a profile opens on, all from one [instance]: ids are only good there, so its pages are read there too. */
data class MastodonProfilePage(
    val profile: MastodonProfile,
    val instance: String,
    /** Pinned posts first, then the timeline without repeating them. */
    val posts: List<MastodonPost>,
    val pinnedIds: Set<String>,
    /** Where the timeline goes on: pins never move it. */
    val next: String?,
)

/** Trending tags and posts, the way an Explore tab shows them. */
data class MastodonExplorePage(val tags: List<MastodonTag> = emptyList(), val posts: List<MastodonPost> = emptyList())

/** [value] as [instance] answered it: what pages after it must be read there too, ids being local to it. */
data class FromInstance<T>(val instance: String, val value: T)

/** A profile's three timelines, each asked of the instance in its own way. */
enum class MastodonProfileTab { POSTS, REPLIES, MEDIA }
