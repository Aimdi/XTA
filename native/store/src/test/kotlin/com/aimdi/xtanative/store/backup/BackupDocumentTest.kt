package com.aimdi.xtanative.store.backup

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive

/** The fixtures are XTA's own output: its `SettingsData.toJson()` of one row in every section, and of an empty document. */
class BackupDocumentTest {
    private val full = resource("/backup/xta_full.json").trim()
    private val empty = resource("/backup/xta_empty.json").trim()

    @Test
    fun xtasBackupGoesBackOutByteForByte() {
        assertEquals(full, BackupDocument.parse(full)!!.encode())
        assertEquals(empty, BackupDocument.parse(empty)!!.encode())
    }

    @Test
    fun anEmptyDocumentIsWhatXtaWritesWithExactlyTheKeysOlderBuildsRead() {
        val written = BackupDocument(exportedAt = "2026-01-01T00:00:00.000Z", appVersion = "test")
        assertEquals(empty, written.encode())
        assertEquals(
            listOf(
                "archiveAnnotations", "formatVersion", "exportedAt", "appVersion", "settings",
                "threadsSubscriptions", "threadsLocalLikes", "blueskySubscriptions", "blueskyLocalLikes", "mastodonSubscriptions",
                "tiktokSubscriptions", "instagramSubscriptions", "redditSubscriptions", "redditLocalVotes", "substackSubscriptions",
                "rssSubscriptions", "booruSubscriptions", "ehFavorites", "ehHistory", "stockSubscriptions",
                "searchSubscriptions", "subscriptions", "subscriptionGroups", "subscriptionGroupMembers", "searchGroupMembers",
                "tweets", "savedTweetFolders", "likedTweets", "retweetFilters", "replyFilters", "feedReadPositions",
                "accounts", "profileNotes", "antennas", "localPosts",
            ),
            written.toJson().keys.toList(),
        )
    }

    @Test
    fun everySectionIsCarried() {
        val backup = BackupDocument.parse(full)!!
        assertEquals("dart", backup.searchSubscriptions!!.single().query)
        val (reader, other) = backup.subscriptions!!
        assertEquals(UserSubscriptionRow("1", "reader", "Reader", "https://pbs.twimg.com/a_normal.jpg", true, "2026-01-02 03:04:05", true, 3), reader)
        assertTrue(!other.verified && !other.inFeed && other.maxPostsPerLoad == null)
        assertEquals(ProfileSubscriptionRow("reader", "Reader", "https://t/a.jpg", true, "2026-01-02 03:04:05"), backup.threadsSubscriptions!!.single())
        assertFalse(backup.blueskySubscriptions!!.single().inFeed)
        assertEquals("reader@example.social", backup.mastodonSubscriptions!!.single().id)
        assertEquals("MS4wLj", backup.tiktokSubscriptions!!.single().secUid)
        assertEquals("42", backup.instagramSubscriptions!!.single().pk)
        assertEquals("dartlang", backup.redditSubscriptions!!.single().name)
        assertEquals("abc123", backup.redditLocalVotes!!.single().id)
        assertEquals("t_like_1", backup.threadsLocalLikes!!.single().id)
        assertEquals("at://did:plc:a/app.bsky.feed.post/1", backup.blueskyLocalLikes!!.single().id)
        assertEquals("https://astral.substack.com", backup.substackSubscriptions!!.single().baseUrl)
        assertEquals("https://example.org/feed.xml", backup.rssSubscriptions!!.single().feedUrl)
        assertEquals("landscape", backup.booruSubscriptions!!.single().id)
        assertEquals(EhGalleryRow(2345, "abc0", "Title", null, "Non-H", "https://e/t.jpg", "someone", 24, 4.5, "2026-01-02T03:04:05.000Z"), backup.ehFavorites!!.single().gallery)
        assertEquals(3, backup.ehHistory!!.single().lastPage)
        assertEquals("AAPL", backup.stockSubscriptions!!.single().symbol)
        assertEquals(GroupRow("g1", "Feeds", """{"pack":"custom","key":"rss_feed"}""", 0xFF2196F3, "2026-01-02T03:04:05.000Z", true, false, "🚀", 1, "g0"), backup.groups!!.single())
        assertEquals(GroupMemberRow("g1", "1"), backup.groupMembers!!.single())
        assertEquals(SearchGroupMemberRow("g1", "dart"), backup.searchGroupMembers!!.single())
        assertEquals(SavedPostRow("t1", "1", """{"id_str":"t1"}""", "f1", "later"), backup.savedPosts!!.single())
        assertEquals(SavedFolderRow("f1", "Later", 2, "2026-01-02T03:04:05.000Z", autoDownload = true), backup.savedFolders!!.single())
        assertEquals("t2", backup.likedPosts!!.single().id)
        assertEquals("reader", backup.repostFilters!!.single().screenName)
        assertEquals("2", backup.replyFilters!!.single().userId)
        assertEquals("2026-08-01T00:00:00.000Z", backup.readPositions!!.single().chainCreatedAt)
        assertEquals("met at a conference", backup.profileNotes!!.single().note)
        assertEquals(listOf("flutter", "dart lang"), backup.antennas!!.first().includeTerms)
        assertEquals(AntennaRow.FOLLOWING, backup.antennas!!.first().scope)
        val note = backup.localPosts!!.single()
        assertEquals(listOf(LocalPostMediaRow("m1", "photo.jpg", "image/jpeg", "AAEC")), note.media)
        assertEquals("n0", note.inReplyToId)
        assertEquals(ArchiveAnnotationRow(mapOf("Quotation" to "My note"), listOf("science", "read"), "Image text"), backup.archiveAnnotations!!["archive-note:article-1"])
        assertEquals(JsonPrimitive(1.2), backup.settings!!["accessibility.text_scale_factor"])
    }

