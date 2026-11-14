package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsConversation
import com.aimdi.xtanative.networks.threads.ThreadsOwnFeed
import com.aimdi.xtanative.networks.threads.ThreadsPeopleList
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsTopic
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Round 21's decisions: what is hidden and muted on this device, a thread read as an article, a post as a picture, which lists share one video player. */
class ThreadsReadingParityTest {
    private fun post(id: String, handle: String = "zuck", text: String = "post $id") =
        ThreadsPost(id = id, handle = handle, authorName = handle.replaceFirstChar(Char::uppercase), text = text, url = "https://www.threads.com/@$handle/post/C$id")

    // ---- Hiding a post and muting an account, on this device ----

    @Test
    fun aMutedAccountsPostsAndRepostsLeaveTheFeedsAndAQuoteOfItStays() {
        val options = ThreadsFeedOptions().mutingAccount("@Spammer ")
        val quoting = post("4").copy(quoted = post("q", handle = "spammer"))
        val posts = listOf(post("1"), post("2", handle = "spammer"), post("3", handle = "meta").repostedBy("r3", "spammer", "Spam"), quoting)

        assertEquals(listOf("spammer"), options.mutedAccounts)
        assertTrue(options.mutes("SPAMMER") && options.mutes("@spammer") && !options.mutes("zuck") && !options.mutes(null))
        assertEquals(listOf("1", "4"), options.shown(posts).map { it.id }, "Home")
        assertEquals(listOf("1", "4"), options.unmuted(posts).map { it.id }, "For you")
        assertEquals(listOf("1", "4"), options.unhidden(posts).map { it.id }, "the app's timelines")
        assertEquals(options, options.mutingAccount("spammer"), "muted once")
        assertEquals(options, options.mutingAccount("  "), "no handle mutes no one")
        assertEquals(posts, options.unmutingAccount("Spammer").shown(posts))
    }

    @Test
    fun aHiddenPostLeavesEveryListAndComesBackFromTheFilters() {
        val posts = listOf(post("1"), post("2"), post("3"))
        val options = ThreadsFeedOptions().hiding("2").hiding("2").hiding(" ")

        assertEquals(listOf("2"), options.hiddenPosts)
        assertEquals(listOf("1", "3"), options.shown(posts).map { it.id })
        assertEquals(listOf("1", "3"), options.unmuted(posts).map { it.id })
        assertFalse(options.filtered, "a hidden post is no filter: the badge counts none")
        assertEquals(posts, options.showingHidden().shown(posts))
        assertEquals(posts, ThreadsFeedOptions().unhidden(posts), "nothing hidden: the same list, not a copy")
    }

    @Test
    fun onlyTheNewestHiddenPostsAreRemembered() {
        val options = (1..ThreadsFeedOptions.HIDDEN_LIMIT + 5).fold(ThreadsFeedOptions()) { all, n -> all.hiding("$n") }

        assertEquals(ThreadsFeedOptions.HIDDEN_LIMIT, options.hiddenPosts.size)
        assertEquals("6", options.hiddenPosts.first())
        assertEquals("${ThreadsFeedOptions.HIDDEN_LIMIT + 5}", options.hiddenPosts.last())
    }

    @Test
    fun mutedAccountsAndHiddenPostsAreKeptBesideXtasKeysAndSurviveReset() {
        val options = ThreadsFeedOptions(hideReposts = true).muting("cat").mutingAccount("spammer").hiding("9")

        assertEquals(options, ThreadsFeedOptions.decode(options.encode()))
        assertEquals(
            """{"content":"all","hideReplies":false,"hideReposts":true,"mutedWords":["cat"],"mutedAccounts":["spammer"],"hiddenPosts":["9"]}""",
            options.encode(),
        )
        assertEquals("""{"content":"all","hideReplies":false,"hideReposts":false}""", ThreadsFeedOptions().encode(), "nothing muted or hidden: XTA's keys alone")
        assertEquals(3, options.activeCount, "reposts, muted words, muted accounts")
        assertTrue(options.resettable)
        val reset = options.reset()
        assertEquals(ThreadsFeedOptions(mutedWords = listOf("cat"), mutedAccounts = listOf("spammer"), hiddenPosts = listOf("9")), reset)
        assertFalse(reset.resettable, "the lists the reader made are not switches to put back")
        assertEquals(listOf("spammer"), ThreadsFeedOptions.decode("""{"mutedAccounts":["@Spammer","spammer"," "]}""").mutedAccounts)
    }

