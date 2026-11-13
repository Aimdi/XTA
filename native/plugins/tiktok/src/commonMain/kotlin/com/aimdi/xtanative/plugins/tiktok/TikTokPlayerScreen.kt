package com.aimdi.xtanative.plugins.tiktok

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.tiktok.TikTokClient
import com.aimdi.xtanative.networks.tiktok.TikTokPost
import com.aimdi.xtanative.networks.tiktok.TikTokVideoSource
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.TikTokPrefs
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.WithSettings
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside

/**
 * A video full screen (tiktok_player_screen.dart): TikTok's file played
 * directly, asked for as TikTok's web player asks, in the renditions it
 * offers; TikTok's embedded player when that is preferred or the file will
 * not play. The caption over the bottom; the bar switches players, picks a
 * rendition and opens the video on TikTok.
 */
@Composable
internal fun TikTokPlayerScreen(app: TikTokApp, route: TikTokRoute.Video) = WithSettings(app.host.settings) { settings ->
    val handed = remember(route) { app.shelf.take<TikTokPost>(route.shelf)?.takeIf { it.id == route.id } }
    // The video as its list handed it over, else read from its page.
    val load = rememberKept("tiktok-video:${route.id}") { scope ->
        Fetch(scope, handed) { _ -> app.client().video(route.id, route.handle) }.also { if (handed == null) it.load() }
    }
    val state by load.state.collectAsState()
    val post = state.value
    val error = state.problem
    when {
        post != null -> Player(app, post, settings)
        error != null && !state.loading -> PluginScaffold(title = route.handle?.let { "@$it" }.orEmpty(), onBack = app.host::back) { padding ->
            RetryMessage(pluginString(TikTokText.error(error)), { load.load() }, Modifier.padding(padding))
        }
        else -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
    }
}

@Composable
private fun Player(app: TikTokApp, post: TikTokPost, settings: Settings) {
    val renditions = remember(post) { renditionsOf(post) }
    var embedded by rememberSaveable(post.id) { mutableStateOf(settings[TikTokPrefs.preferEmbed] || renditions.isEmpty()) }
    var chosen by rememberSaveable(post.id) { mutableIntStateOf(0) }
    val client = produceState<TikTokClient?>(null) { value = app.client() }.value
    val openOutside = rememberOpenOutside()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                !inAppPlayer -> CoverToOpen(post) { openOutside(post.url) }
                client == null -> CircularProgressIndicator(color = Color.White)
                embedded -> EmbedPlayer(post.embedUrl, client.cookies, client.userAgent, Modifier.fillMaxWidth().aspectRatio(post.aspectRatio.coerceIn(9f / 16f, 16f / 9f)))
                else -> key(chosen) {
                    DirectPlayer(renditions[chosen.coerceIn(0, renditions.lastIndex)].url, client.playbackHeaders, Modifier.fillMaxSize(), onError = { embedded = true })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
        PlayerBar(
            post = post,
            embedded = embedded,
            renditions = if (embedded || !inAppPlayer) emptyList() else renditions,
            chosen = chosen,
            onBack = app.host::back,
            onToggle = if (inAppPlayer && renditions.isNotEmpty()) ({ embedded = !embedded }) else null,
            onPick = { chosen = it },
            onOpen = { openOutside(post.url) },
        )
        Caption(post, Modifier.align(Alignment.BottomCenter))
    }
}

/** Where there is no player: the cover, which opens the video on TikTok. */
@Composable
private fun CoverToOpen(post: TikTokPost, onOpen: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(post.aspectRatio.coerceIn(9f / 16f, 16f / 9f)).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
        post.coverUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        Icon(Icons.Filled.PlayArrow, contentDescription = pluginString(Strings.open_on_site), tint = Color.White, modifier = Modifier.size(64.dp))
    }
}

@Composable
private fun PlayerBar(
    post: TikTokPost,
    embedded: Boolean,
    renditions: List<TikTokVideoSource>,
    chosen: Int,
    onBack: () -> Unit,
    onToggle: (() -> Unit)?,
    onPick: (Int) -> Unit,
    onOpen: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, pluginString(Strings.back), tint = Color.White) }
        Text("@${post.author.uniqueId}", color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, pluginString(Strings.more_options), tint = Color.White) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                onToggle?.let { toggle ->
                    DropdownMenuItem(text = { Text(pluginString(if (embedded) Strings.try_native else Strings.use_embed)) }, onClick = { menu = false; toggle() })
                }
                if (renditions.size > 1) {
                    HorizontalDivider()
                    renditions.forEachIndexed { index, source ->
                        DropdownMenuItem(
                            text = { Text(source.label ?: "—") },
                            trailingIcon = if (index == chosen) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                            onClick = { menu = false; onPick(index) },
                        )
                    }
                    HorizontalDivider()
                }
                DropdownMenuItem(text = { Text(pluginString(Strings.open_on_site)) }, onClick = { menu = false; onOpen() })
            }
        }
    }
}

/** Who posted it and what they wrote, over a shade at the bottom. */
@Composable
private fun Caption(post: TikTokPost, modifier: Modifier) {
    val desc = post.desc.trim()
    Box(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
            TikTokAvatar(post.author.avatarUrl, post.author.displayName, post.author.uniqueId, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(post.author.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${post.author.uniqueId}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                if (desc.isNotEmpty()) Text(desc, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
