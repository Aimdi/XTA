package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.ui.saved.PostKeep

/** Where a post's parts lead. Reading only: nothing here writes to X. */
class PostActions(
    val openPost: (id: String) -> Unit,
    val openProfile: (screenName: String) -> Unit,
    val openMedia: (postId: String, index: Int) -> Unit,
    val search: (query: String) -> Unit,
    /** The device's own save, like and downloads of a post (ui/saved): nothing of it reaches X. */
    val keep: PostKeep = PostKeep(),
)
