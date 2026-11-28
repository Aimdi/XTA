package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_export_html
import com.aimdi.xtanative.ui.generated.resources.posts_export_markdown
import com.aimdi.xtanative.ui.saved.ExportFormat
import com.aimdi.xtanative.ui.saved.ExportOnce
import com.aimdi.xtanative.ui.saved.Keepable
import com.aimdi.xtanative.ui.saved.LocalKeeper
import com.aimdi.xtanative.ui.saved.PostExport
import com.aimdi.xtanative.ui.saved.keptPictures
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What a post's "Save as Markdown…" and "Save as printable page…" save: the thread it is drawn in, or the post alone. */
internal object PostExports {
    /** The posts saved for [post]: [shown]'s, when [post] is one of that thread's, else [post] alone. */
    fun posts(post: XPost, shown: XThread?): List<XPost> =
        shown?.posts?.takeIf { posts -> posts.size > 1 && posts.any { it.id == post.id } } ?: listOf(post)

    /** The file's name: whose posts they are and the first one's id. */
    fun fileName(posts: List<XPost>, format: ExportFormat): String {
        val first = posts.first().shown
        return PostExport.fileName(listOf(first.author.screenName, first.id).filter { it.isNotEmpty() }.joinToString(" "), format.extension)
    }

    /** The menu entry (and the save dialog's title) for [format]. */
    fun label(format: ExportFormat): StringResource = when (format) {
        ExportFormat.MARKDOWN -> Res.string.posts_export_markdown
        ExportFormat.HTML -> Res.string.posts_export_html
    }
}

/**
 * Saves [post] (with the rest of its thread, [LocalShownThread]) as
 * [format] through the save dialog, then [onDone]. Pictures link the copies
 * the offline collection keeps, else their addresses; nothing is fetched.
 */
@Composable
internal fun ExportPost(post: XPost, format: ExportFormat, onDone: () -> Unit) {
    val keeper = LocalKeeper.current
    val prefs = postScope().prefs
    val posts = PostExports.posts(post, LocalShownThread.current)
    ExportOnce(stringResource(PostExports.label(format)), PostExports.fileName(posts, format), format, text = {
        val kept = keptPictures(keeper.offline, posts.map { Keepable.of(it).id })
        val exported = posts.map { PostExport.of(it, PostView.url(it, prefs), kept::get) }
        when (format) {
            ExportFormat.MARKDOWN -> PostExport.markdown(null, exported)
            ExportFormat.HTML -> PostExport.html(null, exported)
        }
    }, onDone = onDone)
}