    @Test
    fun theHeaderSaysWhichFormatWroteItFromWhichBuildAndWhen() {
        val backup = BackupDocument.parse(full)!!
        assertEquals(BackupDocument.FORMAT_VERSION, backup.formatVersion)
        assertEquals("4.12.0+400001040", backup.appVersion)
        assertEquals(Instant.parse("2026-08-04T09:30:00Z"), XtaTime.parse(backup.exportedAt))
        assertTrue(backup.isSupported)
    }

    @Test
    fun theSessionsReadFromTheHeadersXtaStored() {
        val account = BackupDocument.parse(full)!!.accounts!!.single()
        assertEquals("ct0value", account.id)
        assertEquals(mapOf("Cookie" to "auth_token=a;ct0=ct0value", "x-csrf-token" to "ct0value"), account.headers)
        assertEquals(1, account.consecutiveNotFound)
        val asObject = BackupDocument.parse("""{"accounts":[{"id":"a","auth_header":{"cookie":"c"}}]}""")!!.accounts!!.single()
        assertEquals(mapOf("cookie" to "c"), asObject.headers, "an older file kept the headers as an object")
    }

    @Test
    fun theCountsAreXtasPreviewInItsOrder() {
        // backupCounts of the same document, dumped from XTA.
        val xta = """{"archiveAnnotations":1,"settings":9,"subscriptions":3,"groups":1,"groupMembers":2,"savedPosts":1,"folders":1,"likedPosts":1,"filters":2,"readPositions":1,"accounts":1,"profileNotes":1,"antennas":2,"localPosts":1,"threads":1,"threadsLikes":1,"bluesky":1,"blueskyLikes":1,"mastodon":1,"tiktokSubscriptions":1,"instagramSubscriptions":1,"subreddits":1,"upvotes":1,"substack":1,"rss":1,"booruTags":1,"ehFavorites":1,"ehHistory":1,"stocks":1}"""
        val counts = BackupDocument.parse(full)!!.counts()
        assertEquals(xta, counts.entries.joinToString(",", "{", "}") { "\"${it.key.xtaName}\":${it.value}" })
    }

    @Test
    fun aFileThatCarriesNothingRestoresNothing() {
        assertTrue(BackupDocument.parse("{}")!!.isEmpty)
        assertTrue(BackupDocument().isEmpty)
        assertTrue(BackupDocument(savedPosts = emptyList(), accounts = emptyList()).isEmpty, "empty sections count for nothing")
        assertTrue(BackupDocument.parse(empty)!!.isEmpty)
    }

    @Test
    fun whatIsNoBackupDocumentIsRefused() {
        assertNull(BackupDocument.parse("not json"))
        assertNull(BackupDocument.parse("[1, 2]"))
    }

    @Test
    fun anEmptySectionIsNotAMissingOne() {
        val missing = BackupDocument.parse("{}")!!
        val emptySection = BackupDocument.parse("""{"redditSubscriptions": []}""")!!
        assertNull(missing.redditSubscriptions)
        assertEquals(emptyList(), emptySection.redditSubscriptions)
    }

