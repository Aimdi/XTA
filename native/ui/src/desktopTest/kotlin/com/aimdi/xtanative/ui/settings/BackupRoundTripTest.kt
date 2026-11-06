package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedSettings
import com.aimdi.xtanative.core.feed.ReadPosition
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAccountHealth
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.ExportChoice
import com.aimdi.xtanative.store.backup.GroupRow
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.PluginStorePrefs
import com.aimdi.xtanative.ui.group.ReadMarks
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

/**
 * A backup as XTA writes it (settings_export_screen.dart, entities.dart's
 * toMap): groups with their look and a local ISO time, read positions with
 * their chain's time (For you's too), accounts with their health, one signed
 * in by a bearer alone, and the catch-up modes and Show private in the
 * preferences.
 */
private val XTA_FILE = """
{
  "formatVersion": 2,
  "exportedAt": "2026-11-05T08:00:00.000Z",
  "appVersion": "4.12.0+400001040",
  "settings": {
    "feed.catch_up_mode.g1": true,
    "feed.catch_up_mode.g2": false,
    "feed.catch_up_mode.-1": true,
    "feed.catch_up_mode.gone": true,
    "plugin_store.show_private": true
  },
  "subscriptions": [{"id": "1", "screen_name": "nasa", "name": "NASA", "verified": 0, "created_at": "2026-01-02 03:04:05", "in_feed": 1}],
  "subscriptionGroups": [
    {"id": "g1", "name": "Space", "icon": "{\"pack\":\"custom\",\"key\":\"rocket\"}", "color": 4280391411, "created_at": "2025-03-01T10:20:30.456", "pinned": 1, "nsfw": 0, "emoji": "🚀", "mark_style": 2, "parent_id": null},
    {"id": "g2", "name": "Plain", "icon": "rss", "color": null, "created_at": "2024-07-08T09:10:11.000Z", "pinned": 0, "nsfw": 1, "emoji": null, "mark_style": 0, "parent_id": "g1"}
  ],
  "subscriptionGroupMembers": [{"group_id": "g1", "profile_id": "1"}, {"group_id": "g2", "profile_id": "1"}],
  "feedReadPositions": [
    {"group_id": "g1", "chain_id": "1900000000000000001", "chain_created_at": "2026-11-04T21:00:00.000Z"},
    {"group_id": "-1", "chain_id": "1900000000000000002", "chain_created_at": "2026-11-04T22:00:00.000Z"},
    {"group_id": "for_you", "chain_id": "1900000000000000003", "chain_created_at": "2026-11-04T23:00:00.000Z"},
    {"group_id": "g2", "chain_id": "1900000000000000004", "chain_created_at": null}
  ],
  "accounts": [
    {"id": "ct0", "auth_header": "{\"Cookie\":\"auth_token=a;ct0=ct0\",\"x-csrf-token\":\"ct0\"}", "screen_name": "maya", "last_not_found_at": "2026-11-04T12:00:00.000Z", "consecutive_not_found": 3},
    {"id": "oauth", "auth_header": "{\"authorization\":\"Bearer AAA\"}", "screen_name": "app", "last_not_found_at": null, "consecutive_not_found": 1}
  ]
}
""".trimIndent()

class BackupRoundTripTest {
    private val dir: File = Files.createTempDirectory("roundtrip").toFile()
    private val now = Instant.parse("2026-11-06T10:00:00Z")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun xta(): BackupDocument = BackupDocument.parse(XTA_FILE)!!

    private suspend fun exported(graph: AppGraph): BackupDocument =
        BackupDocument.parse(DeviceBackup(graph) { now }.export(ExportChoice.everything(includeAccounts = true)))!!

    @Test
    fun whatXtaWroteComesBackOutOfXtaNativeAsXtaReadsIt(): Unit = runBlocking {
        val graph = AppGraph(dir, flowOf(true), "test")
        DeviceBackup(graph) { now }.restore(xta(), includeReadPositions = true)
        val out = exported(graph)

        val groups = out.groups!!.associateBy { it.id }
        xta().groups!!.forEach { row ->
            val back = groups.getValue(row.id)
            assertEquals(row.copy(createdAt = ""), back.copy(createdAt = ""), "the group's look and place")
            assertEquals(XtaTime.parse(row.createdAt), XtaTime.parse(back.createdAt), "made when XTA made it")
        }
        assertEquals(GroupRow.DEFAULT_ICON, groups.getValue("g2").icon, "an icon from before packs reads as XTA's default")

        val positions = out.readPositions!!.associateBy { it.groupId }
        assertEquals(setOf("g1", "following", "for_you", "g2"), positions.keys)
        xta().readPositions!!.forEach { row ->
            val back = positions.getValue(FeedKeys.readPositionKey(row.groupId))
            assertEquals(row.chainId, back.chainId)
            assertEquals(XtaTime.parse(row.chainCreatedAt), XtaTime.parse(back.chainCreatedAt))
        }

        val accounts = out.accounts!!.associateBy { it.id }
        assertEquals(setOf("ct0", "oauth"), accounts.keys, "the bearer sign-in is kept")
        assertEquals(mapOf("authorization" to "Bearer AAA"), accounts.getValue("oauth").headers)
        xta().accounts!!.forEach { row ->
            val back = accounts.getValue(row.id)
            assertEquals(row.consecutiveNotFound, back.consecutiveNotFound)
            assertEquals(XtaTime.parse(row.lastNotFoundAt), XtaTime.parse(back.lastNotFoundAt))
        }

        val settings = out.settings!!
        assertEquals(JsonPrimitive(true), settings["feed.catch_up_mode.g1"])
        assertEquals(JsonPrimitive(false), settings["feed.catch_up_mode.g2"])
        assertEquals(JsonPrimitive(true), settings["feed.catch_up_mode.-1"])
        assertNull(settings["feed.catch_up_mode.gone"], "no feed here, no key")
        assertEquals(JsonPrimitive(true), settings[PluginStorePrefs.showPrivate.key])
    }

