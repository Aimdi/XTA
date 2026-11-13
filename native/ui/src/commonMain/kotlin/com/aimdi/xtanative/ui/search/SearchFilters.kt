package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.core.x.search.XAdvancedSearch
import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Filter
import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Inclusion
import com.aimdi.xtanative.core.x.search.XSearchView

/** What a filter is called, on its chip above the results and in the advanced search form: XTA's labels. */
enum class FilterLabel {
    ALL_WORDS,
    EXACT_PHRASE,
    ANY_WORDS,
    NONE_WORDS,
    HASHTAGS,
    LANGUAGE,
    FROM_ACCOUNTS,
    TO_ACCOUNTS,
    MENTIONING_ACCOUNTS,
    MIN_REPLIES,
    MIN_LIKES,
    MIN_REPOSTS,
    ONLY_MEDIA,
    PHOTOS,
    VIDEOS,
    INCLUDE_LINKS,
    ONLY_LINKS,
    HIDE_LINKS,
    INCLUDE_REPLIES,
    ONLY_REPLIES,
    HIDE_REPLIES,
    INCLUDE_REPOSTS,
    HIDE_RETWEETS,
    SINCE,
    UNTIL,
}

/** A chip above the results: the [filter] it takes away, its [label], and the [value] shown after it, empty for a switch. */
data class FilterChip(val filter: Filter, val label: FilterLabel, val value: String)

object SearchFilters {
    /** The chips of [search]'s filters that are set, in the order of the form; a language is named in its own language. */
    fun chips(search: XAdvancedSearch): List<FilterChip> = search.activeFilters.map { filter ->
        val value = if (filter == Filter.LANGUAGE) SearchLanguages.name(search.language) else search.valueOf(filter)
        FilterChip(filter, labelOf(filter, search), value)
    }

    /** What [filter] is called; the links and replies filters by the choice [search] makes. */
    fun labelOf(filter: Filter, search: XAdvancedSearch): FilterLabel = when (filter) {
        Filter.ALL_WORDS -> FilterLabel.ALL_WORDS
        Filter.EXACT_PHRASE -> FilterLabel.EXACT_PHRASE
        Filter.ANY_WORDS -> FilterLabel.ANY_WORDS
        Filter.NONE_WORDS -> FilterLabel.NONE_WORDS
        Filter.HASHTAGS -> FilterLabel.HASHTAGS
        Filter.LANGUAGE -> FilterLabel.LANGUAGE
        Filter.FROM_ACCOUNTS -> FilterLabel.FROM_ACCOUNTS
        Filter.TO_ACCOUNTS -> FilterLabel.TO_ACCOUNTS
        Filter.MENTIONING_ACCOUNTS -> FilterLabel.MENTIONING_ACCOUNTS
        Filter.MIN_REPLIES -> FilterLabel.MIN_REPLIES
        Filter.MIN_LIKES -> FilterLabel.MIN_LIKES
        Filter.MIN_RETWEETS -> FilterLabel.MIN_REPOSTS
        Filter.ONLY_MEDIA -> FilterLabel.ONLY_MEDIA
        Filter.PHOTOS -> FilterLabel.PHOTOS
        Filter.VIDEOS -> FilterLabel.VIDEOS
        Filter.LINKS -> linksLabel(search.links)
        Filter.REPLIES -> repliesLabel(search.replies)
        Filter.INCLUDE_REPOSTS -> FilterLabel.INCLUDE_REPOSTS
        Filter.EXCLUDE_RETWEETS -> FilterLabel.HIDE_RETWEETS
        Filter.SINCE -> FilterLabel.SINCE
        Filter.UNTIL -> FilterLabel.UNTIL
    }

    /** The replies filter's choices, each named for what it does to replies. */
    fun repliesLabel(inclusion: Inclusion): FilterLabel = when (inclusion) {
        Inclusion.INCLUDE -> FilterLabel.INCLUDE_REPLIES
        Inclusion.ONLY -> FilterLabel.ONLY_REPLIES
        Inclusion.EXCLUDE -> FilterLabel.HIDE_REPLIES
    }

    /** The links filter's choices, each named for what it does to posts with a link. */
    fun linksLabel(inclusion: Inclusion): FilterLabel = when (inclusion) {
        Inclusion.INCLUDE -> FilterLabel.INCLUDE_LINKS
        Inclusion.ONLY -> FilterLabel.ONLY_LINKS
        Inclusion.EXCLUDE -> FilterLabel.HIDE_LINKS
    }

    /** The advanced search form opened from the results: the filters shown, else what the field holds read as filters. */
    fun formFor(view: XSearchView, fieldText: String): XAdvancedSearch =
        if (view.advanced.activeFilters.isEmpty()) XAdvancedSearch.fromQuery(fieldText) else view.advanced
}
