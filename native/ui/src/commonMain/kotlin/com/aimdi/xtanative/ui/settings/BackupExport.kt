package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.antenna.Antenna
import com.aimdi.xtanative.store.backup.AccountRow
import com.aimdi.xtanative.store.backup.AntennaRow
import com.aimdi.xtanative.store.backup.ArchiveAnnotationRow
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.ExportChoice
import com.aimdi.xtanative.store.backup.GroupMemberRow
import com.aimdi.xtanative.store.backup.GroupRow
import com.aimdi.xtanative.store.backup.LikedPostRow
import com.aimdi.xtanative.store.backup.LocalPostRow
import com.aimdi.xtanative.store.backup.ReadPositionRow
import com.aimdi.xtanative.store.backup.SavedFolderRow
import com.aimdi.xtanative.store.backup.SavedPostRow
import com.aimdi.xtanative.store.backup.SearchSubscriptionRow
import com.aimdi.xtanative.store.backup.UserSubscriptionRow
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
import com.aimdi.xtanative.store.settings.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Everything the device holds that an XTA backup carries, read at one moment. */
data class DeviceContent(
    val session: Session = Session(),
    val settings: Settings = Settings(),
    val library: SavedLibrary = SavedLibrary(),
    val likes: List<LikedPost> = emptyList(),
    /** With their files' bytes. */
    val localPosts: List<LocalPost> = emptyList(),
    val annotations: Map<String, ArchiveAnnotation> = emptyMap(),
    val antennas: List<Antenna> = emptyList(),
    /** Where each feed was left, by XTA's key: `following` or a group's id. */
    val readPlaces: Map<String, FeedPlace> = emptyMap(),
    val journal: Map<String, ArticleReadPoint> = emptyMap(),
    val appearance: ReadingAppearance? = null,
    /** What was restored that no store here holds yet. */
    val kept: BackupDocument = BackupDocument(),
)

/** XTA's export (settings_export_screen.dart, _data.dart): the same JSON XTA writes, from XTA Native's stores. */
object BackupExport {
    private val day = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** The file name XTA gives an export. */
    fun fileName(date: LocalDate): String = "xta-${day.format(date)}.json"

