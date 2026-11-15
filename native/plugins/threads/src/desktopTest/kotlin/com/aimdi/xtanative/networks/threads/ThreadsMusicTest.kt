package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A post's song under every shape it is seen or likely in; all values made up, a missing key nothing. */
class ThreadsMusicTest {
    private fun music(info: Any?): ThreadsMusic? = ThreadsParser.post(j(postJson("1", extra = mapOf("text_app_music_info" to info))))!!.facts.music

    private fun manifest(vararg sets: String, duration: String = "PT1M2.5S") =
        """<?xml version="1.0"?><MPD xmlns="urn:mpeg:dash:schema:mpd:2011" mediaPresentationDuration="$duration"><Period>${sets.joinToString("")}</Period></MPD>"""

    private fun audioSet(vararg representations: Pair<Long, String>) =
        """<AdaptationSet contentType="audio" mimeType="audio/mp4">""" +
            representations.joinToString("") { (bandwidth, url) -> """<Representation id="a$bandwidth" bandwidth="$bandwidth" codecs="mp4a.40.2"><BaseURL>$url</BaseURL><SegmentBase indexRange="0-100"/></Representation>""" } +
            "</AdaptationSet>"

    /** The shape CI's probe saw on topic pages, with made-up values. */
    private fun seen(asset: Map<String, Any?>, extra: Map<String, Any?> = emptyMap(), outer: Map<String, Any?> = emptyMap()) = mapOf(
        "is_available_for_consumption" to true,
        "muted_reason" to null,
        "music_consumable_video" to mapOf(
            "audio_metadata" to mapOf("audio_asset" to asset),
            "audio_asset_id" to "123",
            "lyrics_usage_type_as_string" to "NONE",
        ) + extra,
    ) + outer

    private val seenAsset = mapOf(
        "display_title" to mapOf("text" to "Made-up Song"),
        "display_artist" to mapOf("text" to "Made-up Band, Someone Else"),
        "display_image" to mapOf("downloadable_uri" to "https://cdn.example/small.jpg"),
        "large_display_image" to mapOf("downloadable_uri" to "https://cdn.example/large.jpg"),
        "song_id" to "456",
        "tags" to listOf("pop"),
        "lyrics" to mapOf("available" to false, "lines" to emptyList<Any>()),
        "dash_manifest" to manifest(audioSet(64_000L to "https://cdn.example/low.mp4?a=1&amp;b=2", 128_000L to "https://cdn.example/high.mp4?a=1&amp;b=2")),
    )

    @Test
    fun theSeenShapeGivesTitleArtistsBothCoversTheBestAudioFileItsLengthAndStart() {
        val song = music(seen(seenAsset, extra = mapOf("audio_start_time_in_ms" to 42_000)))!!
        assertEquals("Made-up Song", song.title)
        assertEquals("Made-up Band, Someone Else", song.artist)
        assertEquals("https://cdn.example/small.jpg", song.imageUrl)
        assertEquals("https://cdn.example/large.jpg", song.largeImageUrl)
        assertEquals("https://cdn.example/large.jpg", song.cover)
        assertEquals("https://cdn.example/high.mp4?a=1&b=2", song.audioUrl, "the best audio representation, its &amp; read as &")
        assertEquals(62_500L, song.durationMs, "from the manifest's mediaPresentationDuration")
        assertEquals(42_000L, song.startMs)
        assertNull(song.lengthMs)
        assertFalse(song.explicit)
        assertTrue(song.playable)
    }

    @Test
    fun instagramsShapeGivesAProgressiveFileFirstAndItsConsumptionWindow() {
        val song = music(
            mapOf(
                "music_asset_info" to mapOf(
                    "title" to "Tune",
                    "display_artist" to "Someone",
                    "cover_artwork_uri" to "https://cdn.example/big.jpg",
                    "cover_artwork_thumbnail_uri" to "https://cdn.example/thumb.jpg",
                    "progressive_download_url" to "https://cdn.example/tune.m4a",
                    "dash_manifest" to manifest(audioSet(1L to "https://cdn.example/other.mp4")),
                    "duration_in_ms" to 180_000,
                    "highlight_start_times_in_ms" to listOf(15_000, 60_000),
                    "audio_asset_id" to "789",
                    "is_explicit" to true,
                ),
                "music_consumption_info" to mapOf("should_mute_audio" to false, "overlap_duration_in_ms" to 20_000, "audio_asset_start_time_in_ms" to 30_000),
            ),
        )!!
        assertEquals("https://cdn.example/tune.m4a", song.audioUrl, "the progressive download before the manifest")
        assertEquals("https://cdn.example/thumb.jpg", song.imageUrl)
        assertEquals("https://cdn.example/big.jpg", song.largeImageUrl)
        assertEquals(180_000L, song.durationMs)
        assertEquals(30_000L, song.startMs, "the consumption's start before the highlights")
        assertEquals(20_000L, song.lengthMs)
        assertTrue(song.explicit)
        val highlight = music(mapOf("music_asset_info" to mapOf("title" to "Tune", "highlight_start_times_in_ms" to listOf(15_000))))!!
        assertEquals(15_000L, highlight.startMs, "the first highlight where nothing else says")
        assertNull(highlight.audioUrl)
        assertFalse(highlight.playable)
    }