    @Test
    fun aConversationLeavesOutMutedRepliesAndWhatAnswersThem() {
        val focus = post("f")
        val conversation = ThreadsConversation(
            focus = focus,
            ancestors = listOf(post("a", handle = "spammer")),
            replies = listOf(
                listOf(post("r1", handle = "spammer"), post("r1b", handle = "meta")),
                listOf(post("r2", handle = "meta"), post("r2b", handle = "spammer"), post("r2c", handle = "meta")),
                listOf(post("r3", handle = "meta")),
                listOf(post("hidden", handle = "meta")),
            ),
            loaded = true,
            related = listOf(post("x", handle = "spammer"), post("y")),
        )
        val options = ThreadsFeedOptions().mutingAccount("spammer").hiding("hidden")

        val quieted = ThreadsConversationView.quieted(conversation, options)
        assertEquals(listOf(listOf("r2"), listOf("r3")), quieted.replies.map { chain -> chain.map { it.id } })
        assertEquals(listOf("y"), quieted.related.map { it.id })
        assertEquals(conversation.ancestors, quieted.ancestors, "what the opened post answers stays: the reader asked for it")
        assertTrue(ThreadsConversationView.quieted(conversation, ThreadsFeedOptions(mutedWords = listOf("post"))) === conversation, "muted words are the feeds' alone")
    }

    @Test
    fun theSheetOffersWhatAppliesToThePost() {
        val plain = post("1")
        assertEquals(
            listOf(ThreadsSheetRow.COPY_TEXT, ThreadsSheetRow.COPY_LINK, ThreadsSheetRow.SHARE_IMAGE, ThreadsSheetRow.HIDE, ThreadsSheetRow.MUTE),
            ThreadsSheetRows.of(plain, ThreadsFeedOptions(), picture = true),
        )
        assertEquals(
            listOf(ThreadsSheetRow.HIDE, ThreadsSheetRow.MUTE),
            ThreadsSheetRows.of(plain.copy(text = " ", url = null), ThreadsFeedOptions(), picture = false),
        )
        val quiet = ThreadsFeedOptions().mutingAccount("zuck").hiding("1")
        assertEquals(
            listOf(ThreadsSheetRow.COPY_TEXT, ThreadsSheetRow.COPY_LINK, ThreadsSheetRow.UNMUTE),
            ThreadsSheetRows.of(plain, quiet, picture = false),
            "on a profile or in a search, where a hidden post of a muted account still shows",
        )
    }

    @Test
    fun theSheetListsWhatIsDoneWithThePostThenItsListsAndLastWhatPutsItAway() {
        val plain = post("1").copy(likeCount = 4)
        assertEquals(
            listOf(listOf(ThreadsSheetRow.TRANSLATE, ThreadsSheetRow.COPY_TEXT, ThreadsSheetRow.COPY_LINK, ThreadsSheetRow.SHARE_IMAGE), listOf(ThreadsSheetRow.LIKED_BY)),
            ThreadsSheetRows.above(plain, picture = true, translate = ThreadsSheetRow.TRANSLATE, lists = ThreadsSheetRows.lists(plain, signedIn = true)),
            "translate first where it is offered, the lists on Threads a group of their own",
        )
        assertEquals(emptyList(), ThreadsSheetRows.lists(plain, signedIn = false), "a guest is offered no list that would only say sign in")
        assertEquals(emptyList(), ThreadsSheetRows.lists(post("2"), signedIn = true), "nor is anyone where Meta counted nothing")
        assertEquals(
            emptyList(),
            ThreadsSheetRows.above(plain.copy(text = "", url = null), picture = false),
            "a group with nothing in it is left out, so no divider stands alone",
        )
        assertEquals(listOf(ThreadsSheetRow.HIDE, ThreadsSheetRow.MUTE), ThreadsSheetRows.below(plain, ThreadsFeedOptions()), "hiding and muting come last, under the app's entries")
        assertEquals(listOf(ThreadsSheetRow.UNMUTE), ThreadsSheetRows.below(plain, ThreadsFeedOptions().mutingAccount("zuck").hiding("1")))
        assertEquals(ThreadsSheetRow.SHOW_ORIGINAL, ThreadsSheetRows.translation(shown = true, offered = true))
        assertEquals(ThreadsSheetRow.TRANSLATE, ThreadsSheetRows.translation(shown = false, offered = true))
        assertEquals(null, ThreadsSheetRows.translation(shown = false, offered = false))
    }

    @Test
    fun theFootersWayToTheWebGivesWayOnANarrowCardAndLinesMirrorRightToLeft() {
        assertEquals(5, ThreadsCardRules.footerShown(listOf(60, 60, 60, 48, 48), 300, web = true), "all of them while they fit")
        assertEquals(4, ThreadsCardRules.footerShown(listOf(66, 66, 60, 48, 48), 244, web = true), "a 320dp phone with long counts: the sheet has the way to the web too")
        assertEquals(4, ThreadsCardRules.footerShown(listOf(66, 66, 60, 60), 244, web = false), "nothing else is ever left out")
        assertEquals(1, ThreadsCardRules.footerShown(listOf(400), 244, web = true))
        assertEquals(20f, ThreadsCardRules.mirrored(20f, 300f, rtl = false))
        assertEquals(280f, ThreadsCardRules.mirrored(20f, 300f, rtl = true), "the rail through the avatar, on the right in Arabic")
    }

    // ---- An author's thread read as one article ----