    /** The backup [choice] asks for; [appVersion] is this build's, `version+build`. */
    fun document(content: DeviceContent, choice: ExportChoice, appVersion: String, now: Instant): BackupDocument {
        val session = content.session
        val kept = content.kept
        return BackupDocument(
            exportedAt = XtaTime.iso(now),
            appVersion = appVersion,
            archiveAnnotations = ArchiveAnnotationRow.section(J(XtaRows.annotationsSection(content.annotations))),
            settings = settings(content, choice),
            threadsSubscriptions = kept.threadsSubscriptions.orEmpty(),
            threadsLocalLikes = kept.threadsLocalLikes.orEmpty(),
            blueskySubscriptions = kept.blueskySubscriptions.orEmpty(),
            blueskyLocalLikes = kept.blueskyLocalLikes.orEmpty(),
            mastodonSubscriptions = kept.mastodonSubscriptions.orEmpty(),
            tiktokSubscriptions = kept.tiktokSubscriptions.orEmpty(),
            instagramSubscriptions = kept.instagramSubscriptions.orEmpty(),
            redditSubscriptions = kept.redditSubscriptions.orEmpty(),
            redditLocalVotes = kept.redditLocalVotes.orEmpty(),
            substackSubscriptions = kept.substackSubscriptions.orEmpty(),
            rssSubscriptions = kept.rssSubscriptions.orEmpty(),
            booruSubscriptions = kept.booruSubscriptions.orEmpty(),
            ehFavorites = kept.ehFavorites.orEmpty(),
            ehHistory = kept.ehHistory.orEmpty(),
            stockSubscriptions = kept.stockSubscriptions.orEmpty(),
            searchSubscriptions = session.searches.map { SearchSubscriptionRow(it.query, sqlite(it.followedAt)) },
            subscriptions = session.following.map { account ->
                UserSubscriptionRow(
                    id = account.id,
                    screenName = account.screenName,
                    name = account.name,
                    avatarUrl = account.avatarUrl,
                    verified = account.verified,
                    createdAt = sqlite(account.followedAt),
                    inFeed = account.inFeed,
                    maxPostsPerLoad = session.postCaps[account.id],
                )
            },
            groups = groups(session, kept, now),
            groupMembers = session.groups.flatMap { group -> group.memberIds.map { GroupMemberRow(group.id, it) } },
            searchGroupMembers = emptyList(),
            savedPosts = content.library.posts.map { SavedPostRow(it.id, it.authorId, it.content?.toString(), it.folderId, it.note) },
            savedFolders = content.library.folders.mapNotNull { SavedFolderRow.from(J(XtaRows.row(it))) },
            likedPosts = content.likes.map { LikedPostRow(it.id, it.authorId, it.content?.toString()) },
            repostFilters = kept.repostFilters.orEmpty(),
            replyFilters = kept.replyFilters.orEmpty(),
            readPositions = content.readPlaces.map { (key, place) -> ReadPositionRow(key, place.postId) },
            accounts = session.accounts.map { account ->
                AccountRow(account.id, JsonPrimitive(JsonObject(account.headers.mapValues { JsonPrimitive(it.value) }).toString()), account.screenName)
            },
            profileNotes = kept.profileNotes.orEmpty(),
            antennas = content.antennas.map { antenna ->
                AntennaRow(antenna.id, antenna.name, antenna.includeTerms, antenna.excludeTerms, antenna.scope.stored, XtaTime.iso(Instant.ofEpochMilli(antenna.createdAt)))
            },
            localPosts = content.localPosts.mapNotNull { LocalPostRow.from(J(XtaRows.row(it))) },
        ).chosen(choice)
    }

    /**
     * The settings section: the preferences without secrets, with what XTA
     * keeps in preferences but XTA Native keeps elsewhere written back into
     * them (the article places and type, the groups off the home feeds, the
     * deck's columns).
     */
    fun settings(content: DeviceContent, choice: ExportChoice): Map<String, JsonElement>? {
        val section = content.settings.exported(choice.settings, choice.subscriptions) ?: return null
        if (!choice.settings) return section
        val session = content.session
        val overrides = buildMap<String, JsonElement> {
            put(HomePrefs.followingHiddenGroupIds.key, JsonPrimitive(JsonArray(session.offFollowing.map(::JsonPrimitive)).toString()))
            put(HomePrefs.deckGroupIds.key, JsonPrimitive(session.deck.joinToString(",")))
            if (content.journal.isNotEmpty()) put(ReadingPrefs.articles.key, JsonPrimitive(ReadingJournal.toXta(content.journal).toString()))
            content.appearance?.let { put(ReadingPrefs.appearance.key, JsonPrimitive(it.toJson().toString())) }
        }
        return section + overrides
    }

    /** The session's groups as XTA's rows, with what their restored rows carried beyond them. */
    private fun groups(session: Session, kept: BackupDocument, now: Instant): List<GroupRow> {
        val rows = kept.groups.orEmpty().associateBy { it.id }
        return session.groups.map { group ->
            val row = rows[group.id]
            GroupRow(
                id = group.id,
                name = group.name,
                icon = row?.icon ?: GroupRow.DEFAULT_ICON,
                color = row?.color,
                createdAt = row?.createdAt ?: XtaTime.iso(now),
                pinned = group.pinned,
                nsfw = group.nsfw,
                emoji = group.emoji,
                markStyle = row?.markStyle ?: 0,
                parentId = session.nesting[group.id],
            )
        }
    }

    /** Where each feed was left, by XTA's key, from the feeds' own places ([group] null for Following). */
    fun readPlaceKey(group: String?): String = group ?: FeedKeys.FOLLOWING

    private fun sqlite(millis: Long): String = XtaTime.sqlite(Instant.ofEpochMilli(millis))
}
