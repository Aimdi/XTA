package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.PluginDownload
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Pixiv's pages as the app's downloads save them (pixiv_download.dart): each
 * original, from the reader's image server, named and foldered as their
 * template says. A work's pages go as one batch, with one notice for them
 * all, as XTA saves them ([com.aimdi.xtanative.ui.plugins.PluginHost.downloadAll]).
 */
internal object PixivDownloads {
    /** [pages] of [illust] as the files a batch saves. */
    fun pageFiles(runtime: PixivRuntime, illust: PixivIllust, pages: List<Int>): List<PluginDownload> {
        val naming = PixivSaveNaming.of(runtime.settings.now)
        val host = runtime.settings[PixivPrefs.imageHost]
        return pages.map { page -> PluginDownload(PixivImages.url(illust.downloadUrlAt(page), host), naming.pageName(illust, page)) }
    }

    /** The name and type [page] of [illust] is shared under, from the file [url] serves, as Pixiv names its own (pixiv_share_image.dart). */
    fun sharedName(illust: PixivIllust, page: Int, url: String): Pair<String, String> {
        val extension = when (url.toHttpUrlOrNull()?.pathSegments?.lastOrNull().orEmpty().substringAfterLast('.', "").lowercase()) {
            "png" -> "png"
            "gif" -> "gif"
            "webp" -> "webp"
            else -> "jpg"
        }
        return "${illust.id}_p$page.$extension" to if (extension == "jpg") "image/jpeg" else "image/$extension"
    }
}
