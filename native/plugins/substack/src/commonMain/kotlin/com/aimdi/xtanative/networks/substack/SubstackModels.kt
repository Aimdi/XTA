package com.aimdi.xtanative.networks.substack

import java.time.Instant

/**
 * A Substack publication, followed or found. [subdomain] names it and, lower
 * cased, is its [id]; [baseUrl] is the host it is read from, a custom domain
 * included.
 */
data class SubstackPublication(
    val subdomain: String,
    val baseUrl: String,
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null,
) {
    /** What a follow is kept under. */
    val id: String get() = subdomain.lowercase()

    /** The name to show. A custom-domain follow can land on `www`, which is never shown as a title. */
    val displayName: String
        get() {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty() && trimmed.lowercase() != "www") return trimmed
            val sub = subdomain.trim()
            if (sub.isNotEmpty() && sub.lowercase() != "www") return sub
            val fromHost = SubstackHosts.subdomainOf(SubstackHosts.hostOf(baseUrl))
            if (fromHost.isNotEmpty() && fromHost != "www") return fromHost
            return trimmed.ifEmpty { sub }
        }
}

/**
 * A post as a listing, a whole post, an RSS item or a Beehiiv page gives it.
 * [bodyHtml] is only there when the post was read whole (or came from RSS);
 * a paid post may carry only its free opening, or nothing.
 */
data class SubstackPost(
    /** The API's number for the post; a copy from RSS or Beehiiv has its slug instead. */
    val id: String,
    val title: String,
    val slug: String,
    val publicationBaseUrl: String,
    val publicationName: String,
    val subtitle: String? = null,
    val description: String? = null,
    /** When it was published, ISO-8601 as Substack sends it. */
    val postDate: String? = null,
    val canonicalUrl: String? = null,
    val coverImage: String? = null,
    val bodyHtml: String? = null,
    /** Who may read it: `everyone`, `only_paid`, `founding`, … */
    val audience: String? = null,
    val authorName: String? = null,
    /** What kind of post it is: `newsletter`, `podcast`, `video`, … */
    val type: String? = null,
    /** Set when the post is built around a video Substack hosts itself. */
    val hasVideoUpload: Boolean = false,
    /** The episode file of a podcast post, playable directly. */
    val audioUrl: String? = null,
    /** What the post has gathered; null when not carried, which is not zero. */
    val reactionCount: Long? = null,
    val commentCount: Long? = null,
    /** The words in the whole post, as the API counts them. */
    val wordCount: Long? = null,
) {
    /** Substack marks gated posts `only_paid`, and now and then a founding tier. */
    val isPaywalled: Boolean get() = audience?.lowercase() in PAID

    /** The subtitle, else the description; Substack's `...` placeholder is none. */
    val excerpt: String? get() = listOf(subtitle, description).firstNotNullOfOrNull { text -> text?.trim()?.takeIf { it.isNotEmpty() && it != "..." } }

    val publishedAt: Instant? by lazy { SubstackText.instant(postDate) }

    val isPodcast: Boolean get() = !audioUrl.isNullOrEmpty()

    /** Whether the post leads with a video, which a card marks and the reader plays. */
    val isVideo: Boolean get() = hasVideoUpload || type?.lowercase() == "video"

    /** Whether this copy came from the API, which names posts by number; RSS has only the slug. */
    val hasApiId: Boolean get() = id.isNotEmpty() && id.all { it in '0'..'9' }

    /** The publication as far as this post tells it. Its id is only guessed from the host. */
    val publication: SubstackPublication
        get() = SubstackPublication(SubstackHosts.subdomainOf(SubstackHosts.hostOf(publicationBaseUrl)), publicationBaseUrl, publicationName)

    /**
     * One key for this post whichever copy it is: the API names it by number and
     * RSS by slug, but both carry the same canonical URL. Read state, likes and
     * saves are kept under it.
     */
    val key: String by lazy { SubstackPosts.keyOf(this) }

    private companion object {
        val PAID = setOf("only_paid", "only_paying", "founding", "only_founding")
    }
}

/** One public Note from Substack's reader feed: the open discovery mix, not a personal timeline. */
data class SubstackNote(
    val id: String,
    val body: String,
    val authorName: String? = null,
    val authorHandle: String? = null,
    val authorPhotoUrl: String? = null,
    val at: Instant? = null,
    val reactionCount: Long? = null,
    val imageUrl: String? = null,
    /** The note on substack.com, when its author's handle is known. */
    val url: String? = null,
    val publication: SubstackPublication? = null,
)

/** A leaderboard category from `/api/v1/categories`. */
data class SubstackCategory(val id: Long, val name: String, val slug: String)

/** A publication an author recommends, or one a search found alike. */
data class SubstackRecommendation(val publication: SubstackPublication, val blurb: String? = null)

/** One comment under a post, in reading order, [depth] levels below the top. */
data class SubstackComment(
    val id: String,
    val body: String,
    val author: String? = null,
    val parentId: String? = null,
    val at: Instant? = null,
    val depth: Int = 0,
)

/** What an RSS feed says of its publication, and its posts. */
data class SubstackRssChannel(
    val title: String? = null,
    val description: String? = null,
    val link: String? = null,
    val imageUrl: String? = null,
    val posts: List<SubstackPost> = emptyList(),
    /** Whether it is Substack's feed, not Ghost's, Beehiiv's or a blog's. */
    val looksLikeSubstack: Boolean = false,
)

/** A post's address: its publication's origin and its slug. */
data class SubstackPostLink(val publicationBase: String, val slug: String)
