package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.ReadPosition
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccountHealth
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
import com.aimdi.xtanative.store.settings.PluginStorePrefs
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
    /** The last post read in each feed, by XTA's feed key: `following`, `for_you` or a group's id. */
    val readPositions: Map<String, ReadPosition> = emptyMap(),
    /** Each X account's sign-in health, by account id; a clean one may be left out. */
    val accountHealth: Map<String, XAccountHealth> = emptyMap(),
    /** Whether the plugin store offers private plugins; null leaves the settings' own value. */
    val showPrivate: Boolean? = null,
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
            readPositions = content.readPositions.map { (key, position) -> ReadPositionRow(key, position.threadId, position.createdAt?.let(XtaTime::iso)) },
            accounts = session.accounts.map { account ->
                val health = content.accountHealth[account.id] ?: XAccountHealth()
                AccountRow(
                    id = account.id,
                    authHeader = JsonPrimitive(JsonObject(account.headers.mapValues { JsonPrimitive(it.value) }).toString()),
                    screenName = account.screenName,
                    lastNotFoundAt = health.notFoundAt?.let { XtaTime.iso(Instant.ofEpochMilli(it)) },
                    consecutiveNotFound = health.consecutiveNotFound,
                )
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
     * deck's columns, each feed's catch-up mode, the plugin store's private
     * plugins).
     */
    fun settings(content: DeviceContent, choice: ExportChoice): Map<String, JsonElement>? {
        val section = content.settings.exported(choice.settings, choice.subscriptions) ?: return null
        if (!choice.settings) return section
        val session = content.session
        val overrides = buildMap<String, JsonElement> {
            catchUpModes(session).forEach { (key, on) -> put(key, JsonPrimitive(on)) }
            content.showPrivate?.let { put(PluginStorePrefs.showPrivate.key, JsonPrimitive(it)) }
            put(HomePrefs.followingHiddenGroupIds.key, JsonPrimitive(JsonArray(session.offFollowing.map(::JsonPrimitive)).toString()))
            put(HomePrefs.deckGroupIds.key, JsonPrimitive(session.deck.joinToString(",")))
            if (content.journal.isNotEmpty()) put(ReadingPrefs.articles.key, JsonPrimitive(ReadingJournal.toXta(content.journal).toString()))
            content.appearance?.let { put(ReadingPrefs.appearance.key, JsonPrimitive(it.toJson().toString())) }
        }
        return section.filterKeys { !it.startsWith(BackupRestore.CATCH_UP_PREFIX) } + overrides
    }

    /**
     * Every feed's catch-up mode under XTA's key, on or off, Following's
     * under XTA's `-1`: a key a restore brought for a group gone since is
     * left out, and one the reader has turned off since says so.
     */
    private fun catchUpModes(session: Session): Map<String, Boolean> =
        (listOf(FeedKeys.LEGACY_FOLLOWING to FeedKeys.FOLLOWING) + session.groups.map { it.id to it.id })
            .associate { (xta, feed) -> FeedKeys.catchUpPreference(xta) to session.settingsOf(feed).catchUp }

    /** The groups' members as XTA's groups hold them, each once a group: the bare networks' accounts by their subscription's id ([NetworkMembers]). */
    private fun groupMembers(session: Session, accounts: List<NetworkAccount>): List<GroupMemberRow> {
        val searches = session.searches.mapTo(HashSet()) { it.query }
        return session.groups.flatMap { group ->
            group.memberIds.map { NetworkMembers.xtaMemberId(it, accounts, searches) }.distinct().map { GroupMemberRow(group.id, it) }
        }
    }

    /**
     * The session's groups as XTA's rows, with their own icon, colour, mark
     * and creation time. A group from before XTA Native kept when it was made
     * takes the time its restored row carried, else [now].
     */
    private fun groups(session: Session, kept: BackupDocument, now: Instant): List<GroupRow> {
        val rows = kept.groups.orEmpty().associateBy { it.id }
        return session.groups.map { group ->
            GroupRow(
                id = group.id,
                name = group.name,
                icon = group.icon?.takeIf { it.isNotBlank() } ?: GroupRow.DEFAULT_ICON,
                color = group.color,
                createdAt = group.createdAt.takeIf { it > 0 }?.let { XtaTime.iso(Instant.ofEpochMilli(it)) } ?: rows[group.id]?.createdAt ?: XtaTime.iso(now),
                pinned = group.pinned,
                nsfw = group.nsfw,
                emoji = group.emoji,
                markStyle = group.markStyle.takeIf { it in 0..3 } ?: 0,
                parentId = session.nesting[group.id],
            )
        }
    }

    private fun sqlite(millis: Long): String = XtaTime.sqlite(Instant.ofEpochMilli(millis))
}
