package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.services.links.XLink

/** Where a broadcast or a Space opens (live_player_screen.dart's openLivePlayer). */
object LiveRooms {
    /**
     * The live player ([player]) when X can be asked for [live]'s stream,
     * else its page in a browser by name ([browser]); nothing when it has neither.
     */
    fun open(live: XLive, player: (XLive) -> Unit, browser: (String) -> Unit) {
        if (live.canResolve) player(live) else live.watchUrl?.let(browser)
    }

    /** The live room a link names: a Space, else a broadcast. */
    fun of(link: XLink.Live): XLive = XLive.fromUrl(link.url)

    /**
     * The live room a media grid's tile opens in the player
     * (media_grid_lightbox.dart): a broadcast or Space with no recording for
     * the viewer, that X can be asked about. Null for every other tile, and
     * for a room X cannot be asked about, whose post opens instead.
     */
    fun ofTile(item: MediaGridItem): XLive? =
        item.post.live?.takeIf { item.kind == MediaGridItem.Kind.BROADCAST && item.page == null && it.canResolve }
}
