package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.session.SavedSearch
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.store.antenna.Antenna
import com.aimdi.xtanative.store.antenna.AntennaScope
import com.aimdi.xtanative.store.backup.AccountRow
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.FeedFilterRow
import com.aimdi.xtanative.store.backup.GroupRow
import com.aimdi.xtanative.store.backup.ProfileNoteRow
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.reading.ArticleReadPoint
import com.aimdi.xtanative.store.reading.ReadingAppearance
import com.aimdi.xtanative.store.reading.ReadingJournal
import com.aimdi.xtanative.store.saved.ArchiveAnnotation
import com.aimdi.xtanative.store.saved.LikedPost
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.XtaRows
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.ReadingPrefs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a backup file turned out to be. */
sealed interface BackupRead {
    data object NotABackup : BackupRead

    /** Written by a newer XTA: refused rather than half applied. */
    data object Unsupported : BackupRead

    /** A backup with nothing in it to restore. */
    data object Empty : BackupRead

    data class Ready(val document: BackupDocument) : BackupRead

    companion object {
        /** [text] read as XTA's import reads a file (_data.dart). */
        fun of(text: String?): BackupRead {
            val document = text?.let(BackupDocument::parse) ?: return NotABackup
            return when {
                !document.isSupported -> Unsupported
                document.isEmpty -> Empty
                else -> Ready(document)
            }
        }
    }
}

/**
 * How each section of an XTA backup lands in XTA Native's stores. Like XTA's
 * import (SQLite's INSERT OR REPLACE), a row replaces the one with its key and
 * every other row stays; a section the file does not carry changes nothing.
 */
object BackupRestore {
    /** The session with the backup's accounts, follows, saved searches, groups and their members. */
    fun session(session: Session, document: BackupDocument): Session =
        listOf(::withAccounts, ::withFollows, ::withSearches, ::withGroups, ::withMembers, ::withHomeGroups)
            .fold(session) { current, step -> step(current, document) }

    private fun withAccounts(session: Session, document: BackupDocument): Session {
        val rows = document.accounts ?: return session
        val incoming = rows.mapNotNull(::accountOf).associateBy { it.id }
        return session.copy(accounts = session.accounts.filterNot { it.id in incoming } + incoming.values)
    }

    /** An X session from its row; one without a cookie cannot read anything and is left out, as XtaBackup leaves it. */
    fun accountOf(row: AccountRow): XAccount? {
        val headers = row.headers
        if (headers.keys.none { it.equals("cookie", ignoreCase = true) }) return null
        return XAccount(row.id, row.screenName, headers)
    }

    private fun withFollows(session: Session, document: BackupDocument): Session {
        val rows = document.subscriptions ?: return session
        val incoming = rows.associateBy { it.id }
        val follows = incoming.values.map { row ->
            FollowedAccount(row.id, row.screenName, row.name, row.avatarUrl, row.verified, row.inFeed, millis(row.createdAt))
        }
        val caps = rows.fold(session.postCaps) { caps, row ->
            val cap = row.maxPostsPerLoad
            if (cap != null && cap > 0) caps + (row.id to cap) else caps - row.id
        }
        return session.copy(following = session.following.filterNot { it.id in incoming } + follows, postCaps = caps)
    }

    private fun withSearches(session: Session, document: BackupDocument): Session {
        val rows = document.searchSubscriptions ?: return session
        val incoming = rows.filter { it.query.isNotBlank() }.associateBy { it.query.trim() }
        val searches = incoming.map { (query, row) -> SavedSearch(query, millis(row.createdAt)) }
        return session.copy(searches = session.searches.filterNot { it.query in incoming } + searches)
    }

    private fun withGroups(session: Session, document: BackupDocument): Session {
        val rows = document.groups?.filter { it.id != GroupRow.EVERYONE } ?: return session
        val known = session.groups.associateBy { it.id }
        val incoming = rows.associateBy { it.id }
        val groups = incoming.values.map { row ->
            FollowGroup(
                id = row.id,
                name = row.name,
                memberIds = known[row.id]?.memberIds.orEmpty(),
                emoji = row.emoji?.takeIf { it.isNotBlank() },
                pinned = row.pinned,
                nsfw = row.nsfw,
            )
        }
        val grouped = session.copy(groups = session.groups.filterNot { it.id in incoming } + groups)
        return rows.fold(grouped) { current, row -> current.nestedIn(row.id, row.parentId?.takeIf { it != row.id }) ?: current }
    }

    private fun withMembers(session: Session, document: BackupDocument): Session {
        val members = document.groupMembers.orEmpty().map { it.groupId to it.profileId } +
            document.searchGroupMembers.orEmpty().map { it.groupId to it.searchId }
        if (members.isEmpty()) return session
        val byGroup = members.filter { (group, member) -> group.isNotEmpty() && member.isNotEmpty() }.groupBy({ it.first }, { it.second })
        return session.copy(groups = session.groups.map { group ->
            byGroup[group.id]?.let { added -> group.copy(memberIds = (group.memberIds + added).distinct()) } ?: group
        })
    }

