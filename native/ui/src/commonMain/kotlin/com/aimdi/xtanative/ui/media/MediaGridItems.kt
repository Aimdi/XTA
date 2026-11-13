package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.store.settings.MediaGridLayout

/**
 * One tile of a media grid (media_grid_item.dart): the [index]th photo, GIF
 * or video of [post], or the post's broadcast or Space as one tile. [media]
 * is what the viewer shows of it: the picture or video itself, a
 * broadcast's recording when it has one, else nothing (its post opens).
 */
data class MediaGridItem(
    val post: XPost,
    val index: Int,
    val kind: Kind,
    val media: XMedia?,
    val thumbnailUrl: String?,
    val aspectRatio: Float?,
) {
    /** GIFs are their own tile, with their own badge, but play like videos. */
    enum class Kind { PHOTO, GIF, VIDEO, BROADCAST }

    val key: String get() = post.id + "/" + index

    val isSpace: Boolean get() = post.live?.isSpace == true

    /** Where [media] is among the post's own, which "Open post" scrolls its row to. */
    val mediaIndex: Int get() = if (kind == Kind.BROADCAST) post.media.indexOf(media).coerceAtLeast(0) else index

    /** The viewer's page of this tile; null for a broadcast with nothing to play here. */
    val page: ViewerPage? get() = media?.let { ViewerPage(it, post.id, post.author.screenName, mediaIndex) }

    /** A Livestreams card's title (broadcast_media_card.dart): what the post says, without its links, on one line; null when it says nothing. */
    val excerpt: String? get() = XText.plain(post).replace(LINK, "").replace(SPACES, " ").trim().ifEmpty { null }

    private companion object {
        val LINK = Regex("https?://\\S+")
        val SPACES = Regex("\\s+")
    }
}

/** The tiles of a media grid (media_grid_item.dart's mediaItemsFromChains). */
object MediaGridItems {
    /** Every post's tiles, in the order X sent them; a repost's are the post reposted. */
    fun of(threads: List<XThread>): List<MediaGridItem> = threads.flatMap { it.posts }.flatMap(::of)

    /**
     * [post]'s tiles, a picture each. A broadcast or Space is one tile, thumbed
     * by its video, else its first picture, else the card's picture: a live
     * post with photos must not turn into photos.
     */
    fun of(post: XPost): List<MediaGridItem> {
        val shown = post.shown
        val live = shown.live
        if (live != null) {
            val video = shown.media.firstOrNull { it.kind == XMedia.Kind.VIDEO }
            val thumb = video ?: shown.media.firstOrNull()
            val recording = video?.takeIf { it.variants.isNotEmpty() }
            return listOf(MediaGridItem(shown, 0, MediaGridItem.Kind.BROADCAST, recording, thumb?.url ?: live.imageUrl, thumb?.aspectRatio ?: live.aspectRatio))
        }
        return shown.media.mapIndexed { index, media -> MediaGridItem(shown, index, kindOf(media), media, media.url, media.aspectRatio) }
    }

    /** [items] not already in [seen], which then holds them: successive pages overlap at their edges. */
    fun unseen(items: List<MediaGridItem>, seen: MutableSet<String>): List<MediaGridItem> = items.filter { seen.add(it.key) }

    private fun kindOf(media: XMedia): MediaGridItem.Kind = when (media.kind) {
        XMedia.Kind.PHOTO -> MediaGridItem.Kind.PHOTO
        XMedia.Kind.GIF -> MediaGridItem.Kind.GIF
        XMedia.Kind.VIDEO -> MediaGridItem.Kind.VIDEO
    }
}

/** A grid ready for a list: [items] in [look], placed in their [masonry]. */
class MediaGridPlan(val items: List<MediaGridItem>, val look: MediaGridLook) {
    val masonry: Masonry = Masonry.of(items.map(look::aspectOf), look.columns)
}

/** Space around a grid, in dp. */
data class GridPadding(val start: Float, val top: Float, val end: Float, val bottom: Float)

/**
 * How a media grid lays its tiles out (media_grid.dart's mediaGridConfigOf),
 * in dp: [columns] of tiles [spacing] apart with corners of [radius], each
 * at its own shape kept between [minAspect] and [maxAspect] (a masonry), or
 * one a row for XTA's Timeline layout, a timeline without text. With
 * [broadcastCards] each is a card saying what the broadcast is.
 */
data class MediaGridLook(
    val columns: Int,
    val spacing: Float,
    val radius: Float,
    val padding: GridPadding,
    val minAspect: Float,
    val maxAspect: Float,
    val broadcastCards: Boolean = false,
) {
    /** The shape [item] is drawn at, width over height: its own within the look's bounds, 16:9 when X sent no size. */
    fun aspectOf(item: MediaGridItem): Float {
        val aspect = item.aspectRatio ?: (16f / 9f)
        return if (!aspect.isFinite() || aspect <= 0f) 1f else aspect.coerceIn(minAspect, maxAspect)
    }

    companion object {
        /** Settings › Media's layout: the masonry by its columns, the Timeline one a row, Two per row a roomier masonry. */
        fun of(layout: MediaGridLayout, columns: Int): MediaGridLook = when (layout) {
            MediaGridLayout.FEED -> MediaGridLook(1, 12f, 16f, GridPadding(16f, 8f, 16f, 16f), 0.8f, 1.91f)
            MediaGridLayout.TWO_COLUMNS -> MediaGridLook(2, 8f, 12f, GridPadding(8f, 8f, 8f, 8f), 0.8f, 1.5f)
            MediaGridLayout.MASONRY -> MediaGridLook(columns.coerceIn(1, 5), 2f, 8f, GridPadding(2f, 2f, 2f, 2f), 0.6f, 2f)
        }

        /** A profile's Livestreams: one 16:9 card a row (media_grid.dart's broadcastsOnly). */
        val BROADCASTS = MediaGridLook(1, 16f, 16f, GridPadding(12f, 8f, 12f, 16f), 16f / 9f, 16f / 9f, broadcastCards = true)
    }
}
