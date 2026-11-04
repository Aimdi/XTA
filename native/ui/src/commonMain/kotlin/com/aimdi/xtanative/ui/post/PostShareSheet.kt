package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.data.plugins.PluginSaves
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.posts_copied
import com.aimdi.xtanative.ui.generated.resources.posts_share_article_image
import com.aimdi.xtanative.ui.generated.resources.posts_share_article_link
import com.aimdi.xtanative.ui.generated.resources.posts_share_content
import com.aimdi.xtanative.ui.generated.resources.posts_share_content_and_link
import com.aimdi.xtanative.ui.generated.resources.posts_share_image
import com.aimdi.xtanative.ui.generated.resources.reader_link_share_post
import com.aimdi.xtanative.ui.pluginstore.PluginSheetEntries
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.saved.KeepNotices
import org.jetbrains.compose.resources.stringResource

/**
 * An X post's share sheet (tweet_footer.dart's _showShareSheet), in XTA's
 * order: its words, its link, both, the post as a picture ([onPicture],
 * none without it), then what the running plugins offer (Save to Deepmarks,
 * Save to Karakeep), and Cancel. An article shares its link and its
 * picture. Where the platform has no share sheet (the desktop), the words
 * and the link are copied instead, as the notice says.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostShareSheet(post: XPost, onPicture: (() -> Unit)?, onDismiss: () -> Unit) {
    val shown = post.shown
    val prefs = postScope().prefs
    val url = PostView.url(post, prefs)
    val article = shown.article != null
    val share = rememberShareText()
    val copied = stringResource(Res.string.posts_copied)
    val send = { text: String ->
        onDismiss()
        if (!share(text)) KeepNotices.say(copied)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            PostShare.entries(article, picture = onPicture != null).forEach { entry ->
                when (entry) {
                    ShareEntry.CONTENT -> MenuRow(PostIcons.TextSnippet, stringResource(Res.string.posts_share_content)) { send(PostShare.text(shown, prefs.cleanLinks)) }
                    ShareEntry.LINK -> MenuRow(XIcons.Link, stringResource(if (article) Res.string.posts_share_article_link else Res.string.reader_link_share_post)) { send(url) }
                    ShareEntry.CONTENT_AND_LINK -> MenuRow(PostIcons.AddLink, stringResource(Res.string.posts_share_content_and_link)) {
                        send(PostShare.textAndLink(shown, url, prefs.cleanLinks))
                    }
                    ShareEntry.IMAGE -> MenuRow(PostIcons.Screenshot, stringResource(if (article) Res.string.posts_share_article_image else Res.string.posts_share_image)) {
                        onDismiss()
                        onPicture?.invoke()
                    }
                }
            }
            PluginSheetEntries(remember(post, url) { PluginSaves.shared(post, url) }, onDismiss)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            MenuRow(Icons.Filled.Close, stringResource(Res.string.cancel), onDismiss)
        }
    }
}
