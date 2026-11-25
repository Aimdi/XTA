package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.feed.Posts.ids
import com.aimdi.xtanative.core.feed.Posts.post
import com.aimdi.xtanative.core.feed.Posts.thread
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's author_caps_test, feed_chunk_logic_test (dedupe), reply_filter_test and the group feed's page pass, for the native port. */
class FeedShapingTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun capsKeepAtMostSoManyPerAuthorInTheOrderGiven() {
        val threads = listOf(thread(4, by = "a"), thread(3, by = "a"), thread(2, by = "a"), thread(1, by = "b"))
        assertEquals(listOf("4", "3", "1"), ids(PostCaps.perAuthor(threads, mapOf("a" to 2))))
        assertEquals(threads, PostCaps.perAuthor(threads, mapOf("a" to 0)), "a cap of 0 is no cap")
        assertEquals(threads, PostCaps.perAuthor(threads, emptyMap()))
    }

    @Test
    fun zenModeIsNewestFirstWithAFewPostsPerAuthor() {
        val threads = (1L..6L).map { thread(it, by = "a", likes = 7 - it) } + thread(7, by = "b")
        assertEquals(listOf("7", "6", "5", "4", "3"), ids(PostCaps.zen(threads)))
    }

    @Test
    fun theSamePostComesOnceWhateverThreadItCameIn() {
        val first = thread(post(99), id = "chain-1")
        val again = thread(post(99), id = "chain-2")
        val repeated = thread(post(99), id = "chain-1")
        val other = thread(post(100), id = "chain-3")
        assertEquals(listOf("chain-1", "chain-3"), ids(FeedOrder.dedupe(listOf(first, again, repeated, other))))
    }

    @Test
    fun aPopularFeedOrdersByLikesAndARecentOneByTime() {
        val threads = listOf(thread(3, likes = 1), thread(2, likes = 50), thread(1, likes = 50))
        assertEquals(listOf("2", "1", "3"), ids(FeedOrder.byLikes(threads)))
        assertEquals(listOf("3", "2", "1"), ids(FeedOrder.newestFirst(threads.reversed())))
    }

    private fun reply(id: Long, by: String, to: String?) = post(id, by = by, screenName = by, replyTo = to)

    @Test
    fun answeringYourselfIsAThreadNotAReply() {
        assertFalse(ProfileFilters.repliesToSomeoneElse(post(1, by = "1", screenName = "tommy")))
        assertTrue(ProfileFilters.repliesToSomeoneElse(reply(1, "tommy", "someone")))
        assertFalse(ProfileFilters.repliesToSomeoneElse(reply(1, "Tommy", "tommy")))
        assertFalse(ProfileFilters.repliesToSomeoneElse(null))
    }

    @Test
    fun aProfilesHiddenRepliesAndRepostsStayOutAndOnlyThose() {
        val replyToOther = thread(reply(1, "Tommy", "someone"))
        val plain = thread(post(2, by = "Tommy"))
        val selfThread = thread(reply(3, "Tommy", "Tommy"))
        val otherReply = thread(reply(4, "someoneelse", "x"))
        val repost = thread(post(5, by = "Tommy", repostOf = post(50, by = "x")))

        assertEquals(listOf(replyToOther, plain), ProfileFilters.withoutReplies(listOf(replyToOther, plain), emptySet()))
        assertEquals(listOf(plain, selfThread, otherReply, repost), ProfileFilters.withoutReplies(listOf(replyToOther, plain, selfThread, otherReply, repost), setOf("tommy")))
        assertEquals(listOf(replyToOther, plain), ProfileFilters.withoutReposts(listOf(replyToOther, plain, repost), setOf("tommy")), "a reply is not a repost")
    }

    @Test
    fun anAccountHiddenAltogetherStaysOutEvenRepostedButAQuoteOfItIsItsAuthorsPost() {
        val own = thread(post(1, by = "1", screenName = "Tommy"))
        val reposted = thread(post(2, by = "maya", repostOf = post(20, by = "1", screenName = "TOMMY")))
        val itsRepost = thread(post(3, by = "1", screenName = "tommy", repostOf = post(30, by = "x")))
        val quote = thread(post(4, by = "maya", quoted = post(40, by = "1", screenName = "tommy")))
        val other = thread(post(5, by = "maya"))
        val mention = thread(post(6, by = "maya", text = "hello @tommy", replyTo = "tommy"))
        val all = listOf(own, reposted, itsRepost, quote, other, mention)

        assertEquals(listOf(quote, other, mention), ProfileFilters.withoutAuthors(all, setOf("tommy")), "handles are matched whatever their case")
        assertTrue(ProfileFilters.withoutAuthors(all, emptySet()) === all)
        assertFalse(ProfileFilters.hidesAuthor(XThread("empty", emptyList()), setOf("tommy")))

        val shaping = FeedShaping(hiddenAuthors = setOf("tommy"))
        assertEquals(listOf("6", "5", "4"), ids(shaping.shape(all, now).threads), "a feed's shaping leaves them out as it reads")
        assertEquals(listOf("4", "5", "6"), ids(shaping.again(all, now).threads), "and out of what it showed before the account was hidden")
        assertEquals(all.size, FeedShaping().shape(all, now).threads.size)
    }

    private class Languages(val byId: Map<String, String>) : PostFacts {
        override fun sensitive(post: XPost) = false

        override fun language(post: XPost) = byId[post.id]
    }

    @Test
    fun aPageIsHiddenOrderedCappedAndFoldedAsXtaPreparesIt() {
        val shaping = FeedShaping(
            sort = FeedSort.POPULAR,
            rules = FeedRules(minLikes = 2, mutedKeywords = listOf(MutedKeyword("spoilers", action = KeywordAction.FOLD))),
            postCaps = mapOf("a" to 1),
            language = LanguageFilter(listOf("en"), LanguageAction.FOLD),
            hiddenReposts = setOf("c"),
            facts = Languages(mapOf("5" to "de")),
        )
        val threads: List<XThread> = listOf(
            thread(1, by = "a", likes = 10),
            thread(2, by = "a", likes = 20),
            thread(3, by = "b", text = "spoilers", likes = 5),
            thread(4, by = "b", likes = 1),
            thread(post(5, by = "d", likes = 3)),
            thread(post(6, by = "c", likes = 99, repostOf = post(60, by = "x"))),
        )
        val shaped = shaping.shape(threads, now)
        assertEquals(listOf("2", "3", "5"), ids(shaped.threads), "the least liked is hidden, a's cap keeps its most liked, c's repost is hidden")
        assertEquals(mapOf("3" to "spoilers", "5" to "de"), shaped.foldReasons)
    }

    @Test
    fun aLanguagesReasonTakesTheKeywordsPlace() {
        val shaping = FeedShaping(
            rules = FeedRules(mutedKeywords = listOf(MutedKeyword("spoilers", action = KeywordAction.FOLD))),
            language = LanguageFilter(listOf("en"), LanguageAction.FOLD),
            facts = Languages(mapOf("1" to "de")),
        )
        assertEquals(mapOf("1" to "de"), shaping.shape(listOf(thread(1, text = "spoilers")), now).foldReasons)
    }
}
