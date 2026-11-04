package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.ui.downloads.Downloading

/**
 * What a post's footer and menus do with the device's own saves, likes and
 * downloads, as XTA's tweet footer and media menus do. None of it reaches X:
 * a save is a row of Saved, a like a heart this device remembers.
 *
 * These are the actions; the bookmark and the heart to draw (with the save
 * sheet a long press opens), "Write a note" about a post, the note on a saved
 * one, and the state of a post are composables on it in PostKeepViews.kt:
 * `actions.keep.SaveButton(post)`.
 */
class PostKeep {
    /** The bookmark tapped: saves [post] where a plain tap files (the remembered folder, else unfiled), or removes the save. */
    fun toggleSave(graph: AppGraph, post: XPost) = Keeping.toggleSave(graph, Keepable.of(post))

    /** Files [post] in [folderId] (null for unfiled), saving it first, as a chip of the save sheet does. */
    fun file(graph: AppGraph, post: XPost, folderId: String?) = Keeping.fileFromSheet(graph, Keepable.of(post), folderId)

    /** The heart tapped: [post] liked on this device, or no longer. */
    fun toggleLike(graph: AppGraph, post: XPost) = Keeping.toggleLike(graph, Keepable.of(post))

    /** A double-tap on [post], with Settings › Accessibility's "Double-tap to like" on: liked on this device unless it is already. */
    fun likeHere(graph: AppGraph, post: XPost) = Keeping.likeHere(graph, Keepable.of(post))

    /** Saves the media at [index] of [post] where the download setting says, and says how it went. */
    fun download(graph: AppGraph, post: XPost, index: Int) = Downloading.media(graph, post, index)
}
