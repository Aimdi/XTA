package com.aimdi.xtanative.core.x.search

import java.time.LocalDate

/**
 * The filters of X's advanced search and the query they write, as XTA's
 * advanced_search_model.dart holds them. Each filter writes one operator in a
 * fixed place, so the same filters always send the same text; [fromQuery]
 * reads a typed query back into the filters it can show.
 */
data class XAdvancedSearch(
    val allWords: String = "",
    val exactPhrase: String = "",
    val anyWords: String = "",
    val noneWords: String = "",
    val hashtags: String = "",
    /** The code `lang:` takes, or empty for every language. */
    val language: String = "",
    val fromAccounts: String = "",
    val toAccounts: String = "",
    val mentioningAccounts: String = "",
    val minReplies: String = "",
    val minLikes: String = "",
    val minRetweets: String = "",
    val since: LocalDate? = null,
    val until: LocalDate? = null,
    val content: Content = Content.ALL,
    val links: Inclusion = Inclusion.INCLUDE,
    val replies: Inclusion = Inclusion.INCLUDE,
    val includeReposts: Boolean = false,
    val excludeRetweets: Boolean = false,
) {
    /** The filters, in the order the screen lists them and the chips show them. */
    enum class Filter {
        ALL_WORDS, EXACT_PHRASE, ANY_WORDS, NONE_WORDS, HASHTAGS, LANGUAGE, FROM_ACCOUNTS, TO_ACCOUNTS, MENTIONING_ACCOUNTS,
        MIN_REPLIES, MIN_LIKES, MIN_RETWEETS, ONLY_MEDIA, PHOTOS, VIDEOS, LINKS, REPLIES, INCLUDE_REPOSTS, EXCLUDE_RETWEETS, SINCE, UNTIL,
    }

    /** Which posts' media the results keep: any, or only those with media, photos or videos. */
    enum class Content { ALL, MEDIA, PHOTOS, VIDEOS }

    /** Whether results keep posts of one kind among the others, keep only them, or leave them out. */
    enum class Inclusion { INCLUDE, ONLY, EXCLUDE }

    val onlyMedia: Boolean get() = content == Content.MEDIA

    /** The query X receives. */
    val query: String get() = XSearchQuery.build(this)

    val activeFilters: List<Filter> get() = Filter.entries.filter(::isActive)

    /** Whether a filter besides the plain words is set, as when a typed query carries operators. */
    val hasOperators: Boolean get() = activeFilters.any { it != Filter.ALL_WORDS }

    fun isActive(filter: Filter): Boolean = when (filter) {
        Filter.LANGUAGE -> language.isNotEmpty()
        Filter.MIN_REPLIES, Filter.MIN_LIKES, Filter.MIN_RETWEETS -> XSearchQuery.positive(valueOf(filter))
        Filter.ONLY_MEDIA -> onlyMedia
        Filter.PHOTOS -> content == Content.PHOTOS
        Filter.VIDEOS -> content == Content.VIDEOS
        Filter.LINKS -> links != Inclusion.INCLUDE
        Filter.REPLIES -> replies != Inclusion.INCLUDE
        Filter.INCLUDE_REPOSTS -> includeReposts
        Filter.EXCLUDE_RETWEETS -> excludeRetweets
        Filter.SINCE -> since != null
        Filter.UNTIL -> until != null
        else -> valueOf(filter).isNotEmpty()
    }

    /** What a chip says for [filter]; empty for the switches. */
    fun valueOf(filter: Filter): String = when (filter) {
        Filter.ALL_WORDS -> allWords.trim()
        Filter.EXACT_PHRASE -> exactPhrase.trim()
        Filter.ANY_WORDS -> anyWords.trim()
        Filter.NONE_WORDS -> noneWords.trim()
        Filter.HASHTAGS -> hashtags.trim()
        Filter.LANGUAGE -> language
        Filter.FROM_ACCOUNTS -> fromAccounts.trim()
        Filter.TO_ACCOUNTS -> toAccounts.trim()
        Filter.MENTIONING_ACCOUNTS -> mentioningAccounts.trim()
        Filter.MIN_REPLIES -> minReplies.trim()
        Filter.MIN_LIKES -> minLikes.trim()
        Filter.MIN_RETWEETS -> minRetweets.trim()
        Filter.SINCE -> XSearchQuery.date(since)
        Filter.UNTIL -> XSearchQuery.date(until)
        else -> ""
    }

    /** These filters with [filter] typed in as [value]; a filter that takes no text is left as it was. */
    fun withText(filter: Filter, value: String): XAdvancedSearch = when (filter) {
        Filter.ALL_WORDS -> copy(allWords = value)
        Filter.EXACT_PHRASE -> copy(exactPhrase = value)
        Filter.ANY_WORDS -> copy(anyWords = value)
        Filter.NONE_WORDS -> copy(noneWords = value)
        Filter.HASHTAGS -> copy(hashtags = value)
        Filter.FROM_ACCOUNTS -> copy(fromAccounts = value)
        Filter.TO_ACCOUNTS -> copy(toAccounts = value)
        Filter.MENTIONING_ACCOUNTS -> copy(mentioningAccounts = value)
        Filter.MIN_REPLIES -> copy(minReplies = value)
        Filter.MIN_LIKES -> copy(minLikes = value)
        Filter.MIN_RETWEETS -> copy(minRetweets = value)
        else -> this
    }

    /** Media only on; off leaves photos or videos chosen as they are. */
    fun withOnlyMedia(on: Boolean): XAdvancedSearch = when {
        on -> copy(content = Content.MEDIA)
        onlyMedia -> copy(content = Content.ALL)
        else -> this
    }

    /** Reposts brought in and retweets left out contradict each other, so turning one on turns the other off. */
    fun withIncludeReposts(on: Boolean): XAdvancedSearch = copy(includeReposts = on, excludeRetweets = excludeRetweets && !on)

    fun withExcludeRetweets(on: Boolean): XAdvancedSearch = copy(excludeRetweets = on, includeReposts = includeReposts && !on)

    /** These filters without [filter]; the others, and their place in the query, stay. */
    fun cleared(filter: Filter): XAdvancedSearch = when (filter) {
        Filter.LANGUAGE -> copy(language = "")
        Filter.ONLY_MEDIA -> withOnlyMedia(false)
        Filter.PHOTOS -> if (content == Content.PHOTOS) copy(content = Content.ALL) else this
        Filter.VIDEOS -> if (content == Content.VIDEOS) copy(content = Content.ALL) else this
        Filter.LINKS -> copy(links = Inclusion.INCLUDE)
        Filter.REPLIES -> copy(replies = Inclusion.INCLUDE)
        Filter.INCLUDE_REPOSTS -> copy(includeReposts = false)
        Filter.EXCLUDE_RETWEETS -> copy(excludeRetweets = false)
        Filter.SINCE -> copy(since = null)
        Filter.UNTIL -> copy(until = null)
        else -> withText(filter, "")
    }

    companion object {
        /** The filters the form can show for [query]; whatever it cannot stays in [allWords] as typed. */
        fun fromQuery(query: String): XAdvancedSearch = XSearchQuery.read(query)

        /** The languages X tags posts with, by the code `lang:` takes (XTA's search_languages.dart). */
        val LANGUAGES = listOf(
            "am", "ar", "bg", "bn", "ca", "cs", "cy", "da", "de", "el", "en", "es", "et", "eu", "fa", "fi", "fr", "ga",
            "gl", "gu", "he", "hi", "hr", "hu", "hy", "id", "is", "it", "ja", "ka", "km", "kn", "ko", "lo", "lt", "lv",
            "ml", "mr", "ms", "my", "ne", "nl", "no", "or", "pa", "pl", "ps", "pt", "ro", "ru", "si", "sk", "sl", "sr",
            "sv", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh",
        )
    }
}

