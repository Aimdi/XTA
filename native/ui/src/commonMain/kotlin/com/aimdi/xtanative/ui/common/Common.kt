package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.data.SessionStore
import com.aimdi.xtanative.store.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The reader's settings, as every shared part reads them (a post's card, the
 * players, links, haptics): the store alone, so those parts do not depend on
 * the app graph and the screens it holds.
 */
val LocalSettingsStore = staticCompositionLocalOf<SettingsStore> { error("No settings provided") }

/** Whether there is a connection, as the app hears of it; true where nothing tells (a preview). */
val LocalOnline = staticCompositionLocalOf<Flow<Boolean>> { flowOf(true) }

/**
 * The reader's session, the accounts and what is followed on this device,
 * for the shared parts that follow someone or a topic without the app graph.
 */
val LocalSessions = staticCompositionLocalOf<SessionStore> { error("No session provided") }

/** A round picture of someone, asked for as its server wants it ([pictureModel]: Pixiv's with its Referer). */
@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    AsyncImage(
        model = url?.let { rememberPicture(it) },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun LoadingRow(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/** The X client the post views read through (translation, a live room), without the whole app graph. */
val LocalXClient = staticCompositionLocalOf<XClient> { error("No X client provided") }
