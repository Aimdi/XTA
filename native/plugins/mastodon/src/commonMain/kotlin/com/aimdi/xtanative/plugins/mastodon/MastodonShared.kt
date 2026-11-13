package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.networks.mastodon.MastodonSnapshot
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive

/**
 * Mastodon in the app's archive and its shared timelines (mastodon_archive.dart,
 * mastodon_interleaved.dart): a post kept as XTA keeps it, which posts a
 * mixed feed takes, and each as the shared row draws it.
 */
internal object MastodonShared {
    /** XTA's kMastodonInterleavedPageSize: ten posts of the accounts asked, in all. */
    const val SHARED = 10

    /** [post] in Saved, the likes and a backup: under its address (status ids are each instance's own), by its author, as XTA's record. */
    fun archive(post: MastodonPost): PluginPostArchive = PluginPostArchive(MastodonSnapshot.archiveId(post), post.acct, MastodonSnapshot.archive(post))

    /** What a feed taking [kinds] mixes in of [posts], in their order: the kinds first (a boost is a repost), then [limit] of them, those with a date. */
    fun mixed(posts: List<MastodonPost>, kinds: FeedKinds, limit: Int = SHARED): List<MastodonPost> =
        posts.filter { kinds.takes(it.isReply, it.boosted) }.take(limit).filter { it.publishedAt != null }

    /**
     * [post] as the shared row draws it among other networks' posts: who
     * boosted it, and behind its content warning (its words in place of the
     * rest until the reader opens it) or the notice on media marked sensitive.
     */
    fun post(post: MastodonPost, language: String): NetworkPost = post.toNetworkPost(language, open = true).copy(
        repostedBy = post.boostedBy?.takeIf { post.boosted },
        warning = post.spoilerText.trim().takeIf { post.hasSpoiler } ?: Strings.sensitive.get(language).takeIf { post.sensitive },
        coversText = post.hasSpoiler,
    )
}