/**
 * The search a results screen shows, as XTA's SearchViewStore keeps it: the
 * committed [query], and the filters that wrote it or that its typed
 * operators describe. Typing a draft does not search yet.
 */
data class XSearchView(val query: String = "", val advanced: XAdvancedSearch = XAdvancedSearch()) {
    val hasQuery: Boolean get() = query.isNotEmpty()

    /** [text] searched: the filters stay while they still write it; otherwise its typed operators are read. */
    fun committed(text: String): XSearchView {
        val trimmed = text.trim()
        return if (advanced.query == trimmed) XSearchView(trimmed, advanced) else typed(trimmed)
    }

    /** A draft other than the committed query leaves the filters behind until it is committed. */
    fun drafted(text: String): XSearchView =
        if (advanced.activeFilters.isEmpty() || query == text.trim()) this else copy(advanced = XAdvancedSearch())

    /** The filters the form returned, and the query they write. */
    fun applied(filters: XAdvancedSearch): XSearchView = XSearchView(filters.query, filters)

    /** One filter removed from the results, the others kept. */
    fun without(filter: XAdvancedSearch.Filter): XSearchView = applied(advanced.cleared(filter))

    companion object {
        /** The view of [text] as typed, its operators shown as filters when it has any. */
        fun typed(text: String): XSearchView {
            val read = XAdvancedSearch.fromQuery(text)
            return XSearchView(text, if (read.hasOperators) read else XAdvancedSearch())
        }

        fun of(initialQuery: String): XSearchView = typed(initialQuery.trim())
    }
}