    @Test
    fun aHeaderlessFileIsTheLegacyVersionAndAFutureOneIsRefused() {
        assertEquals(BackupDocument.LEGACY_FORMAT_VERSION, BackupDocument.parse("{}")!!.formatVersion)
        assertTrue(BackupDocument.isSupported(BackupDocument.LEGACY_FORMAT_VERSION))
        assertTrue(BackupDocument.isSupported(BackupDocument.FORMAT_VERSION))
        assertFalse(BackupDocument.parse("""{"formatVersion": 3}""")!!.isSupported)
    }

    @Test
    fun aLegacyFileImportsTheSectionsItHasAndNothingElse() {
        // XTA's own legacy fixture (settings_backup_test.dart): no header, and none of the sections added since.
        val legacy = BackupDocument.parse(
            """
            {
              "settings": {"theme": "dark"},
              "subscriptions": [{"id": "1", "screen_name": "reader", "name": "Reader", "verified": 1, "created_at": "2026-01-02 03:04:05", "in_feed": 1}],
              "tweets": [{"id": "t1", "content": "{}", "user_id": "1"}]
            }
            """.trimIndent(),
        )!!
        assertEquals(JsonPrimitive("dark"), legacy.settings!!["theme"])
        assertEquals("reader", legacy.subscriptions!!.single().screenName)
        assertEquals("t1", legacy.savedPosts!!.single().id)
        assertNull(legacy.substackSubscriptions)
        assertNull(legacy.repostFilters)
        assertNull(legacy.readPositions)
        assertNull(legacy.appVersion)
        assertNull(legacy.exportedAt)
        assertEquals(setOf(BackupCategory.SETTINGS, BackupCategory.SUBSCRIPTIONS, BackupCategory.SAVED_POSTS), legacy.counts().keys)
    }

    @Test
    fun aRowXtaCouldNotReadIsLeftOutAndTheRestStillReads() {
        val backup = BackupDocument.parse(
            """{"subscriptions": [{"id": "1", "screen_name": "a", "name": "A"}, {"id": "2", "name": "no handle"}, "nonsense"],
               "savedTweetFolders": [{"id": "f", "name": "No date"}]}""",
        )!!
        assertEquals(listOf("1"), backup.subscriptions!!.map { it.id })
        assertEquals(emptyList(), backup.savedFolders, "a folder XTA cannot date is no folder")
    }

    @Test
    fun aFollowedAccountWithoutInFeedIsInTheFeedAsTheColumnDefaultsUnlikeXtasImport() {
        val row = BackupDocument.parse("""{"subscriptions": [{"id": "1", "screen_name": "a", "name": "A"}], "redditSubscriptions": [{"id": "r", "name": "r"}]}""")!!
        assertTrue(row.subscriptions!!.single().inFeed)
        assertTrue(row.redditSubscriptions!!.single().inFeed)
    }

    @Test
    fun aGroupFromBeforeIconPacksGetsTheDefaultIcon() {
        val groups = BackupDocument.parse(
            """{"subscriptionGroups": [{"id": "g", "name": "G", "icon": "rss", "created_at": "2020-01-01T00:00:00.000", "mark_style": 9}]}""",
        )!!.groups!!
        assertEquals(GroupRow.DEFAULT_ICON, groups.single().icon)
        assertEquals(0, groups.single().markStyle, "a mark style XTA does not know is its automatic one")
    }

    @Test
    fun aLocalPostsFilesComeFromEitherFormAndNeedAnId() {
        val note = BackupDocument.parse(
            """{"localPosts": [{"id": "n", "media_json": "[{\"id\":\"m\",\"name\":\"a.png\",\"mime\":\"image/png\"},{\"id\":\"\"}]", "in_reply_to_id": ""}]}""",
        )!!.localPosts!!.single()
        assertEquals(listOf(LocalPostMediaRow("m", "a.png", "image/png")), note.media)
        assertNull(note.inReplyToId, "an empty parent is no parent")
        assertEquals("""[{"id":"m","name":"a.png","mime":"image/png"}]""", (note.toJson()["media_json"] as JsonPrimitive).content)
    }

    @Test
    fun onlyArchiveNotesAreCarriedAndTheirTagsOnce() {
        val notes = BackupDocument.parse(
            """{"archiveAnnotations": {"archive-note:1": {"highlights": {"q": "n", "bad": 3}, "tags": ["a", "a", 2], "extracted": "x"}, "profile:id:1": {"private": "cache"}, "archive-note:invalid": 7}}""",
        )!!.archiveAnnotations!!
        assertEquals(mapOf("archive-note:1" to ArchiveAnnotationRow(mapOf("q" to "n"), listOf("a"), "x")), notes)
        assertNull(BackupDocument.parse("""{"archiveAnnotations": null}""")!!.archiveAnnotations, "XTA writes null when they were not exported")
    }

