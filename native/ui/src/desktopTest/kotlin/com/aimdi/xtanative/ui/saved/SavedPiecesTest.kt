package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.saved.ArchiveFilter
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedSource
import com.aimdi.xtanative.store.saved.SavedTabs
import com.aimdi.xtanative.store.settings.SavedPrefs
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SavedPiecesTest {
    private val author = XUser("9", "maya", "Maya")

    private fun post(id: String) = XPost(id, author, null, "text $id", media = listOf(XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/p$id.jpg", 10, 10)))

    private fun conversation(focal: XPost?) = XConversation(emptyList(), focal, emptyList(), null)

    @Test
    fun anotherNetworksPostIsDrawnAsWhoWhatAndWhere() {
        val mastodon = SavedContent.of(
            json(
                """{"xtaPlugin":"mastodon","post":{"version":1,"id":"5","acct":"ann@social.example","name":"Ann","url":"https://social.example/@ann/5",
                "text":"<p>Hello &amp; welcome</p><p>Line two<br/>three</p>","images":["https://social.example/a.png"]}}""",
            ),
        )
        val card = KeptCard.of(mastodon)!!
        assertEquals(SavedSource.MASTODON, card.source)
        assertEquals("Ann", card.name)
        assertEquals("@ann@social.example", card.handle)
        assertEquals("Hello & welcome\nLine two\nthree", card.text)
        assertEquals(listOf("https://social.example/a.png"), card.images)

        val reddit = KeptCard.of(savedReddit("r1", picture = true).about)!!
        assertEquals("r/pics", reddit.name)
        assertEquals("Cat r1", reddit.title)
        assertEquals(listOf("https://i.redd.it/r1.jpg"), reddit.images)
        assertEquals("https://www.reddit.com/r/pics/r1", reddit.url)

        assertNull(KeptCard.of(SavedContent.of(xTweet("1"))), "an X post draws as a post")
        assertEquals("https://bsky.app/profile/ann.bsky.social/post/3k", KeptCard.blueskyUrl("ann.bsky.social", "at://did:plc:x/app.bsky.feed.post/3k"))
    }

    @Test
    fun aNewNoticeReplacesTheOneShowingAndAnOldHideLeavesIt() {
        KeepNotices.show(KeepNotice.Downloading)
        val working = KeepNotices.shown.value!!
        KeepNotices.show(KeepNotice.MediaSaved)
        KeepNotices.hide(working)
        assertEquals(KeepNotice.MediaSaved, KeepNotices.shown.value?.notice)
        KeepNotices.endWorking()
        assertEquals(KeepNotice.MediaSaved, KeepNotices.shown.value?.notice, "only a working notice ends with its work")
        KeepNotices.show(KeepNotice.Downloading)
        KeepNotices.endWorking()
        assertNull(KeepNotices.shown.value)
        assertTrue(KeepNotice.LikesStayHere.long)
    }

    @Test
    fun theScanFindsWhatXNoLongerHasAndStopsAtARateLimit(): Unit = runBlocking {
        val kept = savedX("1")
        assertEquals(CleanupCheck.OK, SavedCleanup.check(kept) { conversation(post("1")) })
        assertEquals(CleanupCheck.BROKEN, SavedCleanup.check(kept) { conversation(null) })
        assertEquals(CleanupCheck.UNREACHABLE, SavedCleanup.check(kept) { throw IOException("offline") })
        assertEquals(CleanupCheck.RATE_LIMITED, SavedCleanup.check(kept) { throw XRateLimitedException() })
        assertEquals(CleanupCheck.OK, SavedCleanup.check(savedReddit("r1")) { error("X is not asked about Reddit") })

        val scan = CleanupScan(4).after(kept, CleanupCheck.BROKEN).after(kept, CleanupCheck.UNREACHABLE).after(kept, CleanupCheck.RATE_LIMITED)
        assertTrue(scan.done)
        assertEquals(listOf("1"), scan.broken.map { it.id })
        assertEquals(3, scan.unreachable, "the unreachable one and the two never checked")
        assertEquals(0.5f, CleanupScan(4, checked = 2).fraction)
    }

    @Test
    fun aDeletedPostIsNamedByItsAuthorAndText() {
        assertEquals("@user1: post 1", SavedCleanup.label(savedX("1")))
        assertEquals("r/pics: Cat r1", SavedCleanup.label(savedReddit("r1")))
        val long = savedX("2").copy(content = xTweet("2", text = "x".repeat(60)))
        assertEquals("@user2: " + "x".repeat(50) + "…", SavedCleanup.label(long))
        assertEquals("3", SavedCleanup.label(savedX("3").copy(content = null)))
    }

    @Test
    fun manageFoldersListsEveryTabWithWhatShowsIt() {
        val library = SavedLibrary(listOf(savedX("1", folder = "art"), savedX("2", folder = "art")), listOf(SavedFolder("art", "Art")))
        val rows = FolderRow.of(library, listOf(SavedTabs.NOTES, "art"))
        assertEquals(listOf(SavedTabs.NOTES, "art", SavedTabs.ALL, SavedTabs.UNFILED, SavedTabs.FAVORITES), rows.map { it.token })
        assertNull((rows[0] as FolderRow.BuiltIn).shown, "Notes always shows")
        assertEquals(2, (rows[1] as FolderRow.Folder).count)
        assertEquals(SavedPrefs.showAllTab, (rows[2] as FolderRow.BuiltIn).shown)
    }

    @Test
    fun aDraggedRowPassesANeighbourOnceHalfOfItIsCrossed() {
        val heights = listOf(50, 50, 100, 50)
        assertEquals(0, dropIndex(0, 20f, heights))
        assertEquals(1, dropIndex(0, 30f, heights))
        assertEquals(1, dropIndex(0, 90f, heights))
        assertEquals(2, dropIndex(0, 110f, heights))
        assertEquals(3, dropIndex(0, 500f, heights))
        assertEquals(2, dropIndex(3, -110f, heights))
        assertEquals(1, dropIndex(3, -130f, heights))
        assertEquals(2, dropIndex(2, 0f, heights))
    }

    @Test
    fun theDeviceKeepsThePostShown() {
        val original = post("1")
        val repost = XPost("2", XUser("3", "sam", "Sam"), null, "RT", reposted = original)
        val kept = Keepable.of(repost)
        assertEquals("1", kept.id)
        assertEquals("9", kept.authorId)
        assertEquals("1", SavedContent.of(kept.content).xPost?.id)
        assertTrue(profileSavedEmptyIsLikes(ArchiveFilter.LIKES))
        assertFalse(profileSavedEmptyIsLikes(ArchiveFilter.ALL))
    }
}
