package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineSavedTest {
    private fun content(text: String) = SavedContent.of(json(text))

    @Test
    fun anXPostKeepsItsPicturesAndItsVideoAtTheHighestBitrate() {
        val tweet = content(
            """{"id_str":"7","full_text":"<b>Look</b> at this","possibly_sensitive":true,"user":{"id_str":"1","screen_name":"amy","name":"Amy"},
            "extended_entities":{"media":[{"type":"photo","media_url_https":"https://pbs.twimg.com/p.jpg"},
            {"type":"video","media_url_https":"https://pbs.twimg.com/poster.jpg","video_info":{"variants":[
              {"content_type":"application/x-mpegURL","url":"https://video.twimg.com/v.m3u8"},
              {"content_type":"video/mp4","bitrate":256000,"url":"https://video.twimg.com/low.mp4"},
              {"content_type":"video/mp4","bitrate":2176000,"url":"https://video.twimg.com/high.mp4"}]}}]}}""",
        )
        assertEquals(
            listOf(OfflineMediaSource("https://pbs.twimg.com/p.jpg"), OfflineMediaSource("https://video.twimg.com/high.mp4", video = true)),
            OfflineSaved.media(tweet),
        )
        assertTrue(OfflineSaved.sensitive(tweet))
        assertEquals("Look at this", OfflineSaved.title(tweet))
        assertEquals("amy", OfflineSaved.source(tweet))
        assertEquals("https://x.com/i/status/7", OfflineSaved.url(tweet))
        assertEquals("saved:7", OfflineSaved.idFor("7"))
    }

    @Test
    fun aThreadsPostKeepsItsPlayableVideoInPlaceOfItsPoster() {
        val threads = content(
            """{"xtaPlugin":"threads","post":{"id":"1","handle":"maya","text":"kiln","images":["https://cdn.example/a.jpg","https://cdn.example/b.jpg"],
            "videoUrls":[null,"https://cdn.example/b.mp4"]}}""",
        )
        assertEquals(listOf(OfflineMediaSource("https://cdn.example/a.jpg"), OfflineMediaSource("https://cdn.example/b.mp4", video = true)), OfflineSaved.media(threads))
        assertEquals("maya", OfflineSaved.source(threads))
    }

    @Test
    fun aRedditPostKeepsItsVideoElseItsGalleryElseAPictureLink() {
        fun reddit(extra: String) = content("""{"xtaPlugin":"reddit","post":{"id":"r","title":"T","subreddit":"pics","permalink":"/r/pics/r","over18":true$extra}}""")
        assertEquals(listOf(OfflineMediaSource("https://v.redd.it/x/DASH_720.mp4", video = true)), OfflineSaved.media(reddit(""","videoFallbackUrl":"https://v.redd.it/x/DASH_720.mp4"""")))
        assertEquals(2, OfflineSaved.media(reddit(""","galleryImages":["https://i.redd.it/1.jpg","https://i.redd.it/2.jpg"]""")).size)
        assertEquals(listOf(OfflineMediaSource("https://i.redd.it/cat.PNG")), OfflineSaved.media(reddit(""","url":"https://i.redd.it/cat.PNG"""")))
        assertTrue(OfflineSaved.media(reddit(""","url":"https://example.com/article"""")).isEmpty())
        assertTrue(OfflineSaved.sensitive(reddit("")))
        assertEquals("https://www.reddit.com/r/pics/r", OfflineSaved.url(reddit("")))
    }

    @Test
    fun aMastodonWarningMakesThePostSensitiveAndALongTitleIsCut() {
        val mastodon = content(
            """{"xtaPlugin":"mastodon","post":{"version":1,"id":"1","acct":"a@b.example","url":"https://b.example/@a/1","text":"${"x".repeat(400)}",
            "spoiler":"spoilers","images":["https://b.example/i.png"]}}""",
        )
        assertTrue(OfflineSaved.sensitive(mastodon))
        assertEquals(301, OfflineSaved.title(mastodon).length)
        assertTrue(OfflineSaved.title(mastodon).endsWith("…"))
        assertTrue(OfflineSaved.hasMedia(mastodon))
    }

    @Test
    fun aPostWithNothingToKeepOffersNothing() {
        assertFalse(OfflineSaved.hasMedia(SavedContent.EMPTY))
        assertNull(OfflineSaved.url(SavedContent.EMPTY))
        assertFalse(OfflineSaved.hasMedia(content("""{"xtaPlugin":"bluesky","post":{"uri":"at://x","images":["https://cdn/x.jpg"]}}""")))
    }
}