    @Test
    fun aRestoreWritesEachFieldWhereXtaNativeReadsIt(): Unit = runBlocking {
        val graph = AppGraph(dir, flowOf(true), "test")
        DeviceBackup(graph) { now }.restore(xta(), includeReadPositions = true)

        val session = graph.sessions.state.value!!
        val space = session.groups.first { it.id == "g1" }
        assertEquals(0xFF2196F3, space.color)
        assertEquals("""{"pack":"custom","key":"rocket"}""", space.icon)
        assertEquals(2, space.markStyle)
        assertEquals(XtaTime.parse("2025-03-01T10:20:30.456")!!.toEpochMilli(), space.createdAt)
        assertTrue(session.settingsOf("g1").catchUp)
        assertFalse(session.settingsOf("g2").catchUp)
        assertTrue(session.settingsOf(FeedKeys.FOLLOWING).catchUp)
        assertTrue(graph.pluginPrefs.state.value.showPrivate)
        assertEquals(XAccountHealth(3, Instant.parse("2026-11-04T12:00:00Z").toEpochMilli()), graph.accountHealth.read("ct0"))
        assertEquals(XAccountHealth(1, null), graph.accountHealth.read("oauth"))

        val forYou = ReadMarks(graph.feedCache(ReadMarks.key(FeedKeys.FOR_YOU))).position()
        assertEquals(ReadPosition("1900000000000000003", Instant.parse("2026-11-04T23:00:00Z")), forYou)
        assertEquals(ReadPosition("1900000000000000004", null), ReadMarks(graph.feedCache(ReadMarks.key("g2"))).position())
    }

    @Test
    fun whatXtaNativeMadeGoesOutAndComesBackTheSame(): Unit = runBlocking {
        val made = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli()
        val session = Session(
            accounts = listOf(XAccount("ct0", "maya", mapOf("Cookie" to "ct0=ct0")), XAccount("oauth", null, mapOf("authorization" to "Bearer B"))),
            groups = listOf(FollowGroup("n1", "Mine", emptyList(), emoji = "🌙", color = 0xFF00FF00, icon = """{"pack":"custom","key":"star"}""", markStyle = 3, createdAt = made)),
        ).withSettings("n1", FeedSettings(catchUp = true)).withSettings(FeedKeys.FOLLOWING, FeedSettings(catchUp = false))
        val graph = AppGraph(dir, flowOf(true), "test")
        graph.sessions.update { session }
        graph.accountHealth.write("ct0", XAccountHealth(4, made))
        graph.pluginPrefs.update { it.copy(showPrivate = true) }
        val position = ReadPosition("1900000000000000009", Instant.parse("2026-11-01T00:00:00Z"))
        ReadMarks(graph.feedCache(ReadMarks.key(FeedKeys.FOR_YOU))).record(BackupRestore.markedThread(position), 0)

        val out = exported(graph)
        val row = out.groups!!.single()
        assertEquals(GroupRow("n1", "Mine", """{"pack":"custom","key":"star"}""", 0xFF00FF00, XtaTime.iso(Instant.ofEpochMilli(made)), emoji = "🌙", markStyle = 3), row)
        assertEquals(JsonPrimitive(false), out.settings!!["feed.catch_up_mode.-1"])

        val other = AppGraph(File(dir, "other"), flowOf(true), "test")
        DeviceBackup(other) { now }.restore(out, includeReadPositions = true)
        val again = other.sessions.state.value!!
        assertEquals(session.groups, again.groups)
        assertEquals(session.feeds, again.feeds)
        assertEquals(session.accounts, again.accounts)
        assertEquals(XAccountHealth(4, made), other.accountHealth.read("ct0"))
        assertTrue(other.pluginPrefs.state.value.showPrivate)
        assertEquals(position, ReadMarks(other.feedCache(ReadMarks.key(FeedKeys.FOR_YOU))).position())
    }

    @Test
    fun groupsAnOlderBuildRestoredWithoutTheirLookTakeItFromTheKeptRows(): Unit = runBlocking {
        val graph = AppGraph(dir, flowOf(true), "test")
        graph.sessions.update { Session(groups = listOf(FollowGroup("g1", "Space", emptyList()), FollowGroup("n", "New", emptyList(), createdAt = 5))) }
        graph.kept.merge(BackupDocument(groups = xta().groups!! + GroupRow("n", "New", color = 1, createdAt = "2020-01-01T00:00:00.000Z")))
        DeviceBackup(graph).adoptKeptGroups()
        val groups = graph.sessions.state.value!!.groups.associateBy { it.id }
        assertEquals(0xFF2196F3, groups.getValue("g1").color)
        assertEquals(2, groups.getValue("g1").markStyle)
        assertNull(groups.getValue("n").color, "a group that knows when it was made keeps its own look")
    }
}
