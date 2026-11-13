package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsImage
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfilePage
import com.aimdi.xtanative.store.StoreDir
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The profiles kept so that one opened again shows at once what it showed last. */
class ThreadsProfilesKeptTest {
    private val at = Instant.parse("2026-11-01T09:30:00Z")
    private var now = at
    private val dir = StoreDir(Files.createTempDirectory("threads-profiles").toFile())
    private val zuck = ThreadsProfile(
        pk = "314216", id = "314216", username = "zuck", fullName = "Mark Zuckerberg", isVerified = true,
        profilePicUrl = "https://cdn.example/zuck_150.jpg", hdPictureUrl = "https://cdn.example/zuck_1080.jpg",
        biography = "Mostly superintelligence", followerCount = 5_200_000, mediaCount = 412, externalUrl = "https://about.meta.com",
        pictureSizes = listOf(ThreadsImage("https://cdn.example/zuck_1080.jpg", 1080, 1080), ThreadsImage("https://cdn.example/zuck_320.jpg", 320, 320)),
    )

    private fun shelf() = ThreadsProfileShelf(dir::file, legacy = dir.file("profiles.json")) { now }

    private fun post(id: String, handle: String = "zuck") =
        ThreadsPost(id = id, handle = handle, authorName = handle, text = "t$id", url = "https://www.threads.com/@$handle/post/C$id")

    @Test
    fun aProfileReadsBackWithItsCardItsPictureSizesAndThreadsButNotItsRepliesPage() = runBlocking {
        val page = ThreadsProfilePage(zuck, listOf(post("1"), post("2")), replies = listOf(post("9")), next = "cursor")
        shelf().keep("@Zuck ", page, at)
        val back = shelf().of("ZUCK")!!
        assertEquals(page.copy(replies = null, next = null), back.page)
        assertEquals(at, back.readAt)
        assertNull(shelf().of("mosseri"))
    }

    @Test
    fun theNewestComeFirstAndTheOldestBeyondTheCapAreLetGoWithTheirFiles() = runBlocking {
        val shelf = shelf()
        (1..ThreadsProfileShelf.CAP + 3).forEach { n -> shelf.keep("h$n", ThreadsProfilePage(null, listOf(post("$n", "h$n"))), at.plusSeconds(n.toLong())) }
        assertNull(shelf().of("h1"))
        assertNull(shelf().of("h3"))
        assertEquals("h4", shelf().of("h4")?.handle)
        assertEquals(ThreadsProfileShelf.CAP, dir.root.resolve("kept_profiles").list()!!.count { it != "index.json" }, "a let-go profile's file is deleted")
        shelf.keep("h4", ThreadsProfilePage(null, listOf(post("x", "h4"))), at.plusSeconds(100))
        shelf.keep("h100", ThreadsProfilePage(null, listOf(post("y", "h100"))), at.plusSeconds(101))
        assertEquals(listOf("x"), shelf().of("h4")?.page?.posts?.map { it.id }, "read again, it moved to the front and stays")
        assertNull(shelf().of("h5"), "the oldest left made room")
    }

    @Test
    fun aProfileKeepsItsFirstThreadsOnlyAndAnEmptyReadChangesNothing() = runBlocking {
        val shelf = shelf()
        shelf.keep("zuck", ThreadsProfilePage(zuck, (1..40).map { post("$it") }), at)
        assertEquals(ThreadsProfilesKept.POSTS, shelf.of("zuck")!!.page.posts.size)
        shelf.keep("zuck", ThreadsProfilePage(null), at.plusSeconds(1))
        assertEquals(at, shelf.of("zuck")!!.readAt)
    }

    @Test
    fun aProfileKeptLongerThanAWeekIsNotShown() = runBlocking {
        shelf().keep("zuck", ThreadsProfilePage(zuck), at)
        now = at.plusSeconds(ThreadsProfileShelf.MAX_AGE_DAYS * 24 * 3600 - 60)
        assertEquals(zuck, shelf().of("zuck")?.page?.profile)
        now = at.plusSeconds(ThreadsProfileShelf.MAX_AGE_DAYS * 24 * 3600 + 60)
        assertNull(shelf().of("zuck"))
    }

    @Test
    fun theFileEarlierVersionsKeptIsTakenOverOnceThenDeleted() = runBlocking {
        val legacy = """{"v":1,"profiles":[
            {"handle":"zuck","readAt":"2026-11-01T09:30:00.000Z","profile":{"username":"zuck","fullName":"Mark"},"posts":[]},
            {"handle":"broken"},
            {"handle":"meta","readAt":"2026-10-31T09:30:00.000Z","profile":null,"posts":[{"id":"7","handle":"meta","text":"hi"}]}
        ]}"""
        dir.file("profiles.json").write(legacy)
        val shelf = shelf()
        assertEquals("Mark", shelf.of("zuck")?.page?.profile?.fullName)
        assertEquals(listOf("7"), shelf.of("meta")?.page?.posts?.map { it.id })
        assertNull(dir.file("profiles.json").read(), "taken over, the old file is gone")
        assertTrue(ThreadsProfilesKept.legacy("not json").isEmpty())
        assertTrue(ThreadsProfilesKept.legacy("""{"v":2,"profiles":[]}""").isEmpty())
    }

    @Test
    fun anUnreadableIndexOrEntryKeepsNothingAndAFileNameNeverLeavesItsFolder() = runBlocking {
        shelf().keep("zuck", ThreadsProfilePage(zuck), at)
        dir.file("kept_profiles/zuck.json").write("not json")
        assertNull(shelf().of("zuck"))
        dir.file("kept_profiles/index.json").write("""{"v":1,"entries":[{"key":"x","file":"../../evil.json","at":"2026-11-01T09:30:00.000Z"}]}""")
        assertNull(shelf().of("x"), "an index naming another file is not followed")
        assertEquals("a_b-c_.json", ThreadsKeptShelf.fileName("a/b-c."))
        assertFalse(ThreadsKeptShelf.fileName("../x").contains('/'))
    }
}
