package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.backup.BackupDocument
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The parts of a restored XTA backup no store of XTA Native holds yet: each
 * network's follows, likes, votes and galleries (until its plugin brings its
 * own backup sections), the profiles' repost and reply filters and private
 * notes, and what a group's row carries beyond the session's groups (its
 * icon, colour, mark and when it was made). They are kept as XTA wrote them
 * in `backup/kept.json`, so the next export carries them again and nothing
 * a reader restored is lost.
 */
class KeptSections(dir: StoreDir) {
    private val file = dir.file(FILE)
    private val mutex = Mutex()
    private var current: BackupDocument? = null

    suspend fun get(): BackupDocument = mutex.withLock { loaded() }

    /** [incoming]'s kept sections merged in, row by row, as XTA's import replaces rows by their key. */
    suspend fun merge(incoming: BackupDocument): BackupDocument = mutex.withLock {
        val next = merged(loaded(), incoming)
        if (next != current) {
            file.write(next.encode())
            current = next
        }
        next
    }

    private suspend fun loaded(): BackupDocument {
        current?.let { return it }
        val read = file.read()?.let(BackupDocument::parse)?.let(::keptOf) ?: BackupDocument()
        current = read
        return read
    }

    companion object {
        const val FILE = "backup/kept.json"

        /** Only the sections this store keeps. */
        fun keptOf(document: BackupDocument): BackupDocument = BackupDocument(
            threadsSubscriptions = document.threadsSubscriptions,
            threadsLocalLikes = document.threadsLocalLikes,
            blueskySubscriptions = document.blueskySubscriptions,
            blueskyLocalLikes = document.blueskyLocalLikes,
            mastodonSubscriptions = document.mastodonSubscriptions,
            tiktokSubscriptions = document.tiktokSubscriptions,
            instagramSubscriptions = document.instagramSubscriptions,
            redditSubscriptions = document.redditSubscriptions,
            redditLocalVotes = document.redditLocalVotes,
            substackSubscriptions = document.substackSubscriptions,
            rssSubscriptions = document.rssSubscriptions,
            booruSubscriptions = document.booruSubscriptions,
            ehFavorites = document.ehFavorites,
            ehHistory = document.ehHistory,
            stockSubscriptions = document.stockSubscriptions,
            groups = document.groups,
            repostFilters = document.repostFilters,
            replyFilters = document.replyFilters,
            profileNotes = document.profileNotes,
        )

        /** [kept] with each of [incoming]'s kept sections merged in; a section [incoming] does not carry stays as it was. */
        fun merged(kept: BackupDocument, incoming: BackupDocument): BackupDocument = BackupDocument(
            threadsSubscriptions = upsert(kept.threadsSubscriptions, incoming.threadsSubscriptions) { it.id },
            threadsLocalLikes = upsert(kept.threadsLocalLikes, incoming.threadsLocalLikes) { it.id },
            blueskySubscriptions = upsert(kept.blueskySubscriptions, incoming.blueskySubscriptions) { it.id },
            blueskyLocalLikes = upsert(kept.blueskyLocalLikes, incoming.blueskyLocalLikes) { it.id },
            mastodonSubscriptions = upsert(kept.mastodonSubscriptions, incoming.mastodonSubscriptions) { it.id },
            tiktokSubscriptions = upsert(kept.tiktokSubscriptions, incoming.tiktokSubscriptions) { it.id },
            instagramSubscriptions = upsert(kept.instagramSubscriptions, incoming.instagramSubscriptions) { it.id },
            redditSubscriptions = upsert(kept.redditSubscriptions, incoming.redditSubscriptions) { it.id },
            redditLocalVotes = upsert(kept.redditLocalVotes, incoming.redditLocalVotes) { it.id },
            substackSubscriptions = upsert(kept.substackSubscriptions, incoming.substackSubscriptions) { it.id },
            rssSubscriptions = upsert(kept.rssSubscriptions, incoming.rssSubscriptions) { it.id },
            booruSubscriptions = upsert(kept.booruSubscriptions, incoming.booruSubscriptions) { it.id },
            ehFavorites = upsert(kept.ehFavorites, incoming.ehFavorites) { it.gallery.gid },
            ehHistory = upsert(kept.ehHistory, incoming.ehHistory) { it.gallery.gid },
            stockSubscriptions = upsert(kept.stockSubscriptions, incoming.stockSubscriptions) { it.id },
            groups = upsert(kept.groups, incoming.groups) { it.id },
            repostFilters = upsert(kept.repostFilters, incoming.repostFilters) { it.userId },
            replyFilters = upsert(kept.replyFilters, incoming.replyFilters) { it.userId },
            profileNotes = upsert(kept.profileNotes, incoming.profileNotes) { it.profileId },
        )

        /**
         * [incoming]'s rows over [current]'s: a row with a known key replaces
         * the old one and goes last, as SQLite's INSERT OR REPLACE does; null
         * [incoming] leaves [current] alone.
         */
        fun <T, K> upsert(current: List<T>?, incoming: List<T>?, key: (T) -> K): List<T>? {
            if (incoming == null) return current
            val fresh = incoming.associateBy(key)
            return current.orEmpty().filterNot { key(it) in fresh } + fresh.values
        }
    }
}
