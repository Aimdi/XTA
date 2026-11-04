package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.PluginDownload

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
}
