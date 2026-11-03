package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.store.StoreFile
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking

class BlueskyStoresTest {
    private fun file(name: String): File = File(Files.createTempDirectory("bluesky-plugin").toFile(), name)

    private val maya = BlueskyProfile(did = "did:plc:maya", handle = "maya.bsky.social", displayName = "Maya Chen", avatarUrl = "https://cdn.example/maya.jpg")

    @Test
    fun aFollowIsKeptOnTheDevice(): Unit = runBlocking {
        val path = file("follows.json")
        BlueskyFollows(StoreFile(path)).follow(maya, at = 1000)
        val read = BlueskyFollows(StoreFile(path)).load()
        assertEquals(listOf(BlueskyFollow("did:plc:maya", "maya.bsky.social", "Maya Chen", "https://cdn.example/maya.jpg", 1000)), read)
    }

    @Test
    fun followingAgainKeepsThePlaceAndTheDateWithTheNewName(): Unit = runBlocking {
        val follows = BlueskyFollows(StoreFile(file("follows.json")))
        follows.follow(maya, at = 1000)
        follows.follow(BlueskyProfile(did = "did:plc:sam", handle = "sam.bsky.social", displayName = "Sam"), at = 2000)
        follows.follow(maya.copy(displayName = "Maya C."), at = 3000)
        val kept = follows.load()
        assertEquals(listOf("Maya C.", "Sam"), kept.map { it.name })
        assertEquals(1000, kept.first().followedAt)
    }

    @Test
    fun unfollowingByHandleOrDidRemovesTheAccount(): Unit = runBlocking {
        val follows = BlueskyFollows(StoreFile(file("follows.json")))
        follows.follow(maya, at = 1000)
        follows.unfollow("MAYA.bsky.social")
        assertEquals(emptyList(), follows.load())
        follows.follow(maya, at = 1000)
        follows.unfollow("did:plc:maya")
        assertEquals(emptyList(), follows.load())
        follows.unfollow("")
    }

    @Test
    fun theBackupTakesTheFollowsAndGivesThemBack(): Unit = runBlocking {
        val first = BlueskyFollows(StoreFile(file("follows.json")))
        first.follow(maya, at = 1000)
        val exported = first.export()

        val second = BlueskyFollows(StoreFile(file("follows.json")))
        second.import(exported)
        assertEquals(first.load(), second.load())
        assertEquals(first.load(), second.state.value)
    }

    @Test
    fun aDamagedFileReadsAsNoFollows(): Unit = runBlocking {
        val path = file("follows.json").apply { writeText("{\"follows\": [{\"name\": \"nobody\"}, 3]}") }
        assertEquals(emptyList(), BlueskyFollows(StoreFile(path)).load())
    }

    @Test
    fun theSettingsAreKept(): Unit = runBlocking {
        val path = file("settings.json")
        BlueskySettingsStore(StoreFile(path)).update { it.copy(replies = false) }
        val read = BlueskySettingsStore(StoreFile(path)).also { it.load() }.state.value
        assertFalse(read.replies)
        assertEquals(true, read.reposts)
    }
}
