package com.aimdi.xtanative.networks.substack

/** A filter on the merged feed, applied on the device: no account is needed and nothing is asked. */
enum class SubstackFeedFilter { ALL, UNREAD, SAVED, FREE, PAID, PODCAST, VIDEO }

/** A filter on a publication's page. */
enum class SubstackPublicationFilter { ALL, UNREAD, FREE, PODCASTS, VIDEOS }

/** How loaded posts are ordered: by date either way, or by reactions first. */
enum class SubstackOrder { NEWEST, OLDEST, POPULAR }

/** Filters and orders over posts already read, as XTA's feed, publication page and home controls apply them. */
object SubstackFilters {
    /** Whether [post] passes [filter]; [isSaved] answers for the saved library, which XTA Native keeps. */
    fun matches(post: SubstackPost, filter: SubstackFeedFilter, readIds: Set<String>, isSaved: (SubstackPost) -> Boolean = { false }): Boolean =
        when (filter) {
            SubstackFeedFilter.ALL -> true
            SubstackFeedFilter.UNREAD -> !SubstackPosts.isRead(readIds, post)
            SubstackFeedFilter.SAVED -> isSaved(post)
            SubstackFeedFilter.FREE -> !post.isPaywalled
            SubstackFeedFilter.PAID -> post.isPaywalled
            SubstackFeedFilter.PODCAST -> post.isPodcast
            SubstackFeedFilter.VIDEO -> post.isVideo
        }

    /** A publication page's posts through [filter], in [order]; undated posts last, ties as loaded. */
    fun publicationPosts(posts: List<SubstackPost>, filter: SubstackPublicationFilter, order: SubstackOrder, readIds: Set<String>): List<SubstackPost> =
        posts.filter { post ->
            when (filter) {
                SubstackPublicationFilter.ALL -> true
                SubstackPublicationFilter.UNREAD -> !SubstackPosts.isRead(readIds, post)
                SubstackPublicationFilter.FREE -> !post.isPaywalled
                SubstackPublicationFilter.PODCASTS -> post.isPodcast
                SubstackPublicationFilter.VIDEOS -> post.isVideo
            }
        }.sortedWith(comparator(order))

    /**
     * Loaded posts whose title, excerpt, publication and author hold every
     * word of [query], in [order]. A query only filters; it never asks Substack.
     */
    fun loaded(posts: List<SubstackPost>, query: String, order: SubstackOrder): List<SubstackPost> {
        val terms = SubstackText.terms(query)
        return posts.filter { post ->
            "${post.title} ${post.excerpt.orEmpty()} ${post.publicationName} ${post.authorName.orEmpty()}".lowercase().let { text -> terms.all { it in text } }
        }.sortedWith(comparator(order))
    }

    /** [order] over posts; the sort is stable, so equals keep the order they were loaded in. */
    fun comparator(order: SubstackOrder): Comparator<SubstackPost> = Comparator { a, b ->
        val popularity = if (order == SubstackOrder.POPULAR) (b.reactionCount ?: 0).compareTo(a.reactionCount ?: 0) else 0
        val left = a.publishedAt
        val right = b.publishedAt
        when {
            popularity != 0 -> popularity
            left == null || right == null -> if (left == right) 0 else if (left == null) 1 else -1
            order == SubstackOrder.OLDEST -> left.compareTo(right)
            else -> right.compareTo(left)
        }
    }
}
