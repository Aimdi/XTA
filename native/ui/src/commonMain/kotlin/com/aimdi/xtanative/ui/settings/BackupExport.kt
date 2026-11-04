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
import com.aimdi.xtanative.store.backup.BackupKeys
import com.aimdi.xtanative.store.backup.ExportChoice
import com.aimdi.xtanative.store.backup.FeedFilterRow
import com.aimdi.xtanative.store.backup.GroupMemberRow
import com.aimdi.xtanative.store.backup.GroupRow
import com.aimdi.xtanative.store.backup.LikedPostRow
import com.aimdi.xtanative.store.backup.LocalPostRow
import com.aimdi.xtanative.store.backup.NetworkAccount
import com.aimdi.xtanative.store.backup.NetworkMembers
import com.aimdi.xtanative.store.backup.ProfileNoteRow
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
    /** The private notes on profiles. */
    val profileNotes: List<ProfileNoteRow> = emptyList(),
    /** The accounts whose reposts, and whose replies, the feeds hide. */
    val repostFilters: List<FeedFilterRow> = emptyList(),
    val replyFilters: List<FeedFilterRow> = emptyList(),
    /** What the running plugins keep, by section name ([PluginBackups.exported]). */
    val pluginSections: Map<String, String> = emptyMap(),
    /** What the running plugins follow, as each app names it ([PluginBackups.accounts]): how their accounts go out in the groups. */
    val networkAccounts: List<NetworkAccount> = emptyList(),
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
            pluginSections = pluginSections(content),
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
            groupMembers = groupMembers(session, content.networkAccounts),
            searchGroupMembers = emptyList(),
            savedPosts = content.library.posts.map { SavedPostRow(it.id, it.authorId, it.content?.toString(), it.folderId, it.note) },
            savedFolders = content.library.folders.mapNotNull { SavedFolderRow.from(J(XtaRows.row(it))) },
            likedPosts = content.likes.map { LikedPostRow(it.id, it.authorId, it.content?.toString()) },
            repostFilters = content.repostFilters,
            replyFilters = content.replyFilters,
            readPositions = content.readPlaces.map { (key, place) -> ReadPositionRow(key, place.postId) },
            accounts = session.accounts.map { account ->
                AccountRow(account.id, JsonPrimitive(JsonObject(account.headers.mapValues { JsonPrimitive(it.value) }).toString()), account.screenName)
            },
            profileNotes = content.profileNotes,
            antennas = content.antennas.map { antenna ->
                AntennaRow(antenna.id, antenna.name, antenna.includeTerms, antenna.excludeTerms, antenna.scope.stored, XtaTime.iso(Instant.ofEpochMilli(antenna.createdAt)))
            },
            localPosts = content.localPosts.mapNotNull { LocalPostRow.from(J(XtaRows.row(it))) },
        ).withNetworks(networks(content)).chosen(choice)
    }

    /**
     * Every network's table, as XTA writes them: the rows of a running
     * plugin's section named by its key, else the ones kept for it, else none.
     */
    private fun networks(content: DeviceContent): BackupDocument =
        content.kept.withNetworks(BackupDocument.ofNetworkRows(content.pluginSections)).withEveryNetwork()

    /** The plugins' sections XTA has no key for, a running plugin's own over the one kept for it. */
    private fun pluginSections(content: DeviceContent): Map<String, String>? =
        (content.kept.pluginSections.orEmpty() + content.pluginSections.filterKeys { it !in BackupKeys.NETWORKS }).takeIf { it.isNotEmpty() }

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

    /** The groups' members as XTA's groups hold them, each once a group: the bare networks' accounts by their subscription's id ([NetworkMembers]). */
    private fun groupMembers(session: Session, accounts: List<NetworkAccount>): List<GroupMemberRow> {
        val searches = session.searches.mapTo(HashSet()) { it.query }
        return session.groups.flatMap { group ->
            group.memberIds.map { NetworkMembers.xtaMemberId(it, accounts, searches) }.distinct().map { GroupMemberRow(group.id, it) }
        }
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
