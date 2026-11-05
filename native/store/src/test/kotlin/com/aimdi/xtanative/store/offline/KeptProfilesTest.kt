package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.core.x.model.XAffiliation
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVerifiedType
import com.aimdi.xtanative.store.saved.sha256
import com.aimdi.xtanative.store.saved.tempStore
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class KeptProfilesTest {
    private val dir = tempStore()
    private var now = 1_800_000_000_000L
    private val kept = KeptProfiles(dir) { now }
    private val day = 24L * 60 * 60 * 1000

    private val maya = XUser(
        id = "42",
        screenName = "Maya",
        name = "Maya M.",
        avatarUrl = "https://pbs.twimg.com/a_normal.jpg",
        protected = true,
        description = "hello https://t.co/x",
        bannerUrl = "https://pbs.twimg.com/b",
        location = "Berlin",
        website = "https://maya.example",
        followers = 1200,
        following = 30,
        posts = 900,
        createdAt = Instant.ofEpochMilli(1_500_000_000_000L),
        descriptionLinks = listOf(XEntity.Link(6, 18, "https://t.co/x", "https://maya.example/x", "maya.example/x")),
        verifiedType = XVerifiedType.BUSINESS,
        affiliation = XAffiliation("https://badge", "Org", "https://x.com/org", "BusinessLabel"),
        followedByReader = true,
        likes = 5,
        listed = 2,
        sensitive = true,
    )

    @Test
    fun aProfileReadsBackWholeByIdAndByHandle() = runBlocking {
        kept.remember(maya, listOf("7", "8"))
        val byId = kept.byId("42")!!
        assertEquals(maya, byId.user)
        assertEquals(listOf("7", "8"), byId.pinned)
        assertEquals(now, byId.at)
        assertEquals(maya, kept.byName("@maya")?.user)
    }

    @Test
    fun aWeekOldProfileIsAMiss() = runBlocking {
        kept.remember(maya, emptyList())
        now += 7 * day + 1
        assertNull(kept.byId("42"))
        assertNull(kept.byName("maya"))
    }

    @Test
    fun anUnreadableOrMissingProfileIsAMissNotAnError() = runBlocking {
        assertNull(kept.byId("nope"))
        File(dir.root, "profiles/${sha256("id:9")}.json").apply { parentFile.mkdirs(); writeText("{not json") }
        assertNull(kept.byId("9"))
    }

    @Test
    fun onlyTheNewestHundredKeysAreKept() = runBlocking {
        repeat(60) { kept.remember(maya.copy(id = "$it", screenName = "u$it"), emptyList()) }
        val files = File(dir.root, "profiles").listFiles()!!.filter { it.name.endsWith(".json") }
        assertTrue(files.size <= KeptProfiles.MAX_ENTRIES, "kept ${files.size}")
    }
}