    /**
     * The groups kept out of the home feeds and the deck's columns: XTA keeps
     * them in its preferences, which the session holds for XTA Native's feeds.
     */
    private fun withHomeGroups(session: Session, document: BackupDocument): Session {
        val settings = document.settings ?: return session
        val groupIds = session.groups.mapTo(HashSet()) { it.id }
        val off = settings[HomePrefs.followingHiddenGroupIds.key]?.let(::stringList)
        val deck = (settings[HomePrefs.deckGroupIds.key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
        return session.copy(
            offFollowing = off?.filterTo(LinkedHashSet()) { it in groupIds } ?: session.offFollowing,
            deck = deck?.filter { it in groupIds }?.distinct() ?: session.deck,
        )
    }

    /** The saved posts and folders with the backup's; posts new to the device are saved in the file's order, newest first. */
    fun library(library: SavedLibrary, document: BackupDocument, now: Long): SavedLibrary? {
        if (document.savedPosts == null && document.savedFolders == null) return null
        val known = library.posts.associateBy { it.id }
        val posts = document.savedPosts.orEmpty().mapIndexedNotNull { index, row ->
            XtaRows.savedPost(J(row.toJson()), known[row.id]?.savedAt ?: (now - index))
        }.associateBy { it.id }
        val folders = document.savedFolders.orEmpty().mapNotNull { XtaRows.folder(J(it.toJson())) }.associateBy { it.id }
        return SavedLibrary(
            posts = (library.posts.filterNot { it.id in posts } + posts.values).sortedByDescending { it.savedAt },
            folders = library.folders.filterNot { it.id in folders } + folders.values,
        )
    }

    fun likes(likes: List<LikedPost>, document: BackupDocument, now: Long): List<LikedPost>? {
        val rows = document.likedPosts ?: return null
        val known = likes.associateBy { it.id }
        val incoming = rows.mapIndexedNotNull { index, row ->
            XtaRows.likedPost(J(row.toJson()), known[row.id]?.likedAt ?: (now - index))
        }.associateBy { it.id }
        return (likes.filterNot { it.id in incoming } + incoming.values).sortedByDescending { it.likedAt }
    }

    /** The notes with the backup's; their files' bytes travel with them and become files again. */
    fun localPosts(posts: List<LocalPost>, document: BackupDocument, now: Long): List<LocalPost>? {
        val rows = document.localPosts ?: return null
        val incoming = rows.mapNotNull { XtaRows.localPost(J(it.toJson()), now) }.associateBy { it.id }
        return (posts.filterNot { it.id in incoming } + incoming.values).sortedByDescending { it.createdAt }
    }

    /** The article notes, by article id: merged into the device's, whose own edits win. */
    fun annotations(document: BackupDocument): Map<String, ArchiveAnnotation>? {
        val section = document.archiveAnnotations ?: return null
        return XtaRows.annotations(J(JsonObject(section.mapValues { it.value.toJson() })))
    }

    /** The private notes on profiles with the backup's: a note replaces the one on its profile, every other stays. */
    fun profileNotes(notes: List<ProfileNoteRow>, document: BackupDocument): List<ProfileNoteRow>? =
        document.profileNotes?.let { rows -> KeptSections.upsert(notes, rows) { it.profileId }.orEmpty() }

    /** The hidden reposts and replies with the backup's, by account; null when the file carries neither. */
    fun profileFilters(filters: Pair<List<FeedFilterRow>, List<FeedFilterRow>>, document: BackupDocument): Pair<List<FeedFilterRow>, List<FeedFilterRow>>? {
        if (document.repostFilters == null && document.replyFilters == null) return null
        val (reposts, replies) = filters
        return KeptSections.upsert(reposts, document.repostFilters) { it.userId }.orEmpty() to
            KeptSections.upsert(replies, document.replyFilters) { it.userId }.orEmpty()
    }

    fun antennas(document: BackupDocument, now: Long): List<Antenna>? = document.antennas?.map { row ->
        Antenna(row.id, row.name, row.includeTerms, row.excludeTerms, AntennaScope.of(row.scope), XtaTime.parse(row.createdAt)?.toEpochMilli() ?: now)
    }

    /**
     * Where each feed was left, by the group whose feed it is (null for
     * Following): only feeds this device has. XTA's `-1` is Following.
     */
    fun readPlaces(document: BackupDocument, groupIds: Set<String>): Map<String?, FeedPlace> =
        document.readPositions.orEmpty().filter { it.chainId.isNotEmpty() }.mapNotNull { row ->
            val key = FeedKeys.readPositionKey(row.groupId)
            when {
                key == FeedKeys.FOLLOWING -> null to FeedPlace(row.chainId, 0)
                key in groupIds -> key to FeedPlace(row.chainId, 0)
                else -> null
            }
        }.distinctBy { it.first }.toMap()

    /** The article places in the backup's settings; null when it carries none. */
    fun journal(document: BackupDocument): Map<String, ArticleReadPoint>? =
        preferenceText(document, ReadingPrefs.articles.key)?.let { ReadingJournal.fromXta(J.parse(it)) }

    fun appearance(document: BackupDocument): ReadingAppearance? =
        preferenceText(document, ReadingPrefs.appearance.key)?.let { J.parse(it) }?.takeIf { it.raw is JsonObject }?.let(ReadingAppearance::parse)

    private fun preferenceText(document: BackupDocument, key: String): String? =
        (document.settings?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    /** A list XTA keeps as a JSON array in a string, or as a list. */
    private fun stringList(element: JsonElement): List<String> = when (element) {
        is JsonPrimitive -> if (element.isString) J.parse(element.content).list.mapNotNull { it.string } else emptyList()
        else -> J(element).list.mapNotNull { it.string }
    }

    /** A time XTA wrote, in epoch milliseconds; a missing or unreadable one sorts first. */
    fun millis(text: String?): Long = XtaTime.parse(text)?.toEpochMilli() ?: 0
}
