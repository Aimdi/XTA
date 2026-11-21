package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsActivityFilter
import com.aimdi.xtanative.networks.threads.ThreadsActivityItem
import com.aimdi.xtanative.networks.threads.ThreadsActivityKind
import com.aimdi.xtanative.networks.threads.ThreadsCaption
import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsForYou
import com.aimdi.xtanative.networks.threads.ThreadsForYouFeed
import com.aimdi.xtanative.networks.threads.ThreadsForYouPage
import com.aimdi.xtanative.networks.threads.ThreadsForYouSource
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPages
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** What the reader was missing: Threads' own Media tab, activity kinds, a people search that spares the account, and feeds that go on. */
class ThreadsReaderGapsTest {
    private val picture = ThreadsMedia(url = "https://scontent.cdninstagram.com/a.jpg")

    private fun post(id: String, handle: String = "ann", media: Boolean = false, repostedBy: String? = null, at: Long = 0) = ThreadsPost(
        id = id,
        handle = handle,
        authorName = handle,
        text = "t$id",
        media = if (media) listOf(picture) else emptyList(),
        repostedByHandle = repostedBy,
        publishedAt = Instant.ofEpochSecond(at),
    )

    @Test
    fun theMediaPageGivesTheAccountsOwnPostsWithMediaOnce() {
        val chains = listOf(
            listOf(post("1", media = true), post("2")),
            listOf(post("3", handle = "bob", media = true), post("4", media = true)),
            listOf(post("5", handle = "bob", media = true, repostedBy = "ann")),
            listOf(post("1", media = true)),
        )
        assertEquals(listOf("1", "4"), ThreadsPages.media(chains, "ann").map { it.id }, "her threads and replies with media; not others', not a repost, none twice")
    }

    @Test
    fun theMediaTabJoinsThreadsOwnPageWithWhatIsLoadedNewestFirst() {
        val own = listOf(post("1", media = true, at = 10), post("2", media = true, at = 30))
        val loaded = listOf(post("2", media = true, at = 30), post("3", media = true, at = 20))
        assertEquals(listOf("2", "3", "1"), ThreadsProfileView.media(own, loaded).map { it.id })
        assertEquals(listOf("3"), ThreadsProfileView.media(emptyList(), listOf(post("3", media = true))).map { it.id }, "a page Meta gave nothing of still shows what is at hand")
    }

    @Test
    fun aTabKeptFromGuestsIsEmptyAndAnyOtherFailureIsSaid() {
        assertTrue(ThreadsProfileView.keptFromGuests(ThreadsException(ThreadsFailure.SIGN_IN_REQUIRED, "x")))
        assertFalse(ThreadsProfileView.keptFromGuests(ThreadsException(ThreadsFailure.THROTTLED, "x")))
        assertFalse(ThreadsProfileView.keptFromGuests(java.io.IOException("offline")))
    }

    @Test
    fun aKindOfActivityIsPickedOutOfAllWithoutAReadOfItsOwn() {
        fun line(id: String, kind: ThreadsActivityKind) = ThreadsActivityItem(id, kind, "words")
        val lines = listOf(line("1", ThreadsActivityKind.FOLLOW), line("2", ThreadsActivityKind.QUOTE), line("3", ThreadsActivityKind.LIKE))
        assertEquals(ThreadsActivityFilter.ALL, ThreadsActivityFilter.QUOTES.asked)
        assertEquals(ThreadsActivityFilter.REPLIES, ThreadsActivityFilter.REPLIES.asked, "Meta's own filters are asked by name")
        assertEquals(listOf("2"), ThreadsActivityFilter.QUOTES.shown(lines).map { it.id })
        assertEquals(listOf("1"), ThreadsActivityFilter.FOLLOWS.shown(lines).map { it.id })
        assertEquals(lines, ThreadsActivityFilter.ALL.shown(lines))
        assertNull(ThreadsActivityFilter.FOLLOWS.selected)
    }

    @Test
    fun aPeopleSearchSpendsTheAccountOnlyWhenAGuestFindsNoOne(): Unit = runBlocking {
        val ann = ThreadsProfile(pk = "1", id = "1", username = "ann", fullName = "Ann")
        val bob = ThreadsProfile(pk = "2", id = "2", username = "bob", fullName = "Bob")
        var asked = 0
        val account: suspend () -> List<ThreadsProfile> = { asked++; listOf(bob) }
        assertEquals(listOf(ann), ThreadsSearchRules.people({ listOf(ann) }, account))
        assertEquals(0, asked, "a guest found her")
        assertEquals(listOf(bob), ThreadsSearchRules.people({ null }, account), "the guest read is off")
        assertEquals(listOf(bob), ThreadsSearchRules.people({ emptyList() }, account))
        assertEquals(listOf(bob), ThreadsSearchRules.people({ throw ThreadsException(ThreadsFailure.THROTTLED, "x") }, account))
        assertEquals(3, asked)
        assertEquals(emptyList(), ThreadsSearchRules.people({ emptyList() }, null))
        assertFailsWith<ThreadsException> { ThreadsSearchRules.people({ throw ThreadsException(ThreadsFailure.THROTTLED, "x") }, null) }
    }

    @Test
    fun aMentionEndsBeforeTheSentencesFullStop() {
        fun mentions(text: String) = ThreadsCaption.hits(text).filter { it.kind == ThreadsTextFragment.Kind.MENTION }.map { text.substring(it.start, it.end) }
        assertEquals(listOf("@zuck"), mentions("thanks @zuck."))
        assertEquals(listOf("@a.b_c"), mentions("see @a.b_c, then"))
    }

    @Test
    fun aGuestsSelectionKeptFromTheLastRunGoesOn(): Unit = runBlocking {
        val asked = mutableListOf<String?>()
        val feed = ThreadsForYouFeed({ cursor ->
            asked += cursor
            ThreadsForYouPage(ThreadsForYouSource.GUEST, listOf(post("9")), null)
        })
        feed.restore(listOf(post("1")), ThreadsForYouSource.GUEST, Instant.EPOCH)
        assertEquals(ThreadsForYou.AFTER_RESTORE, feed.state.value.next)
        feed.more()
        assertEquals(listOf<String?>(ThreadsForYou.AFTER_RESTORE), asked)
        assertEquals(listOf("1", "9"), feed.state.value.posts.map { it.id })

        val own = ThreadsForYouFeed({ ThreadsForYouPage(ThreadsForYouSource.SESSION, emptyList(), null) })
        own.restore(listOf(post("1")), ThreadsForYouSource.SESSION, Instant.EPOCH)
        assertNull(own.state.value.next, "a session's cursor is not kept: a refresh reads on")
    }

    @Test
    fun aSnapshotCutAtItsCapDoesNotAnswerForAQuietAccount() {
        assertFalse(ThreadsFeedSnapshot(listOf(post("1"))).isCut)
        assertTrue(ThreadsFeedSnapshot((1..ThreadsFeedSnapshot.CAP).map { post("$it") }).isCut)
    }
}
