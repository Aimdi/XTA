package com.aimdi.xtanative.plugins.tiktok

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.tiktok.TikTokAuthor
import com.aimdi.xtanative.networks.tiktok.TikTokClient
import com.aimdi.xtanative.networks.tiktok.TikTokPost
import com.aimdi.xtanative.networks.tiktok.TikTokVideoSource
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TikTokRoutesTest {
    @Test
    fun eachScreenOpensFromItsRoute() {
        assertEquals(TikTokRoute.Profile("nba"), TikTokRoutes.parse(TikTokRoutes.profile("@NBA")))
        assertEquals(TikTokRoute.Video("7400000000000000001", "nba", "k"), TikTokRoutes.parse(TikTokRoutes.video("7400000000000000001", "nba", "k")))
        assertEquals(TikTokRoute.Search("dance"), TikTokRoutes.parse(TikTokRoutes.search(" dance ")))
        assertNull(TikTokRoutes.parse("video?id=abc"))
        assertNull(TikTokRoutes.parse("profile?handle=no/handle"))
    }

    @Test
    fun aProfileLinkOpensHereAndAVideosOnlyWhereThereIsAPlayer() {
        assertEquals(TikTokRoutes.profile("nba"), TikTokRoutes.forUrl("https://www.tiktok.com/@nba", inApp = false))
        val video = "https://www.tiktok.com/@nba/video/7400000000000000001"
        assertEquals(TikTokRoutes.video("7400000000000000001", "nba", null), TikTokRoutes.forUrl(video, inApp = true))
        assertNull(TikTokRoutes.forUrl(video, inApp = false), "the desktop opens a video on the site")
        assertNull(TikTokRoutes.forUrl("https://vm.tiktok.com/ZMabc/", inApp = true))
    }

    @Test
    fun failuresReadAsXtaTellsThem() {
        assertEquals(Strings.error_not_found, TikTokText.error(NetworkRequestException(TikTokClient.NETWORK, TikTokClient.NOT_FOUND, "404")))
        assertEquals(Strings.error_rate_limited, TikTokText.error(NetworkRequestException(TikTokClient.NETWORK, TikTokClient.RATE_LIMITED, "429")))
        assertEquals(Strings.error_response, TikTokText.error(NetworkRequestException(TikTokClient.NETWORK, TikTokClient.UNREADABLE, "502")))
        assertEquals(Strings.error_network, TikTokText.error(IOException("offline")))
        assertEquals(Strings.error_network, TikTokText.problem(Problem.Other))
    }

    @Test
    fun thePlayerOffersTheRenditionsAndTheDownloadOnlyWhenThereIsNothingElse() {
        val author = TikTokAuthor("nba", "NBA")
        val ladder = TikTokPost(
            "1", "", null, author,
            sources = listOf(TikTokVideoSource("https://v/720", "720p"), TikTokVideoSource("https://v/540", "540p"), TikTokVideoSource("https://v/dl", "download")),
        )
        assertEquals(listOf("720p", "540p"), renditionsOf(ladder).map { it.label })
        val single = TikTokPost("2", "", null, author, sources = listOf(TikTokVideoSource("https://v/dl", "download")))
        assertEquals(listOf("https://v/dl"), renditionsOf(single).map { it.url })
        assertEquals(emptyList(), renditionsOf(TikTokPost("3", "", null, author, coverUrl = "https://c")))
    }
}
