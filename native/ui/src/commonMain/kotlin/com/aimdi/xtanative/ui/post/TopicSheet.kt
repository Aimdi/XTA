package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.topic_follow
import com.aimdi.xtanative.ui.generated.resources.topic_follow_done
import com.aimdi.xtanative.ui.generated.resources.topic_following
import com.aimdi.xtanative.ui.generated.resources.topic_unsubscribe
import com.aimdi.xtanative.ui.plugins.pathIcon
import com.aimdi.xtanative.ui.saved.KeepNotices
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * A long-pressed hashtag's sheet (rich_text.dart's _showTopicFollowSheet):
 * "Follow this topic", or "Following topic" once it is, which saves the
 * search for [topic] on the device, or takes it away, and says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicSheet(topic: String, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val followed = session?.let { TopicLinks.isFollowed(it, topic) } ?: false
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            MenuRow(if (followed) Icons.Outlined.Check else TagIcon, stringResource(if (followed) Res.string.topic_following else Res.string.topic_follow)) {
                onDismiss()
                graph.keep {
                    graph.sessions.update { TopicLinks.toggled(it, topic, System.currentTimeMillis()) }
                    KeepNotices.say(if (followed) getString(Res.string.topic_unsubscribe) else getString(Res.string.topic_follow_done, topic))
                }
            }
        }
    }
}

/** Material's tag (Apache 2.0), the topic's mark in its sheet as in XTA's. */
private val TagIcon: ImageVector by lazy {
    pathIcon("Tag", "M20,10V8h-4V4h-2v4h-4V4H8v4H4v2h4v4H4v2h4v4h2v-4h4v4h2v-4h4v-2h-4v-4H20zM14,14h-4v-4h4V14z")
}
