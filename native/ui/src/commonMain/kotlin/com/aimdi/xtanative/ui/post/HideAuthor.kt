package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.saved.ProfileFilters

/**
 * The entry of an X post's ⋯ menu that hides its author's posts in the
 * feeds, or shows them again: whose posts ([userId], [screenName]), and
 * whether they are [hidden] now, which is what a tap turns round.
 */
data class HideAuthor(val userId: String, val screenName: String, val hidden: Boolean) {
    companion object {
        /**
         * The entry for [post] under [filters]. It is about who wrote what is
         * read: for a repost the author of the reposted post, not who
         * reposted it. None before the filters are read (the entry would not
         * know its wording), nor for an author X gave no id or handle.
         */
        fun of(post: XPost, filters: ProfileFilters?): HideAuthor? {
            val author = post.shown.author
            if (filters == null || author.id.isEmpty() || author.screenName.isEmpty()) return null
            return HideAuthor(author.id, author.screenName, filters.allHidden(author.id))
        }
    }
}
