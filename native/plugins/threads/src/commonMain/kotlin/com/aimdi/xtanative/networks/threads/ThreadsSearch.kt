package com.aimdi.xtanative.networks.threads

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A search the results page runs: [query] is the words or the tag (without its `#`); [profile] a handle the words also spell. */
data class ThreadsSearchTerms(val query: String, val tag: Boolean, val profile: String? = null) {
    /** The terms as the search field shows them: a tag with its `#`. */
    val text: String get() = if (tag) "#$query" else query
}

/** What a query typed into Threads search names, as XTA's search sheet and results page read it. */
sealed interface ThreadsQuery {
    /** What the search history keeps of it; a post link is not kept. */
    val remembered: String?

    /** The search it runs; null when it opens a page of its own instead. */
    val terms: ThreadsSearchTerms?

    /** A pasted post link: that post opens. */
    data class Post(val link: ThreadsLink.Post) : ThreadsQuery {
        override val remembered: String? get() = null
        override val terms: ThreadsSearchTerms? get() = null
    }

    /** A #tag: the posts filed under it. */
    data class Tag(val tag: String) : ThreadsQuery {
        override val remembered: String get() = "#$tag"
        override val terms: ThreadsSearchTerms get() = ThreadsSearchTerms(tag, tag = true)
    }

    /** An @handle or a profile link: that profile. */
    data class Profile(val handle: String) : ThreadsQuery {
        override val remembered: String get() = handle
        override val terms: ThreadsSearchTerms? get() = null
    }

    /** Words: the posts that say them, with [profile] the handle they also spell. */
    data class Words(val words: String, val profile: String? = null) : ThreadsQuery {
        override val remembered: String get() = words
        override val terms: ThreadsSearchTerms get() = ThreadsSearchTerms(words, tag = false, profile = profile)
    }

    companion object {
        /** What [text] names, or null when it is blank. */
        fun of(text: String): ThreadsQuery? {
            val query = text.trim()
            if (query.isEmpty()) return null
            (ThreadsLinks.parse(query) as? ThreadsLink.Post)?.let { return Post(it) }
            val tag = if (query.startsWith("#")) query.substring(1).trim() else ""
            if (tag.isNotEmpty()) return Tag(tag)
            val handle = ThreadsLinks.normaliseHandle(query)
            val named = query.startsWith("@") || query.toHttpUrlOrNull()?.host?.contains("threads.") == true
            return if (named && handle != null) Profile(handle) else Words(query, handle)
        }
    }
}
