package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVideoVariant
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class XtaTweetJsonTest {
    /** A post as XTA's Saved keeps it: `TweetWithCard.toJson`. */
    private val kept = J(
        json(
            """{"created_at":"2026-03-01T10:20:30.000Z","id_str":"100","full_text":"Look https://t.co/x @bob #tag",
            "display_text_range":[0,29],"conversationIdStr":"90","viewCount":1234,
            "user":{"id_str":"7","screen_name":"amy","name":"Amy","profile_image_url_https":"https://pbs.twimg.com/a_normal.jpg","verified":true},
            "entities":{"urls":[{"url":"https://t.co/x","expanded_url":"https://example.com/x","display_url":"example.com/x","indices":[5,19]}],
              "user_mentions":[{"screen_name":"bob","indices":[20,24]}],"hashtags":[{"text":"tag","indices":[25,29]}],"symbols":[]},
            "extended_entities":{"media":[{"id_str":"m1","type":"photo","media_url_https":"https://pbs.twimg.com/media/p.jpg",
              "url":"https://t.co/m","sizes":{"large":{"w":800,"h":600,"resize":"fit"}}}]},
            "mediaAltText":{"m1":"A cat"},
            "reply_count":1,"retweet_count":2,"quote_count":3,"favorite_count":4,
            "quotedStatusWithCard":{"id_str":"50","full_text":"quoted","created_at":"2026-02-01T00:00:00.000Z","user":{"id_str":"8","screen_name":"cid","name":"Cid"}},
            "card":{"name":"summary_large_image","url":"https://t.co/x","binding_values":{"title":{"string_value":"Example"},
              "card_url":{"string_value":"https://t.co/x"},"summary_photo_image_large":{"image_value":{"url":"https://img.example/x.jpg","width":100,"height":50}}}}}""",
        ),
    )

    @Test
    fun aPostKeptByXtaReadsAsAFetchedOne() {
        val post = XtaTweetJson.decode(kept)!!
        assertEquals("100", post.id)
        assertEquals("amy", post.author.screenName)
        assertEquals(true, post.author.verified)
        assertEquals(Instant.parse("2026-03-01T10:20:30Z"), post.createdAt)
        assertEquals(listOf(XEntity.Link(5, 19, "https://t.co/x", "https://example.com/x", "example.com/x"), XEntity.Mention(20, 24, "bob"), XEntity.Hashtag(25, 29, "tag")), post.entities)
        assertEquals("A cat", post.media.single().alt)
        assertEquals(800, post.media.single().width)
        assertEquals(listOf(1L, 2L, 3L, 4L), listOf(post.replyCount, post.repostCount, post.quoteCount, post.likeCount))
        assertEquals(1234L, post.viewCount)
        assertEquals("90", post.conversationId)
        assertEquals("50", post.quoted?.id)
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), post.quoted?.createdAt)
        val card = assertIs<XCard.Link>(post.card)
        assertEquals("https://example.com/x", card.url)
        assertEquals("https://img.example/x.jpg", card.imageUrl)
    }

    @Test
    fun aLongPostReadsItsNoteText() {
        val long = J(json("""{"id_str":"5","full_text":"Short…","noteText":"The whole long text","user":{"id_str":"1","screen_name":"a","name":"A"}}"""))
        assertEquals("The whole long text", XtaTweetJson.decode(long)?.text)
    }

    @Test
    fun somethingThatIsNoTweetReadsAsNoPost() {
        assertNull(XtaTweetJson.decode(J(json("[1,2]"))))
        assertNull(XtaTweetJson.decode(J(json("""{"id_str":"5","full_text":"no author"}"""))))
    }

    @Test
    fun aPostSavedHereReadsBackAsItWas() {
        val author = XUser("7", "amy", "Amy", "https://pbs.twimg.com/a_normal.jpg", verified = true)
        val reposted = XPost("41", XUser("9", "dan", "Dan"), Instant.parse("2026-01-02T03:04:05Z"), "original", displayEnd = 8)
        val post = XPost(
            id = "42",
            author = author,
            createdAt = Instant.parse("2026-03-01T10:20:30Z"),
            text = "Poll time #q",
            displayStart = 0,
            displayEnd = 12,
            entities = listOf(XEntity.Hashtag(10, 12, "q")),
            media = listOf(
                XMedia(
                    XMedia.Kind.VIDEO, "https://pbs.twimg.com/poster.jpg", 1280, 720, alt = "A clip",
                    variants = listOf(XVideoVariant("https://video.twimg.com/v.mp4", 832000, "video/mp4")), durationMs = 5000, shortUrl = "https://t.co/v",
                ),
            ),
            card = XCard.Poll(listOf(XCard.Poll.Choice("Yes", 10), XCard.Poll.Choice("No", 5)), Instant.parse("2026-03-02T10:20:30Z"), final = true),
            reposted = reposted,
            replyCount = 3,
            likeCount = 9,
            viewCount = 77,
            replyToScreenName = "bob",
            conversationId = "40",
        )
        assertEquals(post, XtaTweetJson.decode(J(XtaTweetJson.encode(post))))
    }
}
