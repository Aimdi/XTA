package com.aimdi.xtanative.ui.saved

import androidx.compose.runtime.staticCompositionLocalOf
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.downloads.DownloadRequest
import com.aimdi.xtanative.store.saved.LikesStore
import com.aimdi.xtanative.store.saved.SavedStore
import com.aimdi.xtanative.store.settings.SettingsStore

/**
 * What saving and liking on this device need of the app ([Keeping]): the
 * stores they write, a scope that writes each change to its end even when the
 * screen that asked goes away ([keep]), and the download queue a folder's
 * downloads start in. The app makes one from its graph; a post's card and the
 * plugins' bookmarks take it from [LocalKeeper], so they do not depend on the
 * app graph and the screens it holds.
 */
class Keeper(
    val saved: SavedStore,
    val likes: LikesStore,
    val settings: SettingsStore,
    private val writes: (suspend () -> Unit) -> Unit,
    private val downloads: (List<DownloadRequest>) -> Unit,
    private val mediaDownload: (XPost, Int) -> Unit,
    private val madeShare: (ByteArray, String, String) -> Unit = { _, _, _ -> },
) {
    /** [write] run to its end, whatever happens to the screen that asked for it. */
    fun keep(write: suspend () -> Unit) = writes(write)

    /** [requests] queued, as a folder's downloads start when a post is filed in it. */
    fun download(requests: List<DownloadRequest>) = downloads(requests)

    /** The picture or video at [index] of [post] downloaded, as a post's menu asks. */
    fun downloadMedia(post: XPost, index: Int) = mediaDownload(post, index)

    /** A picture the app made ([bytes], a post as an image) handed to the share sheet, or saved where it cannot be shared. */
    fun shareMade(bytes: ByteArray, fileName: String, mime: String) = madeShare(bytes, fileName, mime)
}

/** The app's [Keeper], for the parts that save and like: a post's card, its menu, the bookmarks plugins draw. */
val LocalKeeper = staticCompositionLocalOf<Keeper> { error("No Keeper provided") }
