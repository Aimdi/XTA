package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.session.SavedSearch
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.antenna.AntennaScope
import com.aimdi.xtanative.store.backup.AccountRow
import com.aimdi.xtanative.store.backup.AntennaRow
import com.aimdi.xtanative.store.backup.ArchiveAnnotationRow
import com.aimdi.xtanative.store.backup.BackupCategory
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.ExportChoice
import com.aimdi.xtanative.store.backup.FeedFilterRow
import com.aimdi.xtanative.store.backup.GroupMemberRow
import com.aimdi.xtanative.store.backup.GroupRow
import com.aimdi.xtanative.store.backup.LikedPostRow
import com.aimdi.xtanative.store.backup.LocalMarkRow
import com.aimdi.xtanative.store.backup.LocalPostMediaRow
import com.aimdi.xtanative.store.backup.LocalPostRow
import com.aimdi.xtanative.store.backup.ProfileNoteRow
import com.aimdi.xtanative.store.backup.ProfileSubscriptionRow
import com.aimdi.xtanative.store.backup.ReadPositionRow
import com.aimdi.xtanative.store.backup.SavedFolderRow
import com.aimdi.xtanative.store.backup.SavedPostRow
import com.aimdi.xtanative.store.backup.SearchGroupMemberRow
import com.aimdi.xtanative.store.backup.SearchSubscriptionRow
import com.aimdi.xtanative.store.backup.UserSubscriptionRow
import com.aimdi.xtanative.store.reading.ArticleReadPoint
import com.aimdi.xtanative.store.saved.ProfileNotesStore
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedPost
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.ReadingPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThemeBackground
import com.aimdi.xtanative.store.settings.ThemePrefs
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

private const val HEADERS = """{"Cookie":"auth_token=a;ct0=c1","x-csrf-token":"c1"}"""

/** One row in most sections, as a file XTA exported would carry them. */
private fun backup() = BackupDocument(
    exportedAt = "2026-08-04T09:30:00.000Z",
    appVersion = "4.12.0+400001040",
    archiveAnnotations = mapOf("archive-note:a1" to ArchiveAnnotationRow(mapOf("Quote" to "Note"), listOf("read"))),
    settings = mapOf(
        ThemePrefs.background.key to JsonPrimitive("dim"),
        PostsPrefs.includeReplies.key to JsonPrimitive(false),
        HomePrefs.followingHiddenGroupIds.key to JsonPrimitive("""["g1","missing"]"""),
        HomePrefs.deckGroupIds.key to JsonPrimitive("g1, missing"),
        ReadingPrefs.articles.key to JsonPrimitive("""{"art":{"fraction":0.5,"paragraph":3,"leading":0,"completed":false,"updatedAt":7}}"""),
        "ai.api_key" to JsonPrimitive("never restored"),
    ),
    threadsSubscriptions = listOf(ProfileSubscriptionRow("reader", "Reader")),
    redditLocalVotes = listOf(LocalMarkRow("v1")),
    searchSubscriptions = listOf(SearchSubscriptionRow("dart", "2026-01-02 03:04:05")),
    subscriptions = listOf(
        UserSubscriptionRow("1", "reader", "Reader", createdAt = "2026-01-02 03:04:05", maxPostsPerLoad = 3),
        UserSubscriptionRow("2", "other", "Other", inFeed = false),
    ),
    groups = listOf(
        GroupRow("-1", "Everyone", createdAt = "2026-01-02T03:04:05.000Z"),
        GroupRow("g0", "Parent", createdAt = "2026-01-02T03:04:05.000Z"),
        GroupRow("g1", "Feeds", color = 0xFF2196F3, createdAt = "2026-01-02T03:04:05.000Z", pinned = true, emoji = "🚀", markStyle = 1, parentId = "g0"),
    ),
    groupMembers = listOf(GroupMemberRow("g1", "1")),
    searchGroupMembers = listOf(SearchGroupMemberRow("g1", "dart")),
    savedPosts = listOf(SavedPostRow("t1", "1", """{"id_str":"t1"}""", "f1", "later"), SavedPostRow("t2", "1", """{"id_str":"t2"}""")),
    savedFolders = listOf(SavedFolderRow("f1", "Later", 2, "2026-01-02T03:04:05.000Z", autoDownload = true)),
    likedPosts = listOf(LikedPostRow("t3", "1", "{}")),
    repostFilters = listOf(FeedFilterRow("1", "reader")),
    replyFilters = listOf(FeedFilterRow("2", "Other")),
    profileNotes = listOf(ProfileNoteRow("1", "met at a conference", "2026-01-02T03:04:05.000Z")),
    readPositions = listOf(ReadPositionRow("g1", "c1"), ReadPositionRow("-1", "c0"), ReadPositionRow("nowhere", "c9")),
    accounts = listOf(AccountRow("c1", JsonPrimitive(HEADERS), "reader"), AccountRow("nocookie", JsonPrimitive("""{"x":"y"}"""))),
    antennas = listOf(AntennaRow("a1", "Tech", listOf("dart"), scope = AntennaRow.FOLLOWING, createdAt = "2026-01-02T03:04:05.000Z")),
    localPosts = listOf(LocalPostRow("n1", "stays", "2026-01-02T03:04:05.000Z", media = listOf(LocalPostMediaRow("m1", "a.jpg", "image/jpeg", "AAEC")))),
)

