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
    /** The posts quoting a post, and who reposted it: XTA's two tabs behind the repost count. */
    val openQuotes: (postId: String) -> Unit = {},
    val openReposters: (postId: String) -> Unit = {},
    /** The X article a post carries, read in full. */
    val openArticle: (postId: String) -> Unit = {},
    /** Whom a profile follows, or who follows it. */
    val openFollows: (screenName: String, followers: Boolean) -> Unit = { _, _ -> },
    /** A link tapped in a post: an installed plugin's or XTA's own screen when one reads it, else the web (ui.links' LinkOpener.open). */
    val openLink: (url: String) -> Unit = {},
    /** A page handed to a browser as it is: "Open in browser" (LinkOpener.openOutside). */
    val openInBrowser: (url: String) -> Unit = {},
    /** One picture in the media viewer, saved under [username]'s name: a profile's banner or face. */
    val openImage: (url: String, username: String) -> Unit = { _, _ -> },
)
