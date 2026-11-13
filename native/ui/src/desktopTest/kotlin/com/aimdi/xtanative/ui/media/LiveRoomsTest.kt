package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.services.links.XLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Where broadcasts and Spaces open (live_player_screen.dart, media_grid_lightbox.dart). */
class LiveRoomsTest {
    private val maya = XUser("1", "maya", "Maya Rivers")

    private fun opened(live: XLive): List<String> {
        val seen = ArrayList<String>()
        LiveRooms.open(live, player = { seen += "player ${it.broadcastId ?: it.spaceId ?: it.mediaKey}" }, browser = { seen += "browser $it" })
        return seen
    }

    @Test
    fun aRoomXCanBeAskedAboutPlaysInTheApp() {
        assertEquals(listOf("player 1abc"), opened(XLive(broadcastId = "1abc")))
        assertEquals(listOf("player 1room"), opened(XLive(spaceId = "1room")))
        assertEquals(listOf("player 28_1"), opened(XLive(mediaKey = "28_1")))
    }

    @Test
    fun aRoomWithNothingToAskWithOpensNowhereInTheApp() {
        assertEquals(emptyList(), opened(XLive(imageUrl = "https://card/live.jpg")))
    }

    @Test
    fun aLinkNamesItsRoom() {
        assertEquals(XLive(spaceId = "1room"), LiveRooms.of(XLink.Live("https://x.com/i/spaces/1room", isSpace = true)))
        assertEquals(XLive(broadcastId = "1abc"), LiveRooms.of(XLink.Live("https://x.com/i/broadcasts/1abc", isSpace = false)))
    }

    @Test
    fun aGridsBroadcastWithoutARecordingOpensThePlayer() {
        val live = XLive(broadcastId = "1live")
        val tile = MediaGridItems.of(XPost("4", maya, null, "text", live = live)).single()
        assertEquals(live, LiveRooms.ofTile(tile))
        val recorded = XMedia(XMedia.Kind.VIDEO, "https://pbs.twimg.com/v.jpg", 16, 9, variants = listOf(XVideoVariant("https://video.twimg.com/v.mp4", 832_000, "video/mp4")))
        val vod = MediaGridItems.of(XPost("3", maya, null, "text", media = listOf(recorded), live = XLive(broadcastId = "1abc"))).single()
        assertNull(LiveRooms.ofTile(vod), "its recording plays in the viewer")
        val unknown = MediaGridItems.of(XPost("5", maya, null, "text", live = XLive(imageUrl = "https://card/live.jpg"))).single()
        assertNull(LiveRooms.ofTile(unknown), "its post opens")
    }
}
