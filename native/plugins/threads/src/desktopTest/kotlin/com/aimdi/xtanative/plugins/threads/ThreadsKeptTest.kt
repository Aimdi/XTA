package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsKept
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ThreadsKeptTest {
    private val folder = Files.createTempDirectory("threads-kept").toFile().apply { deleteOnExit() }
    private fun file(name: String) = StoreFile(folder.resolve(name))
    private fun post(id: String, handle: String = "zuck", name: String = handle, avatar: String? = null) =
        ThreadsPost(id = id, handle = handle, authorName = name, text = "t", avatarUrl = avatar)

    @Test
    fun accountsAreXtasRowsListedByNameWhateverTheCase(): Unit = runBlocking {
        val accounts = ThreadsAccounts(file("accounts.json")) { Instant.parse("2026-11-04T08:00:00Z") }
        accounts.add(ThreadsAccount("Zuck", "mark"))
        accounts.add(ThreadsAccount("mosseri", "Adam"))
        accounts.add(ThreadsAccount("zuck", "Mark", "https://cdn/zuck.jpg"))

        val rows = accounts.load()
        assertEquals(listOf("mosseri", "zuck"), rows.map { it.id }, "each handle once, lower case, by name")
        assertEquals("https://cdn/zuck.jpg", rows.last().avatarUrl)
        val exported = J.parse(accounts.export()).list
        assertEquals(setOf("id", "name", "avatar_url", "in_feed", "created_at"), exported.first().keys)
        assertEquals(1L, exported.first()["in_feed"].long)
        assertTrue(exported.first()["created_at"].string!!.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")))

        val restored = ThreadsAccounts(file("other.json"))
        restored.import(accounts.export())
        assertEquals(rows, restored.load())
        restored.remove("@zuck".removePrefix("@"))
        assertFalse(ThreadsAccounts.follows(restored.state.value, "ZUCK"))
    }

    @Test
    fun followedAccountsKeepTheNameAndPictureTheirNewestPostsShow(): Unit = runBlocking {
        val accounts = ThreadsAccounts(file("accounts.json"))
        accounts.add(ThreadsAccount("zuck", "Mark", "https://cdn/old.jpg"))
        accounts.add(ThreadsAccount("meta", "Meta", "https://cdn/meta.jpg"))

        accounts.keepCurrent(listOf(post("1", "zuck", "Mark Z", "https://cdn/new.jpg"), post("2", "zuck", "Older"), post("3", "meta", " ", null)))

        val rows = accounts.load().associateBy { it.id }
        assertEquals("Mark Z", rows.getValue("zuck").name)
        assertEquals("https://cdn/new.jpg", rows.getValue("zuck").avatarUrl)
        assertEquals("Meta", rows.getValue("meta").name, "a post without a name keeps the one held")
        assertEquals("https://cdn/meta.jpg", rows.getValue("meta").avatarUrl)
    }

    @Test
    fun aLikeIsKeptHereItsPostInXtasPreferenceNewestFirst(): Unit = runBlocking {
        val settings = MutableStateFlow(Settings())
        val likes = ThreadsLikes(file("likes.json"), { settings.value }) { edit -> settings.value = edit(settings.value) }

        assertTrue(likes.toggle(post("1")), "a like says it is one, for the notice that likes stay here")
        likes.toggle(post("2"))
        assertEquals(listOf("2", "1"), likes.posts().map { it.id })
        assertTrue(likes.isLiked("1"))
        assertFalse(likes.toggle(post("1")))
        assertEquals(listOf("2"), likes.posts().map { it.id })
        assertEquals(listOf("2"), ThreadsPostJson.list(settings.value[ThreadsPrefs.likedPosts]).map { it.id })

        assertEquals(listOf("2"), J.parse(likes.export()).list.map { it["id"].string })
        val other = ThreadsLikes(file("other.json"), { settings.value }) { edit -> settings.value = edit(settings.value) }
        other.import("""[{"id":"1"},{"id":"2"},{"id":""}]""")
        assertEquals(setOf("1", "2"), other.load())
        assertEquals(listOf("2"), other.posts().map { it.id }, "a restored like shows once its post is kept")
    }

    @Test
    fun asManyLikedPostsAsXtaKeeps() {
        val many = (1..ThreadsLikes.CAP).map { post("$it") }
        val next = ThreadsLikes.toggled(many, post("new"), wasLiked = false)
        assertEquals(ThreadsLikes.CAP, next.size)
        assertEquals("new", next.first().id)
    }

    @Test
    fun theClientsMemoryIsXtasPreferences(): Unit = runBlocking {
        val until = Instant.parse("2026-11-04T09:00:00Z").toEpochMilli()
        val kept = ThreadsKept(deviceId = "d-1", userIds = mapOf("zuck" to "314"), guestLsd = "lsd", guestLsdAt = until - 1000, cooldownUntil = until)
        val written = ThreadsSettingsMemory.written(Settings(), kept)

        assertEquals("d-1", written[ThreadsPrefs.directDeviceId])
        assertEquals("""{"zuck":"314"}""", written[ThreadsPrefs.userIds])
        assertEquals("2026-11-04T09:00:00.000Z", written[ThreadsPrefs.directCooldownUntil])
        assertEquals(kept, ThreadsSettingsMemory.read(written))
        val local = Settings().with(ThreadsPrefs.directCooldownUntil, "2026-11-04T10:00:00.000")
        val tenLocal = LocalDateTime.parse("2026-11-04T10:00:00").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(tenLocal, ThreadsSettingsMemory.read(local).cooldownUntil, "XTA writes a local time")
        assertEquals(ThreadsKept(), ThreadsSettingsMemory.read(Settings()))
    }

    @Test
    fun theClientsChangesReachTheAppsSettingsAndAreInForceAtOnce(): Unit = runBlocking {
        val stored = MutableStateFlow<Settings?>(Settings().with(ThreadsPrefs.directDeviceId, "stored"))
        val memory = ThreadsSettingsMemory(stored, { edit -> stored.value = edit(stored.value!!) }, this)

        assertEquals("stored", memory.kept.deviceId, "what XTA kept is read until the client changes something")
        memory.kept = memory.kept.copy(userIds = mapOf("zuck" to "314"))
        assertEquals(mapOf("zuck" to "314"), memory.kept.userIds)
        stored.first { it?.get(ThreadsPrefs.userIds) == """{"zuck":"314"}""" }
        assertEquals("stored", stored.value!![ThreadsPrefs.directDeviceId])
        coroutineContext.cancelChildren()
    }
}