    @Test
    fun anAuthorsOwnThreadReadsAsOneArticle() {
        val conversation = ThreadsConversation(
            focus = post("2", text = "second"),
            ancestors = listOf(post("0", handle = "meta", text = "asked"), post("1", text = "first")),
            continuation = listOf(post("3", text = "third"), post("4", text = ""), post("5", handle = "meta", text = "butting in"), post("6", text = "after")),
            replies = listOf(listOf(post("r", text = "reply"))),
            loaded = true,
        )

        val article = assertNotNull(ThreadsArticles.of(conversation))
        assertEquals(listOf("1", "2", "3", "4"), article.posts.map { it.id }, "the author's run around the opened post, as far as each is theirs")
        assertEquals("1", article.first.id)
        assertEquals("Zuck\n\nfirst\n\nsecond\n\nthird", article.spokenText(), "the author once, then the words; a post of pictures alone says nothing")
        assertTrue(ThreadsArticles.reading(asked = true, article))
        assertFalse(ThreadsArticles.reading(asked = false, article))
    }

    @Test
    fun aLonePostAnUnreadPageOrANamelessAuthorIsNoArticle() {
        val thread = ThreadsConversation(focus = post("1"), continuation = listOf(post("2")), loaded = true)

        assertNotNull(ThreadsArticles.of(thread))
        assertNull(ThreadsArticles.of(thread.copy(loaded = false)), "the tapped card alone, before its page is read")
        assertNull(ThreadsArticles.of(thread.copy(continuation = emptyList())))
        assertNull(ThreadsArticles.of(ThreadsConversation(focus = post("1"), ancestors = listOf(post("0", handle = "meta")), loaded = true)), "a reply to someone else")
        assertNull(ThreadsArticles.of(ThreadsConversation(focus = post("1", handle = ""), continuation = listOf(post("2", handle = "")), loaded = true)))
        assertFalse(ThreadsArticles.reading(asked = true, article = null), "asked for, then the page read again without the thread: the conversation shows")
    }

    // ---- A post as a picture ----

    @Test
    fun aPictureIsWrittenAsAPngOverTheScreensBackground() {
        val white = 0xFFFFFFFF.toInt()
        val pixels = intArrayOf(
            0xFFFF0000.toInt(), 0x00000000, 0x80000000.toInt(),
            0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF123456.toInt(),
        )

        val png = assertNotNull(ThreadsPng.encode(3, 2, pixels, white))
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(png)), "a PNG any reader opens")
        assertEquals(3 to 2, image.width to image.height)
        fun at(x: Int, y: Int) = image.getRGB(x, y) and 0xFFFFFF
        assertEquals(0xFF0000, at(0, 0))
        assertEquals(0xFFFFFF, at(1, 0), "a clear pixel is the background")
        assertEquals(0x7F7F7F, at(2, 0), "half black over white")
        assertEquals(listOf(0x00FF00, 0x0000FF, 0x123456), listOf(at(0, 1), at(1, 1), at(2, 1)))
        assertFalse(image.colorModel.hasAlpha(), "nothing see-through: it is pasted anywhere")
    }

    @Test
    fun aPictureThatIsNotThereOrTooLargeIsNotTaken() {
        assertNull(ThreadsPng.encode(0, 4, IntArray(0), 0))
        assertNull(ThreadsPng.encode(2, 2, IntArray(3), 0), "fewer pixels than its size says")
        assertFalse(ThreadsPng.fits(-1, 10))
        assertTrue(ThreadsPng.fits(1440, 12_000))
        assertFalse(ThreadsPng.fits(4_000, 7_000))
        assertEquals(0x102030, ThreadsPng.over(0xFF102030.toInt(), 0xFFFFFFFF.toInt()))
        assertEquals(0x000000, ThreadsPng.over(0x00FFFFFF, 0xFF000000.toInt()))
    }

    @Test
    fun aPostsPictureIsNamedAfterItsAuthorAndItsCode() {
        assertEquals("threads-zuck-DAbC-12_x.png", ThreadsPng.name(post("9").copy(url = "https://www.threads.com/@zuck/post/DAbC-12_x")))
        assertEquals("threads-zuck-9.png", ThreadsPng.name(post("9").copy(url = null)))
        assertEquals("image/png", ThreadsPng.MIME)
    }

    // ---- The lists whose videos take turns at one player ----

    @Test
    fun listsOfPostsShareOnePlayerAndAnOpenedPostDoesNot() {
        val lists = listOf(
            ThreadsRoute.Profile("zuck"),
            ThreadsRoute.Search(ThreadsSearchTerms("cats", tag = false)),
            ThreadsRoute.Community(ThreadsTopic("Books", community = true)),
            ThreadsRoute.Quotes("123", null),
            ThreadsRoute.OwnFeed(ThreadsOwnFeed.entries.first()),
            ThreadsRoute.Saved,
            ThreadsRoute.Activity,
        )
        val others = listOf(
            ThreadsRoute.Post("https://www.threads.com/@zuck/post/C1", "zuck"),
            ThreadsRoute.Media("key", 0),
            ThreadsRoute.People("zuck", ThreadsPeopleList.FOLLOWERS),
            ThreadsRoute.Likers("123", null),
            ThreadsRoute.Reposters("123", null),
            ThreadsRoute.SignIn,
        )

        assertTrue(lists.all(ThreadsFeedVideo::shared))
        assertTrue(others.none(ThreadsFeedVideo::shared))
    }
}
