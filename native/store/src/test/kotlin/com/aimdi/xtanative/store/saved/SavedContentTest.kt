package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.store.saved.SavedContent.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedContentTest {
    private val tweet = json(
        """{"id_str":"99","full_text":"Hello from X","user":{"id_str":"1","screen_name":"someone","name":"Some One"},
        "extended_entities":{"media":[{"id_str":"m1","type":"photo","media_url_https":"https://pbs.twimg.com/media/a.jpg"},
        {"id_str":"m2","type":"video","media_url_https":"https://pbs.twimg.com/poster.jpg"}]}}""",
    )

    private val threads = json(
        """{"xtaPlugin":"threads","post":{"id":"3471","handle":"maya","authorName":"Maya Chen",
        "text":"Field notes from the studio: the kiln held.","url":"https://www.threads.com/@maya/post/C9xYz",
        "images":["https://cdn.example/a.jpg","https://cdn.example/b.jpg"],"videoUrls":[null,"https://cdn.example/b.mp4"],"topicTag":"Ceramics"}}""",
    )

    private val mastodon = json(
        """{"xtaPlugin":"mastodon","post":{"version":1,"id":"123","acct":"artist@studio.example","name":"Artist",
        "text":"A garden in spring","url":"https://studio.example/@artist/123","spoiler":"plants","sensitive":true,
        "images":["https://studio.example/garden.png"]}}""",
    )

    private val bluesky = json(
        """{"xtaPlugin":"bluesky","post":{"uri":"at://did:plc:a/app.bsky.feed.post/1","handle":"alice.test","authorName":"Alice",
        "text":"A saved thought","images":["https://cdn.test/image.jpg","https://cdn.test/video.jpg"],"imageIsVideo":[false,true],
        "repostedByDid":"did:plc:b"}}""",
    )

    private val reddit = json(
        """{"xtaPlugin":"reddit","post":{"id":"abc","title":"Who to pull on reunion banner","subreddit":"GirlsFrontline2",
        "permalink":"/r/GirlsFrontline2/comments/abc/who/","author":"Jikikato01","selfText":"Q1 Quihua"}}""",
    )

    @Test
    fun anOrdinaryTweetIsAnXPostSearchedByTextAndAuthor() {
        val content = SavedContent.of(tweet)
        assertEquals(Kind.X, content.kind)
        assertEquals(SavedSource.X, content.source)
        assertTrue(content.matches("hello from x"))
        assertTrue(content.matches("someone"))
        assertTrue(content.hasMedia)
        assertEquals(listOf(SavedPhoto("m1", "https://pbs.twimg.com/media/a.jpg")), content.photos)
        assertEquals(listOf(XMedia.Kind.PHOTO, XMedia.Kind.VIDEO), content.media.map { it.kind }, "uploads take the video too")
        assertEquals("99", content.xPost?.id)
    }

    @Test
    fun aRedditBlobIsReadAsRedditNotAsATweet() {
        val content = SavedContent.of(reddit)
        assertEquals(SavedSource.REDDIT, content.source)
        assertNull(content.xPost)
        assertTrue(content.matches("quihua"))
        assertTrue(content.matches("girlsfrontline2"))
        assertFalse(content.matches("nope"))
        assertFalse(content.hasMedia)
    }

    @Test
    fun aSavedThreadsPostIsFiledUnderThreadsAndOffersItsPicturesNotItsVideo() {
        val content = SavedContent.of(threads)
        assertEquals(SavedSource.THREADS, content.source)
        assertTrue(content.matches("kiln"))
        assertTrue(content.matches("ceramics"))
        assertTrue(content.hasMedia)
        assertEquals(listOf("https://cdn.example/a.jpg"), content.photos.map { it.url })
        assertEquals("threads:https://www.threads.com/@maya/post/C9xYz:0", content.photos.single().id)
    }

    @Test
    fun aThreadsPostSavedAsALinkIsStillAThreadsPost() {
        val link = json("""{"xtaPlugin":"link","source":"threads","url":"https://www.threads.com/@maya/post/C9xYz","author":"maya","text":"kiln","images":[]}""")
        assertEquals(SavedSource.THREADS, SavedContent.of(link).source)
    }

    @Test
    fun aDamagedBlobIsNothingRatherThanACrashOrATweet() {
        assertEquals(Kind.NONE, SavedContent.of(json("""{"xtaPlugin":"threads","post":"nope"}""")).kind)
        assertEquals(Kind.NONE, SavedContent.of(json("""{"xtaPlugin":"bluesky","post":{"uri":12}}""")).kind)
        assertEquals(Kind.NONE, SavedContent.of(json("""{"xtaPlugin":"mastodon","post":null}""")).kind)
        assertNull(SavedContent.of(json("""{"xtaPlugin":"bluesky","post":{"uri":12}}""")).xPost)
        assertEquals(SavedContent.EMPTY, SavedContent.of(null))
    }

    @Test
    fun aMastodonPostKeepsItsWarningAuthorAndTextSearchable() {
        val content = SavedContent.of(mastodon)
        assertTrue(content.matches("garden"))
        assertTrue(content.matches("artist@studio"))
        assertTrue(content.matches("plants"))
        assertTrue(content.from(SavedSource.MASTODON))
        assertFalse(content.from(SavedSource.X))
        assertTrue(content.from(SavedSource.ALL))
        assertTrue(content.hasMedia)
        assertEquals(SavedPhoto("mastodon:https://studio.example/@artist/123:0", "https://studio.example/garden.png"), content.photos.single())
    }

    @Test
    fun aBlueskyPostIsSearchableAndNeverOffersAVideoThumbnailAsAPhoto() {
        val content = SavedContent.of(bluesky)
        assertEquals(SavedSource.BLUESKY, content.source)
        assertTrue(content.matches("alice"))
        assertTrue(content.hasMedia)
        assertEquals(listOf("https://cdn.test/image.jpg"), content.photos.map { it.url })
    }

    @Test
    fun anotherNetworkKeptAsALinkIsOtherAndSearchedByItsLink() {
        val content = SavedContent.of(json("""{"xtaPlugin":"link","source":"substack","url":"https://alice.substack.com/p/remember-this","author":"Alice","text":"Remember this"}"""))
        assertEquals(SavedSource.OTHER, content.source)
        assertEquals("substack", content.linkSource)
        assertTrue(content.matches("remember-this"))
        assertEquals(Kind.NONE, SavedContent.of(json("""{"xtaPlugin":"link","url":"javascript:alert(1)"}""")).kind)
    }

    @Test
    fun aKeptPostOfAnotherNetworkNamesThePluginThatReadsItAndKeepsItsWholeRecord() {
        assertEquals("bluesky", SavedContent.of(bluesky).network)
        assertEquals("reddit", SavedContent.of(reddit).network)
        assertEquals("mastodon", SavedContent.of(mastodon).network)
        assertEquals("threads", SavedContent.of(threads).network)
        val link = json("""{"xtaPlugin":"link","source":"pixiv","url":"https://www.pixiv.net/artworks/1","author":"A","text":"t"}""")
        assertEquals("pixiv", SavedContent.of(link).network)
        assertNull(SavedContent.of(tweet).network)
        assertNull(SavedContent.EMPTY.network)
        assertEquals("abc", SavedContent.of(reddit).record["post"]["id"].string, "the record is the whole blob, not its post")
        assertEquals("reddit", SavedContent.of(reddit).record["xtaPlugin"].string)
    }

    @Test
    fun aRedditPostWithAPictureLinkHasMedia() {
        val picture = json("""{"xtaPlugin":"reddit","post":{"id":"p","title":"t","subreddit":"s","permalink":"/r/s/p","url":"https://i.redd.it/x.png"}}""")
        assertTrue(SavedContent.of(picture).hasMedia)
        assertEquals("https://i.imgur.com/a.gif", SavedContent.redditImageUrl("https://i.imgur.com/a.gifv"))
        assertNull(SavedContent.redditImageUrl("https://example.com/page"))
    }
}
