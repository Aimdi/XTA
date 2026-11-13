package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.TextRun
import kotlinx.serialization.json.JsonElement

/**
 * Pixiv's works among other networks' posts (a group's feed) and in the app's
 * Saved: a work or novel as XTA's link record (pixiv_post_actions.dart's
 * showPixivPageSheet), and a work drawn by the shared row as XTA's tile
 * shows it in a group, from the image server the reader picked.
 */
internal object PixivShared {
    private const val SOURCE = "pixiv"
    private const val PARAGRAPH = "\n\n"

    fun archive(illust: PixivIllust): PluginPostArchive =
        PluginPostArchive.link(SOURCE, illust.url, illust.userName, words(illust.title, illust.caption), illust.viewerUrls)

    fun archive(novel: PixivNovel): PluginPostArchive =
        PluginPostArchive.link(SOURCE, novel.url, novel.user.name, words(novel.title, novel.caption), listOfNotNull(novel.coverUrl))

    /** The shared post sheet's part of a work's or novel's sheet. */
    fun menu(illust: PixivIllust): PluginPostMenu = PluginPostMenu(archive(illust), illust.url)

    fun menu(novel: PixivNovel): PluginPostMenu = PluginPostMenu(archive(novel), novel.url)

    /** A record of this plugin's in Saved, the id and author it was kept under; null for another. */
    fun record(content: JsonElement): PluginPostArchive? = PluginPostArchive.ofLink(content, SOURCE)

    /**
     * [illust] in the shared row (pixiv_group.dart's tile): its creator, title
     * and first page, its bookmarks, comments and views, its pictures and
     * its creator's from [imageHost], which the shared row asks for with
     * the Referer Pixiv's server wants.
     */
    fun post(illust: PixivIllust, imageHost: String): NetworkPost = NetworkPost(
        id = "${illust.id}",
        authorId = "${illust.userId}",
        authorName = illust.userName,
        authorHandle = illust.userAccount,
        avatarUrl = illust.userAvatarUrl?.let { PixivImages.url(it, imageHost) },
        createdAt = illust.createdAt,
        media = listOf(
            MediaItem(
                PixivImages.url(illust.viewerUrls.first(), imageHost),
                aspectRatio = illust.aspectRatio.toFloat(),
                thumbUrl = PixivImages.url(illust.thumbnailUrl, imageHost),
            ),
        ),
        replies = illust.totalComments,
        likes = illust.totalBookmarks,
        views = illust.totalViews,
        url = illust.url,
        title = illust.title.ifEmpty { null },
    )

    /** A work or novel Saved kept ([archive], its link record), as the shared row draws it: its creator, its title over its caption, its pages. */
    fun saved(archive: PluginPostArchive, imageHost: String): NetworkPost {
        val record = J(archive.content)
        val words = record["text"].string.orEmpty()
        return NetworkPost(
            id = archive.id,
            authorId = archive.authorId.orEmpty(),
            authorName = record["author"].string.orEmpty(),
            authorHandle = archive.authorId.orEmpty(),
            text = words.substringAfter(PARAGRAPH, "").takeIf { it.isNotBlank() }?.let { listOf(TextRun.Plain(it)) }.orEmpty(),
            media = record["images"].list.mapNotNull { it.string?.takeIf(String::isNotEmpty) }.take(MAX_PAGES).map { MediaItem(PixivImages.url(it, imageHost)) },
            url = record["url"].string,
            title = words.substringBefore(PARAGRAPH).takeIf { it.isNotBlank() },
        )
    }

    /** A title and caption as XTA joins them: the blank ones left out. */
    private fun words(title: String, caption: String): String = listOf(title, caption).filter { it.isNotBlank() }.joinToString(PARAGRAPH)

    /** The pages a kept work shows, as the shared tiles lay out four at most. */
    private const val MAX_PAGES = 4
}
