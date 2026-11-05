package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.posts_filter_broadcasts
import com.aimdi.xtanative.ui.generated.resources.posts_open_in_browser
import com.aimdi.xtanative.ui.generated.resources.posts_spaces
import com.aimdi.xtanative.ui.generated.resources.reader_video_failed
import com.aimdi.xtanative.ui.generated.resources.reader_video_restart
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.post.PlayBadge
import org.jetbrains.compose.resources.stringResource

/**
 * What opens a broadcast or a Space in the live player ([LiveRooms.open]
 * decides whether it can); null outside the app's navigation, where a room
 * opens in the browser.
 */
val LocalLivePlayer = staticCompositionLocalOf<((XLive) -> Unit)?> { null }

/** Where a live room's stream stands: asked for, found at [url], or not given. */
private sealed interface LiveStream {
    data object Asking : LiveStream

    data class Found(val url: String) : LiveStream

    data object Failed : LiveStream
}

/**
 * A broadcast or a Space played in the app (live_player_screen.dart): its
 * HLS stream asked of X as the post's card asks for it, played under XTA's
 * controls with full screen, on black. The bar's button opens its page in a
 * browser, which is all there is to do when X gives no stream.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePlayerScreen(live: XLive, onBack: () -> Unit) {
    val links = LocalLinks.current
    val watchUrl = live.watchUrl
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (live.isSpace) Res.string.posts_spaces else Res.string.posts_filter_broadcasts)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                actions = {
                    if (watchUrl != null) {
                        IconButton(onClick = { links?.openOutside(watchUrl) }) { Icon(ReaderIcons.OpenInNew, stringResource(Res.string.posts_open_in_browser)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            LiveRoom(live, Modifier.fillMaxWidth().aspectRatio(live.aspectRatio.takeIf { it > 0f } ?: (16f / 9f)))
        }
    }
}

/** The room's stream, asked for again on "Restart video player" after X gave none. */
@Composable
private fun LiveRoom(live: XLive, modifier: Modifier) {
    val client = LocalGraph.current.client
    var attempt by remember { mutableIntStateOf(0) }
    val stream by produceState<LiveStream>(LiveStream.Asking, live, attempt) {
        value = LiveStream.Asking
        value = client.livePlaybackUrl(live)?.let { LiveStream.Found(it) } ?: LiveStream.Failed
    }
    val poster: @Composable () -> Unit = {
        AsyncImage(model = live.imageUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val now = stream) {
            LiveStream.Asking -> {
                poster()
                CircularProgressIndicator(color = Color.White)
            }
            LiveStream.Failed -> {
                poster()
                NoStream(onRetry = { attempt++ })
            }
            is LiveStream.Found -> LiveVideo(now.url, live, poster)
        }
    }
}

/** The stream under XTA's controls in place, its mute corner and full screen; where the platform has no player, a tap hands it to the system's. */
@Composable
private fun LiveVideo(url: String, live: XLive, poster: @Composable () -> Unit) {
    val graph = LocalGraph.current
    val players = rememberVideoPlayers()
    if (players == null) {
        val links = LocalLinks.current
        Box(Modifier.fillMaxSize().clickable { links?.openOutside(url) }, contentAlignment = Alignment.Center) {
            poster()
            PlayBadge(Modifier)
        }
        return
    }
    val opened = remember(url, players) {
        val settings = graph.settings.state.value
        val player = players.make(VideoOptions.of(settings)).apply { open(VideoSource.of(url, gif = false, settings).copy(loop = false)) }
        player to KeptPlayer(player::release)
    }
    val (player, kept) = opened
    DisposableEffect(kept) { onDispose(kept::left) }
    LaunchedEffect(player) { player.setActive(true) }
    PausesWhenLeft(player)
    val state by player.state.collectAsState()
    val fullscreen = LocalVideoFullscreen.current
    Box(Modifier.fillMaxSize()) {
        // Full screen has it: the room keeps its poster meanwhile.
        if (fullscreen?.shows(player) == true) return@Box poster()
        player.Picture(fill = false, Modifier.fillMaxSize())
        val open: (() -> Unit)? = fullscreen?.let { screen ->
            {
                kept.enteredFullscreen()
                screen.open(FullscreenVideo(player, live.aspectRatio, onDownload = null, onClosed = kept::leftFullscreen))
            }
        }
        VideoControls(player, VideoChrome(onFullscreen = open), Modifier.fillMaxSize())
        PosterUntilStarted(state, Modifier.fillMaxSize(), poster)
        MuteCorner(state.muted, onToggle = { toggleMute(player, state.muted) }, Modifier.align(Alignment.BottomEnd))
    }
}

/** X gave no stream: why, and a way to ask again (_video.dart's error poster). */
@Composable
private fun NoStream(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.54f)).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(ReaderIcons.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(stringResource(Res.string.reader_video_failed), color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRetry) { Text(stringResource(Res.string.reader_video_restart)) }
    }
}
