package com.aimdi.xtanative.ui.post

/** Where a post's parts lead. Reading only: nothing here writes to X. */
class PostActions(
    val openPost: (id: String) -> Unit,
    val openProfile: (screenName: String) -> Unit,
    val openMedia: (postId: String, index: Int) -> Unit,
    val search: (query: String) -> Unit,
)