class BackupReadTest {
    @Test
    fun aFileIsABackupXtaCanRestoreOrSaysWhyNot() {
        assertEquals(BackupRead.NotABackup, BackupRead.of("not json"))
        assertEquals(BackupRead.NotABackup, BackupRead.of(null))
        assertEquals(BackupRead.Unsupported, BackupRead.of("""{"formatVersion":99,"subscriptions":[]}"""))
        assertEquals(BackupRead.Empty, BackupRead.of("""{"formatVersion":2,"subscriptions":[]}"""))
        assertIs<BackupRead.Ready>(BackupRead.of(backup().encode()))
    }
}

class BackupRestoreTest {
    private val session = Session(
        accounts = listOf(XAccount("c1", "old", mapOf("Cookie" to "x")), XAccount("c2", "kept", mapOf("Cookie" to "y"))),
        following = listOf(FollowedAccount("1", "old", "Old", null, false, true, 5), FollowedAccount("9", "mine", "Mine", null, false, true, 6)),
        groups = listOf(FollowGroup("g1", "Old name", listOf("9"))),
        searches = listOf(SavedSearch("kotlin", 1)),
        postCaps = mapOf("2" to 4),
    )

    @Test
    fun rowsReplaceTheOnesWithTheirKeyAndEveryOtherStays() {
        val restored = BackupRestore.session(session, backup())
        assertEquals(listOf("c2", "c1"), restored.accounts.map { it.id })
        assertEquals("reader", restored.accounts.last().screenName)
        assertEquals(listOf("9", "1", "2"), restored.following.map { it.id })
        assertFalse(restored.following.last().inFeed)
        assertEquals(mapOf("1" to 3), restored.postCaps)
        assertEquals(listOf("kotlin", "dart"), restored.searches.map { it.query })
    }

    @Test
    fun groupsKeepTheirMembersAndGainTheBackupsAndTheirNesting() {
        val restored = BackupRestore.session(session, backup())
        assertEquals(listOf("g1", "g0"), restored.groups.map { it.id }.sorted().reversed())
        val feeds = restored.groups.first { it.id == "g1" }
        assertEquals("Feeds", feeds.name)
        assertEquals(listOf("9", "1", "dart"), feeds.memberIds)
        assertTrue(feeds.pinned)
        assertEquals("🚀", feeds.emoji)
        assertEquals("g0", restored.nesting["g1"])
        assertTrue(restored.groups.none { it.id == GroupRow.EVERYONE })
        assertEquals(setOf("g1"), restored.offFollowing)
        assertEquals(listOf("g1"), restored.deck)
    }

