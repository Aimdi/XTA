package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The API's search listings, as XTA's reddit_search_html_test reads them (reddit_search_json.dart). */
class RedditSearchJsonTest {
    @Test
    fun postsAreReadFromT3Children() {
        val posts = RedditJson.searchPosts(
            J.parse(
                """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"abc123","title":"Hu Tao build",""" +
                    """"subreddit":"Genshin_Impact","permalink":"/r/Genshin_Impact/comments/abc123/hu_tao/","author":"someone","score":12,"num_comments":4}}]}}""",
            ),
        )

        val post = posts.single()
        assertEquals("abc123", post.id)
        assertEquals("Hu Tao build", post.title)
        assertEquals("Genshin_Impact", post.subreddit)
        assertEquals(4, post.commentCount)
    }

    @Test
    fun subredditsAreReadFromT5Children() {
        val results = RedditJson.searchSubreddits(
            J.parse(
                """{"kind":"Listing","data":{"children":[{"kind":"t5","data":{"display_name":"HuTaoMains","public_description":"For Hu Tao",""" +
                    """"subscribers":12000,"community_icon":"https://styles.redditmedia.com/t5_ht/styles/communityIcon.png?width=64&s=sig"}}]}}""",
            ),
        )

        assertEquals(RedditSubredditResult("HuTaoMains", 12000, "For Hu Tao", "https://styles.redditmedia.com/t5_ht/styles/communityIcon.png"), results.single())
    }

    @Test
    fun usersAreReadFromT2Children() {
        val results = RedditJson.searchUsers(J.parse("""{"kind":"Listing","data":{"children":[{"kind":"t2","data":{"name":"hutao","total_karma":321}}]}}"""))

        assertEquals(RedditUserResult("hutao", 321), results.single())
    }

    @Test
    fun aMissingListingYieldsNoneRatherThanThrowing() {
        assertTrue(RedditJson.searchPosts(J(null)).isEmpty())
        assertTrue(RedditJson.searchSubreddits(J.parse("\"nope\"")).isEmpty())
        assertTrue(RedditJson.searchUsers(J.parse("{}")).isEmpty())
    }
}