    @Test
    fun anAntennasTermsSplitAsXtasImportSplitsThem() {
        val antenna = BackupDocument.parse("""{"antennas": [{"id": "a", "name": "A", "include_terms": "a, b\nc,,", "exclude_terms": null, "scope": "odd"}]}""")!!.antennas!!.single()
        assertEquals(listOf("a", "b", "c"), antenna.includeTerms)
        assertEquals(emptyList(), antenna.excludeTerms)
        assertEquals(AntennaRow.SEARCH, antenna.scope)
        assertEquals("a, b, c", (antenna.toJson()["include_terms"] as JsonPrimitive).content)
    }

    @Test
    fun anExportCarriesWhatTheReaderChose() {
        val everything = BackupDocument.parse(full)!!
        val subscriptions = everything.chosen(ExportChoice(subscriptions = true, groupMembers = true))
        assertNotNull(subscriptions.subscriptions)
        assertNotNull(subscriptions.blueskyLocalLikes, "device-only likes go with the follows")
        assertNotNull(subscriptions.ehHistory)
        assertNull(subscriptions.groups)
        assertNull(subscriptions.groupMembers, "members need both subscriptions and groups")
        assertNull(subscriptions.accounts)
        assertNotNull(subscriptions.localPosts, "the reader's own notes always go")
        assertNotNull(subscriptions.settings, "the settings are the settings' own business")
        val filters = everything.chosen(ExportChoice(filters = true))
        assertNotNull(filters.profileNotes)
        assertNotNull(filters.antennas)
        assertNull(filters.subscriptions)
        val saved = everything.chosen(ExportChoice(savedPosts = true))
        assertNotNull(saved.archiveAnnotations, "archive notes go with the saved posts")
        assertNull(saved.savedFolders)
        val synced = everything.chosen(ExportChoice.everything(includeAccounts = false))
        assertNull(synced.accounts)
        assertEquals(everything.copy(accounts = null), synced)
        assertTrue(ExportChoice().isEmpty)
    }

    @Test
    fun readingPositionsAreRestoredOnlyWhenAskedFor() {
        val everything = BackupDocument.parse(full)!!
        assertNull(everything.forRestore(includeReadPositions = false).readPositions)
        assertNotNull(everything.forRestore(includeReadPositions = true).readPositions)
    }

    @Test
    fun aWebDavDocumentKeepsItsVersionsLast() {
        val file = "1754300000000000-" + "a".repeat(64) + ".json"
        val synced = BackupDocument.parse(
            """{"tweets": [], "_xtaWebDavHistory": [{"file": "$file", "at": "2026-08-04T09:30:00.000Z"}, {"file": "../etc/passwd", "at": "2026-08-04T09:30:00.000Z"}, {"file": "$file", "at": "never"}]}""",
        )!!
        assertEquals(listOf(WebDavVersion(file, "2026-08-04T09:30:00.000Z")), synced.webDavHistory)
        assertEquals("_xtaWebDavHistory", synced.toJson().keys.last())
        assertFalse("_xtaWebDavHistory" in BackupDocument.parse(full)!!.toJson(), "a file export has none")
    }

    @Test
    fun thePluginsSectionsGoOutAndComeBackAndXtaNeedNotKnowThem() {
        val sections = mapOf("bluesky.follows" to """{"v":1,"follows":[]}""", "reddit.votes" to "up")
        val written = BackupDocument(localPosts = emptyList(), pluginSections = sections)
        val read = BackupDocument.parse(written.encode())!!
        assertEquals(sections, read.pluginSections)
        assertEquals(mapOf(BackupCategory.PLUGINS to 2), read.counts())
        assertFalse(read.isEmpty, "a file with only plugins' sections still restores them")
        assertNull(BackupDocument.parse(full)!!.pluginSections, "XTA writes none")
        assertFalse(BackupKeys.PLUGIN_SECTIONS in BackupDocument().toJson(), "a document without them is XTA's to the byte")
        assertNull(BackupDocument.parse("""{"pluginSections": {"a": 1, "b": "text"}}""")!!.pluginSections!!["a"], "only texts")
        assertEquals(sections, written.chosen(ExportChoice(subscriptions = true)).pluginSections, "they go with the follows")
        assertNull(written.chosen(ExportChoice(savedPosts = true)).pluginSections)
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)) { path }.readText()
}