    @Test
    fun aSectionTheFileDoesNotCarryChangesNothing() {
        assertEquals(session, BackupRestore.session(session, BackupDocument()))
        assertNull(BackupRestore.library(SavedLibrary(), BackupDocument(), 0))
        assertNull(BackupRestore.likes(emptyList(), BackupDocument(), 0))
        assertNull(BackupRestore.antennas(BackupDocument(), 0))
        assertNull(BackupRestore.journal(BackupDocument()))
    }

    @Test
    fun savedPostsNewToTheDeviceComeInTheFilesOrderAndKnownOnesKeepTheirPlace() {
        val library = SavedLibrary(listOf(SavedPost("t2", null, null, savedAt = 50), SavedPost("t0", null, null, savedAt = 40)))
        val restored = BackupRestore.library(library, backup(), now = 1_000)!!
        assertEquals(listOf("t1", "t2", "t0"), restored.posts.map { it.id })
        assertEquals(50, restored.posts.first { it.id == "t2" }.savedAt)
        assertEquals("f1", restored.posts.first().folderId)
        assertEquals("later", restored.posts.first().note)
        assertTrue(restored.folders.single().autoDownload)
        assertEquals("t3", BackupRestore.likes(emptyList(), backup(), 1_000)!!.single().id)
    }

    @Test
    fun notesArticleNotesAndAntennasComeBack() {
        val note = BackupRestore.localPosts(emptyList(), backup(), 1_000)!!.single()
        assertEquals("AAEC", note.media.single().data)
        assertEquals(listOf("read"), BackupRestore.annotations(backup())!!["a1"]!!.tags.toList())
        val antenna = BackupRestore.antennas(backup(), 1_000)!!.single()
        assertEquals(AntennaScope.FOLLOWING, antenna.scope)
        assertEquals(Instant.parse("2026-01-02T03:04:05Z").toEpochMilli(), antenna.createdAt)
    }

    @Test
    fun readingPositionsGoToTheFeedsThisDeviceHas() {
        val places = BackupRestore.readPlaces(backup(), setOf("g1"))
        assertEquals(mapOf(null to FeedPlace("c0", 0), "g1" to FeedPlace("c1", 0)), places)
        assertEquals(ArticleReadPoint(0.5, 3, 0.0, false, 7), BackupRestore.journal(backup())!!["art"])
    }
}

class KeptSectionsTest {
    @Test
    fun aRowReplacesTheOneWithItsKeyAndGoesLast() {
        val kept = BackupDocument(threadsSubscriptions = listOf(ProfileSubscriptionRow("a", "A"), ProfileSubscriptionRow("b", "B")))
        val merged = KeptSections.merged(kept, BackupDocument(threadsSubscriptions = listOf(ProfileSubscriptionRow("a", "A2"))))
        assertEquals(listOf("b" to "B", "a" to "A2"), merged.threadsSubscriptions!!.map { it.id to it.name })
        assertEquals(kept.threadsSubscriptions, KeptSections.merged(kept, BackupDocument()).threadsSubscriptions)
    }

    @Test
    fun theyAreKeptAcrossLaunches(): Unit = runBlocking {
        val dir = Files.createTempDirectory("kept").toFile()
        try {
            KeptSections(StoreDir(dir)).merge(KeptSections.keptOf(backup()))
            val read = KeptSections(StoreDir(dir)).get()
            assertEquals("reader", read.threadsSubscriptions!!.single().id)
            assertEquals("v1", read.redditLocalVotes!!.single().id)
            assertNull(read.repostFilters, "the profiles' filters and notes have their own stores")
            assertNull(read.profileNotes)
            assertNull(read.subscriptions)
        } finally {
            dir.deleteRecursively()
        }
    }
}

