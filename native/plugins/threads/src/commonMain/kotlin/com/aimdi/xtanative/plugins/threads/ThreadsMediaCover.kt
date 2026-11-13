package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons

/** When a post's media waits behind a cover: Meta laid one over it and the reader hides sensitive media, as X's posts are hidden. */
internal object ThreadsMediaCover {
    fun covers(post: ThreadsPost, settings: Settings): Boolean = post.mediaCovered && hides(settings)

    /** Whether the reader hides sensitive media. */
    fun hides(settings: Settings): Boolean = settings[PostsPrefs.hideSensitive] && !settings[PostsPrefs.alwaysShowSensitiveMedia]
}

/**
 * Where covered media stands: Meta's own words for the cover when it gave
 * some ([words]), else Threads' warning in a word, and Show, which draws it;
 * nothing under it is loaded meanwhile.
 */
@Composable
internal fun MediaCover(modifier: Modifier, words: String?, onShow: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(PostIcons.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            words ?: pluginString(Strings.media_covered),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onShow, modifier = Modifier.heightIn(min = 48.dp)) { Text(pluginString(Strings.show)) }
    }
}
