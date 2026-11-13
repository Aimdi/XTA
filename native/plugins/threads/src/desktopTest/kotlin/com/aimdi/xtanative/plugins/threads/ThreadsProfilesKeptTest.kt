package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfilePage
import com.aimdi.xtanative.store.StoreFile
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** The profiles kept so that one opened again shows at once what it showed last. */
class ThreadsProfilesKeptTest {
    private val at = Instant.parse("2026-11-01T09:30:00Z")
    private val zuck = ThreadsProfile(
        pk = "314216", id = "314216", username = "zuck", fullName = "Mark Zuckerberg", isVerified = true,
        profilePicUrl = "https://cdn.example/zuck_150.jpg", hdPictureUrl = "https://cdn.example/zuck_1080.jpg",
        biography = "Mostly superintelligence", followerCount = 5_200_000, mediaCount = 412, externalUrl = "https://about.meta.com",
    )

    private fun post(id: String, handle: String = "zuck") =
        ThreadsPost(id = id, handle = handle, authorName = handle, text = "t$id", url = "https://www.threads.com/@$handle/post/C$id")

    @Test
    fun aProfileReadsBackWithItsCardAndThreadsButNotItsRepliesPage() {
        val page = ThreadsProfilePage(zuck, listOf(post("1"), post("2")), replies = listOf(post("9")))
        val kept = ThreadsProfilesKept.decode(ThreadsProfilesKept().with("@Zuck ".removePrefix("@"), page, at).encode())
        val back = kept.of("ZUCK")!!
        assertEquals(page.copy(replies = null), back.page)
        assertEquals(at, back.readAt)
    }

    @Test
    fun theNewestComeFirstAndTheOldestBeyondTheCapAreLetGo() {
        val many = (1..ThreadsProfilesKept.CAP + 3).fold(ThreadsProfilesKept()) { kept, n ->
            kept.with("h$n", ThreadsProfilePage(null, listOf(post("$n", "h$n"))), at.plusSeconds(n.toLong()))
        }
        assertEquals(ThreadsProfilesKept.CAP, many.profiles.size)
        assertEquals("h${ThreadsProfilesKept.CAP + 3}", many.profiles.first().handle)
        assertNull(many.of("h1"))
        val again = many.with("h10", ThreadsProfilePage(null, listOf(post("x", "h10"))), at)
        assertEquals("h10", again.profiles.first().handle, "read again, it moves to the front, once")
        assertEquals(1, again.profiles.count { it.handle == "h10" })
    }

    @Test
    fun aProfileKeepsItsFirstThreadsOnlyAndAnEmptyReadChangesNothing() {
        val long = ThreadsProfilePage(zuck, (1..40).map { post("$it") })
        assertEquals(ThreadsProfilesKept.POSTS, ThreadsProfilesKept().with("zuck", long, at).of("zuck")!!.page.posts.size)
        val kept = ThreadsProfilesKept().with("zuck", long, at)
        assertEquals(kept, kept.with("zuck", ThreadsProfilePage(null), at))
    }

    @Test
    fun anUnreadableFileKeepsNothing() {
        assertEquals(ThreadsProfilesKept(), ThreadsProfilesKept.decode(null))
        assertEquals(ThreadsProfilesKept(), ThreadsProfilesKept.decode("not json"))
        assertEquals(ThreadsProfilesKept(), ThreadsProfilesKept.decode("""{"v":2,"profiles":[]}"""))
        assertEquals(ThreadsProfilesKept(), ThreadsProfilesKept.decode("""{"v":1,"profiles":[{"handle":"zuck"},{"readAt":"2026-11-01T09:30:00.000Z"}]}"""))
    }

    @Test
    fun theShelfWritesWhatItKeepsAndAFreshShelfReadsItBack() = runBlocking {
        val file = StoreFile(Files.createTempDirectory("threads-profiles").resolve("profiles.json").toFile())
        ThreadsProfileShelf(file).keep("zuck", ThreadsProfilePage(zuck, listOf(post("1"))), at)
        assertEquals(zuck, ThreadsProfileShelf(file).of("zuck")?.page?.profile)
        assertNull(ThreadsProfileShelf(file).of("mosseri"))
    }
}