class BackupExportTest {
    @Test
    fun theFileIsNamedAsXtaNamesIt() {
        assertEquals("xta-2026-11-03.json", BackupExport.fileName(LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun onlyTheChosenSectionsAreWrittenAndNotesAlwaysAre() {
        val content = DeviceContent(
            session = BackupRestore.session(Session(), backup()),
            settings = Settings().restoring(backup().settings!!),
            kept = KeptSections.keptOf(backup()),
        )
        val now = Instant.parse("2026-11-03T10:00:00Z")
        val subscriptions = BackupExport.document(content, ExportChoice(subscriptions = true), "1.0", now)
        assertEquals(listOf("1", "2"), subscriptions.subscriptions!!.map { it.id })
        assertEquals(3, subscriptions.subscriptions!!.first().maxPostsPerLoad)
        assertEquals("reader", subscriptions.threadsSubscriptions!!.single().id)
        assertNull(subscriptions.groups)
        assertNull(subscriptions.accounts)
        // Without the settings, only the preferences that hold followed accounts go along.
        assertEquals(setOf("plugin.hackernews.follows", "plugin.websources.follows", "plugin.pixiv.group_subscriptions"), subscriptions.settings!!.keys)
        assertNotNull(subscriptions.localPosts)
        assertEquals("2026-11-03T10:00:00.000Z", subscriptions.exportedAt)
    }

    @Test
    fun groupsGoOutWithWhatTheirRowsCarried() {
        val content = DeviceContent(session = BackupRestore.session(Session(), backup()), kept = KeptSections.keptOf(backup()))
        val document = BackupExport.document(content, ExportChoice(subscriptions = true, groups = true, groupMembers = true), "1.0", Instant.EPOCH)
        val feeds = document.groups!!.first { it.id == "g1" }
        assertEquals(0xFF2196F3, feeds.color)
        assertEquals(1, feeds.markStyle)
        assertEquals("g0", feeds.parentId)
        assertEquals(listOf(GroupMemberRow("g1", "1"), GroupMemberRow("g1", "dart")), document.groupMembers)
    }

    @Test
    fun theSettingsCarryWhatXtaKeepsInPreferencesAndNoSecret() {
        val session = Session(groups = listOf(FollowGroup("g1", "Feeds", emptyList())), offFollowing = setOf("g1"), deck = listOf("g1"))
        val content = DeviceContent(session = session, settings = Settings().restoring(backup().settings!!), journal = mapOf("art" to ArticleReadPoint(0.25)))
        val section = BackupExport.settings(content, ExportChoice(settings = true))!!
        assertEquals(JsonPrimitive("dim"), section[ThemePrefs.background.key])
        assertEquals(JsonPrimitive("""["g1"]"""), section[HomePrefs.followingHiddenGroupIds.key])
        assertEquals(JsonPrimitive("g1"), section[HomePrefs.deckGroupIds.key])
        assertTrue((section[ReadingPrefs.articles.key] as JsonPrimitive).content.contains("\"art\""))
        assertFalse("ai.api_key" in section)
    }

    @Test
    fun aBackupGoesOutAndComesBackTheSame() {
        val session = BackupRestore.session(Session(), backup())
        val content = DeviceContent(session = session, kept = KeptSections.keptOf(backup()))
        val document = BackupExport.document(content, ExportChoice.everything(includeAccounts = true), "1.0", Instant.EPOCH)
        val again = BackupRestore.session(Session(), BackupDocument.parse(document.encode())!!)
        assertEquals(session.following.map { it.copy(avatarUrl = null) }, again.following.map { it.copy(avatarUrl = null) })
        assertEquals(session.accounts, again.accounts)
        assertEquals(session.searches, again.searches)
        assertEquals(session.groups.map { it.id to it.memberIds }.toSet(), again.groups.map { it.id to it.memberIds }.toSet())
    }
}

class DeviceBackupTest {
    private val dir: File = Files.createTempDirectory("graph").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aRestoreWritesEverySectionIntoItsStore(): Unit = runBlocking {
        val graph = AppGraph(dir, flowOf(true), "test")
        val now = Instant.parse("2026-11-03T10:00:00Z")
        DeviceBackup(graph) { now }.restore(backup(), includeReadPositions = true)

        val settings = graph.settings.load()
        assertEquals(ThemeBackground.DIM, settings[ThemePrefs.background])
        assertFalse(settings.isSet(com.aimdi.xtanative.store.settings.AiPrefs.apiKey))
        val session = graph.sessions.state.value!!
        assertFalse(session.includeReplies)
        assertEquals(listOf("c1"), session.accounts.map { it.id })
        assertEquals(listOf("1", "2"), session.following.map { it.id })
        assertEquals(listOf("t1", "t2"), graph.saved.library().posts.map { it.id })
        assertEquals("t3", graph.likes.likes().single().id)
        assertEquals("stays", graph.localPosts.notes().single().body)
        assertTrue(graph.localPosts.mediaFile("n1", "m1").isFile)
        assertEquals("Tech", graph.antennas.load().single().name)
        assertEquals(listOf("read"), graph.archiveNotes.of("a1").tags.toList())
        assertEquals(0.5, graph.readingJournal.point("art")!!.fraction)
        assertEquals("v1", graph.kept.get().redditLocalVotes!!.single().id)
        assertEquals(FeedPlace("c1", 0), graph.feedCache("g1").readPlace())
        assertEquals(FeedPlace("c0", 0), graph.feedCache(null).readPlace())
        assertEquals("met at a conference", graph.profileNotes.of("1")?.note)
        assertTrue(graph.profileFilters.filters().repostsHidden("1"))
        assertEquals(setOf("other"), graph.profileFilters.filters().hiddenReplyHandles)

        val exported = BackupDocument.parse(DeviceBackup(graph) { now }.export(ExportChoice.everything(includeAccounts = false)))!!
        val counts = exported.counts()
        assertEquals(2, counts[BackupCategory.SUBSCRIPTIONS]?.minus(1))
        assertEquals(2, counts[BackupCategory.SAVED_POSTS])
        assertEquals(1, counts[BackupCategory.THREADS])
        assertEquals(1, counts[BackupCategory.UPVOTES])
        assertNull(counts[BackupCategory.ACCOUNTS])
        assertEquals(2, counts[BackupCategory.READ_POSITIONS])
        assertEquals(2, counts[BackupCategory.FILTERS])
        assertEquals(1, counts[BackupCategory.PROFILE_NOTES])
        assertEquals("AAEC", exported.localPosts!!.single().media.single().data)
    }

    @Test
    fun profileNotesAndFiltersGoOutAndComeBackThroughTheirStores(): Unit = runBlocking {
        val graph = AppGraph(dir, flowOf(true), "test")
        graph.profileNotes.set("42", "met at a conference")
        graph.profileFilters.setRepostsHidden("42", "Tommy", true)
        graph.profileFilters.setRepliesHidden("7", "Maya", true)
        val exported = BackupDocument.parse(DeviceBackup(graph).export(ExportChoice(filters = true)))!!
        assertEquals(listOf("42"), exported.profileNotes!!.map { it.profileId })
        assertEquals(listOf(FeedFilterRow("42", "tommy")), exported.repostFilters)
        assertEquals(listOf(FeedFilterRow("7", "maya")), exported.replyFilters)

        val other = AppGraph(File(dir, "other"), flowOf(true), "test")
        other.profileNotes.set("9", "stays")
        DeviceBackup(other).restore(exported, includeReadPositions = false)
        assertEquals("met at a conference", other.profileNotes.of("42")?.note)
        assertEquals("stays", other.profileNotes.of("9")?.note, "a note replaces the one on its profile, as XTA's import does")
        assertTrue(other.profileFilters.filters().repostsHidden("42"))
        assertTrue(other.profileFilters.filters().repliesHidden("7"))
    }

    @Test
    fun theProfileRowsAnOlderBuildKeptGoToTheirStoresOnce(): Unit = runBlocking {
        val kept = File(dir, "store/" + KeptSections.FILE)
        kept.parentFile.mkdirs()
        kept.writeText(
            BackupDocument(
                profileNotes = listOf(ProfileNoteRow("1", "old note")),
                repostFilters = listOf(FeedFilterRow("1", "Reader")),
                threadsSubscriptions = listOf(ProfileSubscriptionRow("t", "T")),
            ).encode(),
        )
        val graph = AppGraph(dir, flowOf(true), "test")
        DeviceBackup(graph).adoptKeptProfiles()
        assertEquals("old note", graph.profileNotes.of("1")?.note)
        assertTrue(graph.profileFilters.filters().repostsHidden("1"))

        graph.profileNotes.set("1", "newer")
        DeviceBackup(AppGraph(dir, flowOf(true), "test")).adoptKeptProfiles()
        assertEquals("newer", ProfileNotesStore(StoreDir(File(dir, "store"))).of("1")?.note, "taken once, never again over what came after")
        val left = BackupDocument.parse(kept.readText())!!
        assertNull(left.profileNotes)
        assertEquals("t", left.threadsSubscriptions!!.single().id, "the rest stays kept")
    }

    /** A plugin's backup section, keeping its text in memory. */
    private class FakeSection(name: String, var text: String? = null, failing: Boolean = false) {
        val section = PluginSection(name, export = { text }, import = {
            if (failing) error("broken")
            text = it
        })
    }

    @Test
    fun eachRunningPluginTakesItsSectionsAndTheOthersWaitForTheirs(): Unit = runBlocking {
        val graph = AppGraph(dir, flowOf(true), "test")
        val bluesky = FakeSection("bluesky.follows", "mine")
        graph.pluginBackups.attach(listOf(bluesky.section))
        val restored = BackupDocument(pluginSections = mapOf("bluesky.follows" to "restored", "reddit.votes" to "up"))
        DeviceBackup(graph).restore(restored, includeReadPositions = false)
        assertEquals("restored", bluesky.text)
        assertEquals(mapOf("reddit.votes" to "up"), graph.kept.get().pluginSections, "kept for a plugin that is not running")

        bluesky.text = "followed since"
        val exported = BackupDocument.parse(DeviceBackup(graph).export(ExportChoice(subscriptions = true)))!!
        assertEquals(mapOf("reddit.votes" to "up", "bluesky.follows" to "followed since"), exported.pluginSections)

        val reddit = FakeSection("reddit.votes")
        graph.pluginBackups.attach(listOf(bluesky.section, reddit.section))
        assertEquals("up", reddit.text, "installed, a plugin gets what was kept for it")
        assertNull(graph.kept.get().pluginSections, "which is kept no more: uninstalling erases it")
    }

    @Test
    fun aSectionAPluginCannotTakeStaysKeptAndGoesOut(): Unit = runBlocking {
        val graph = AppGraph(dir, flowOf(true), "test")
        graph.pluginBackups.attach(listOf(FakeSection("broken.data", failing = true).section))
        DeviceBackup(graph).restore(BackupDocument(pluginSections = mapOf("broken.data" to "text")), includeReadPositions = false)
        assertEquals(mapOf("broken.data" to "text"), graph.kept.get().pluginSections)
        val exported = BackupDocument.parse(DeviceBackup(graph).export(ExportChoice(subscriptions = true)))!!
        assertEquals(mapOf("broken.data" to "text"), exported.pluginSections)
    }

    @Test
    fun readingPositionsAreRestoredOnlyWhenAskedFor(): Unit = runBlocking {
        val graph = AppGraph(dir, flowOf(true), "test")
        DeviceBackup(graph).restore(backup(), includeReadPositions = false)
        assertNull(graph.feedCache("g1").readPlace())
    }
}