    @Test
    fun aMutedOrUnavailableSongIsNamedAndNotPlayed() {
        assertNull(music(seen(seenAsset, outer = mapOf("is_available_for_consumption" to false)))!!.audioUrl)
        val muted = music(mapOf("music_asset_info" to mapOf("title" to "T", "progressive_download_url" to "https://cdn.example/t.m4a"), "music_consumption_info" to mapOf("should_mute_audio" to true)))!!
        assertEquals("T", muted.title)
        assertNull(muted.audioUrl)
    }

    @Test
    fun aManifestThePlayersCannotTakeGivesNoFile() {
        val segments = manifest("""<AdaptationSet contentType="audio"><SegmentTemplate media="${'$'}Number${'$'}.m4s"/><Representation id="1" bandwidth="1"></Representation></AdaptationSet>""")
        assertNull(ThreadsDash.audioFile(segments), "segments alone are no file")
        assertNull(ThreadsDash.audioFile(manifest("""<AdaptationSet contentType="video"><Representation bandwidth="9"><BaseURL>https://cdn.example/v.mp4</BaseURL></Representation></AdaptationSet>""")), "video is not the song")
        assertNull(ThreadsDash.audioFile(manifest(audioSet(1L to "http://cdn.example/plain.mp4"))), "plain http is not loaded")
        assertNull(ThreadsDash.audioFile("https://cdn.example/song.mpd"), "a manifest's address is not a file")
        assertNull(ThreadsDash.audioFile("<MPD"), "a broken manifest is none")
        assertEquals(
            "https://cdn.example/r.mp4",
            ThreadsDash.audioFile("""<MPD><Period><Representation mimeType="audio/mp4" bandwidth="5"><BaseURL>https://cdn.example/r.mp4</BaseURL></Representation></Period></MPD>"""),
            "an audio representation outside any adaptation set",
        )
        assertEquals(3_723_000L, ThreadsDash.durationMs(manifest(duration = "PT1H2M3S")))
        assertNull(ThreadsDash.durationMs(manifest(duration = "soon")))
        val noAudio = music(seen(seenAsset - "dash_manifest"))!!
        assertNull(noAudio.audioUrl)
        assertNull(noAudio.durationMs)
    }

    @Test
    fun oddValuesAreDroppedAndNeverCrash() {
        assertNull(music("muted"))
        assertNull(music(listOf(1, 2)))
        assertNull(music(mapOf("music_asset_info" to mapOf("display_artist" to "No title"))))
        val odd = music(
            mapOf(
                "music_asset_info" to mapOf(
                    "title" to mapOf("text" to "  Spaced  "),
                    "display_artist" to 12,
                    "cover_artwork_uri" to "http://cdn.example/x.jpg",
                    "progressive_download_url" to listOf("https://cdn.example/a.m4a"),
                    "duration_in_ms" to "10000",
                    "highlight_start_times_in_ms" to listOf(50_000),
                    "is_explicit" to "yes",
                    "dash_manifest" to 7,
                ),
            ),
        )!!
        assertEquals(ThreadsMusic("Spaced", durationMs = 10_000), odd, "a start past the song's end is the start; a word for explicit is not a flag")
        assertNull(music(mapOf("music_asset_info" to mapOf("title" to "T", "duration_in_ms" to -5)))!!.durationMs, "a time below zero is no time")
        assertEquals("https://cdn.example/page", music(mapOf("music_asset_info" to mapOf("title" to "T", "song_url" to "https://cdn.example/page")))!!.pageUrl)
    }
}
