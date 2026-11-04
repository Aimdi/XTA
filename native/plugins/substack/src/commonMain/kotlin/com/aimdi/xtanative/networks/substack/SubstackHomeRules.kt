package com.aimdi.xtanative.networks.substack

/** What a feed's search field and sort ask of the posts already loaded: nothing is asked of Substack. */
data class SubstackLoadedOptions(val query: String = "", val order: SubstackOrder = SubstackOrder.NEWEST) {
    val searching: Boolean get() = query.isNotBlank()
}

/** The Library's sections, in XTA's order. */
enum class SubstackLibrarySection { FOLLOWING, SAVED, LIKED }

/** What the home tab's panes show, as XTA's substack_screen.dart decides it. */
object SubstackHomeRules {
    /** Home: the loaded posts of the publications followed now, through [filter], then [options]. */
    fun home(
        posts: List<SubstackPost>,
        publications: List<SubstackPublication>,
        filter: SubstackFeedFilter,
        readIds: Set<String>,
        isSaved: (SubstackPost) -> Boolean,
        options: SubstackLoadedOptions,
    ): List<SubstackPost> {
        val followed = publications.mapTo(HashSet()) { it.baseUrl }
        val shown = posts.filter { it.publicationBaseUrl in followed && SubstackFilters.matches(it, filter, readIds, isSaved) }
        return SubstackFilters.loaded(shown, options.query, options.order)
    }

    /** Inbox: the unread posts of the publications followed now, through [options]. */
    fun inbox(posts: List<SubstackPost>, publications: List<SubstackPublication>, readIds: Set<String>, options: SubstackLoadedOptions): List<SubstackPost> =
        home(posts, publications, SubstackFeedFilter.UNREAD, readIds, { false }, options)

    /** Whether a feed's posts are narrowed: a content filter (Home only), an order other than newest. */
    fun filtered(options: SubstackLoadedOptions, filter: SubstackFeedFilter?): Boolean =
        options.order != SubstackOrder.NEWEST || (filter != null && filter != SubstackFeedFilter.ALL)

    /** How many of a feed's controls are set: its search, its order, its content filter. */
    fun activeCount(options: SubstackLoadedOptions, filter: SubstackFeedFilter?): Int =
        listOf(options.searching, options.order != SubstackOrder.NEWEST, filter != null && filter != SubstackFeedFilter.ALL).count { it }

    /** The logo of the follow that gave [post]: a post's payload carries none. */
    fun logoOf(publications: List<SubstackPublication>, post: SubstackPost): String? {
        val base = post.publicationBaseUrl.lowercase()
        return publications.firstOrNull { it.baseUrl.lowercase() == base || it.name == post.publicationName }?.logoUrl
    }

    /** The publications with an unread post loaded, by address, for the publication picker's marks. */
    fun withUnread(posts: List<SubstackPost>, readIds: Set<String>): Set<String> =
        posts.filterNot { SubstackPosts.isRead(readIds, it) }.mapTo(HashSet()) { it.publicationBaseUrl.lowercase() }

    /** Whether "Mark all as read" has anything to do. */
    fun anyUnread(posts: List<SubstackPost>, readIds: Set<String>): Boolean = posts.any { !SubstackPosts.isRead(readIds, it) }

    /** A library post matching [query] by its title, publication or excerpt. */
    fun matches(query: String, post: SubstackPost): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || post.title.lowercase().contains(q) || post.publicationName.lowercase().contains(q) ||
            post.excerpt?.lowercase()?.contains(q) == true
    }

    /** A follow matching [query] by its name or address. */
    fun matches(query: String, publication: SubstackPublication): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || publication.displayName.lowercase().contains(q) || publication.baseUrl.lowercase().contains(q)
    }

    /**
     * The saved or liked [posts] the Library shows for [query]: by title, or by
     * a kept body ([passages], by post key), whose passage shows above the card
     * when the title did not match.
     */
    fun library(posts: List<SubstackPost>, query: String, passages: Map<String, String>): List<Pair<SubstackPost, String?>> =
        posts.filter { matches(query, it) || it.key in passages }.map { post -> post to passages[post.key]?.takeIf { !matches(query, post) } }

    /** A publication's address as its rows show it: the host alone. */
    fun hostOf(publication: SubstackPublication): String = SubstackHosts.hostOf(publication.baseUrl).ifEmpty { publication.baseUrl }

    /** The publication a post came from, with the logo its follow has. */
    fun publicationOf(post: SubstackPost, logoUrl: String? = null): SubstackPublication {
        val publication = post.publication
        return publication.copy(name = publication.displayName, logoUrl = logoUrl ?: publication.logoUrl)
    }
}
