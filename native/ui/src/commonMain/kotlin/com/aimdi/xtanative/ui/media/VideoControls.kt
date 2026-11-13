package com.aimdi.xtanative.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_download
import com.aimdi.xtanative.ui.generated.resources.reader_more_options
import com.aimdi.xtanative.ui.generated.resources.reader_video
import com.aimdi.xtanative.ui.generated.resources.reader_video_failed
import com.aimdi.xtanative.ui.generated.resources.reader_video_fill
import com.aimdi.xtanative.ui.generated.resources.reader_video_fit
import com.aimdi.xtanative.ui.generated.resources.reader_video_mute
import com.aimdi.xtanative.ui.generated.resources.reader_video_quality
import com.aimdi.xtanative.ui.generated.resources.reader_video_restart
import com.aimdi.xtanative.ui.generated.resources.reader_video_seconds
import com.aimdi.xtanative.ui.generated.resources.reader_video_speed
import com.aimdi.xtanative.ui.generated.resources.reader_video_speed_value
import com.aimdi.xtanative.ui.generated.resources.reader_video_subtitles
import com.aimdi.xtanative.ui.generated.resources.video_fullscreen
import com.aimdi.xtanative.ui.generated.resources.video_fullscreen_exit
import com.aimdi.xtanative.ui.generated.resources.video_picture_in_picture
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private enum class VideoSheet { MORE, SPEED, QUALITY }

/**
 * What the controls offer beyond playing (XtaControls' options). Full screen
 * ([fullscreen]) has the mute button in its bar and fit or fill
 * ([onToggleFill]) in its menu; in place, the corner badge mutes
 * ([MuteCorner]) and the seek bar stops short of it. [onFullscreen] goes full
 * screen in place and leaves it full screen; [onPictureInPicture] shrinks
 * the app into a floating window where the platform has one.
 */
internal class VideoChrome(
    val fullscreen: Boolean = false,
    val onFullscreen: (() -> Unit)? = null,
    val onPictureInPicture: (() -> Unit)? = null,
    val fill: Boolean = false,
    val onToggleFill: (() -> Unit)? = null,
    val onDownload: (() -> Unit)? = null,
)

/**
 * XTA's video controls (_video_controls.dart): a tap shows or hides them, and
 * they hide by themselves a few seconds after the last touch. The play button
 * is in the middle; at the bottom, over the seek bar, the time, then (full
 * screen) mute and picture-in-picture, the menu (speed, quality, subtitles,
 * fit or fill when full screen, download) and the full-screen button. A double
 * tap on either half skips ten seconds that way. They take no insets: their
 * caller keeps them clear of the system bars.
 */
@Composable
internal fun VideoControls(playback: VideoPlayback, chrome: VideoChrome, modifier: Modifier = Modifier) {
    val state by playback.state.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var skipped by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<VideoSheet?>(null) }
    val touch: () -> Unit = { touches++ }
    LaunchedEffect(visible, touches, sheet) {
        if (visible && sheet == null) {
            delay(VideoRules.CONTROLS_SHOWN_MS)
            visible = false
        }
    }
    LaunchedEffect(skipped) {
        if (skipped != 0) {
            delay(600)
            skipped = 0
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(playback) {
                detectTapGestures(
                    onTap = { visible = !visible },
                    onDoubleTap = { at ->
                        val back = at.x < size.width / 2f
                        val now = playback.state.value
                        val delta = (if (back) -VideoRules.SEEK_SECONDS else VideoRules.SEEK_SECONDS) * 1000L
                        playback.seekTo(VideoRules.seekTarget(now.positionMs, delta, now.durationMs))
                        skipped = if (back) -VideoRules.SEEK_SECONDS else VideoRules.SEEK_SECONDS
                    },
                )
            },
    ) {
        AnimatedVisibility(visible, enter = fadeIn(tween(MOTION_STANDARD_MS)), exit = fadeOut(tween(MOTION_STANDARD_MS))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                PlayPauseButton(state, Modifier.align(Alignment.Center)) {
                    touch()
                    playback.playOrPause()
                }
                BottomBar(state, playback, chrome, touch, onMenu = { sheet = VideoSheet.MORE }, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        if (skipped != 0) SkipBadge(skipped, Modifier.align(if (skipped < 0) Alignment.CenterStart else Alignment.CenterEnd))
        if (state.qualityFailed) QualityFailed(playback, Modifier.align(Alignment.TopCenter))
        if (state.failed) FailedOverlay(playback)
    }
    VideoSheets(sheet, state, playback, chrome) { sheet = it }
}

/** The viewer's controls as they were before full screen came: the mute button in the bar and fit or fill in the menu, clear of the navigation bar. */
@Composable
internal fun VideoControls(playback: VideoPlayback, fill: Boolean, onToggleFill: () -> Unit, onDownload: (() -> Unit)?, modifier: Modifier = Modifier) =
    VideoControls(playback, VideoChrome(fullscreen = true, fill = fill, onToggleFill = onToggleFill, onDownload = onDownload), modifier.navigationBarsPadding())

/** The time, then the buttons, over the seek bar; in place, the bar stops short of the mute corner. */
@Composable
private fun BottomBar(state: VideoState, playback: VideoPlayback, chrome: VideoChrome, touch: () -> Unit, onMenu: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PositionText(state)
            Spacer(Modifier.weight(1f))
            if (chrome.fullscreen) {
                MuteButton(state.muted) {
                    touch()
                    toggleMute(playback, state.muted)
                }
            }
            chrome.onPictureInPicture?.let { enter -> ControlButton(ReaderIcons.PictureInPicture, stringResource(Res.string.video_picture_in_picture), enter) }
            ControlButton(Icons.Filled.MoreVert, stringResource(Res.string.reader_more_options), onMenu)
            chrome.onFullscreen?.let { toggle ->
                if (chrome.fullscreen) {
                    ControlButton(ReaderIcons.FullscreenExit, stringResource(Res.string.video_fullscreen_exit), toggle)
                } else {
                    ControlButton(ReaderIcons.Fullscreen, stringResource(Res.string.video_fullscreen), toggle)
                }
            }
        }
        val end = if (chrome.fullscreen) 16.dp else MuteCornerReserve
        SeekBar(state, Modifier.offset(y = (-8).dp).padding(start = 14.dp, end = end, bottom = 6.dp), onTouch = touch) { playback.seekTo(it) }
    }
}

/** Mute is the app's, not one video's: the next one starts as this one was left (VideoContextState). */
internal fun toggleMute(playback: VideoPlayback, muted: Boolean) {
    VideoMute.set(!muted)
    playback.setMuted(!muted)
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = Color.White) }
}

/** Full screen's sound button, said as on or off. */
@Composable
private fun MuteButton(muted: Boolean, onToggle: () -> Unit) {
    val label = stringResource(Res.string.reader_video_mute)
    IconToggleButton(checked = muted, onCheckedChange = { onToggle() }, modifier = Modifier.semantics { contentDescription = label }) {
        Crossfade(targetState = muted, animationSpec = tween(MOTION_FAST_MS)) { quiet ->
            Icon(if (quiet) ReaderIcons.VolumeOff else ReaderIcons.VolumeUp, contentDescription = null, tint = Color.White)
        }
    }
}

private val MuteDisc = 30.dp
private val MuteInset = 12.dp
private val TouchTarget = 48.dp

/** kVideoMuteCornerReserve: where the seek bar stops in place, clear of the mute corner's touch target. */
private val MuteCornerReserve = MuteInset + MuteDisc + MuteInset + 2.dp

/**
 * The always-shown sound switch at the bottom end of a video in place
 * (video_mute_badge.dart): a small dark disc with the speaker, its edge [inset]
 * in from the corner (12 dp), [disc] across (30 dp), inside a full touch target.
 */
@Composable
internal fun MuteCorner(muted: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, disc: Dp = MuteDisc, inset: Dp = MuteInset) {
    val label = stringResource(Res.string.reader_video_mute)
    Box(
        modifier
            .padding(inset - (TouchTarget - disc) / 2)
            .size(TouchTarget)
            .clip(CircleShape)
            .toggleable(value = muted, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(disc)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.5f to Color.Transparent))
                .border(0.75.dp, Color.White.copy(alpha = 0.24f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = muted, animationSpec = tween(MOTION_FAST_MS)) { quiet ->
                Icon(if (quiet) ReaderIcons.VolumeOff else ReaderIcons.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** The round button in the middle: play or pause, and start over once the video has ended. */
@Composable
private fun PlayPauseButton(state: VideoState, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(if (state.completed) Res.string.reader_video_restart else Res.string.reader_video)
    Box(
        modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.54f))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val shown = when {
            state.completed -> ReaderIcons.Replay
            state.playing -> ReaderIcons.Pause
            else -> Icons.Filled.PlayArrow
        }
        Crossfade(targetState = shown, animationSpec = tween(MOTION_STANDARD_MS)) { icon ->
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

/** "0:12 / 1:30": where the video is, in bold, over how long it is. */
@Composable
private fun PositionText(state: VideoState) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(VideoRules.timeText(state.positionMs) + " ") }
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.75f))) { append("/ " + VideoRules.timeText(state.durationMs)) }
        },
        fontSize = 14.sp,
    )
}

/**
 * Rounded bars, as media_kit's square ones are not: what has played in the
 * accent, what has loaded behind it. A tap or a drag moves the place, which
 * is sought once the finger lifts.
 */
@Composable
private fun SeekBar(state: VideoState, modifier: Modifier, onTouch: () -> Unit, onSeek: (Long) -> Unit) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val accent = MaterialTheme.colorScheme.primary
    val label = stringResource(Res.string.reader_video)
    val played = dragged ?: VideoRules.fraction(state.positionMs, state.durationMs)
    val buffered = VideoRules.fraction(state.bufferedMs, state.durationMs)
    val position = VideoRules.timeText(state.positionMs) + " / " + VideoRules.timeText(state.durationMs)
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = position
                progressBarRangeInfo = ProgressBarRangeInfo(played, 0f..1f)
                setProgress { target ->
                    onSeek((target.coerceIn(0f, 1f) * state.durationMs).toLong())
                    true
                }
            }
            .pointerInput(state.durationMs) {
                detectTapGestures(
                    onPress = { at ->
                        onTouch()
                        dragged = (at.x / size.width).coerceIn(0f, 1f)
                        if (tryAwaitRelease()) dragged?.let { onSeek((it * state.durationMs).toLong()) }
                        dragged = null
                    },
                )
            }
            .pointerInput(state.durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { at -> dragged = (at.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragged?.let { onSeek((it * state.durationMs).toLong()) }
                        dragged = null
                    },
                    onDragCancel = { dragged = null },
                ) { change, _ ->
                    onTouch()
                    dragged = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val track = Color.White.copy(alpha = 0.3f)
        val loaded = Color.White.copy(alpha = 0.5f)
        Canvas(Modifier.fillMaxSize()) {
            val height = 10.dp.toPx()
            val top = (size.height - height) / 2f
            val radius = CornerRadius(height / 2f)
            drawRoundRect(track, Offset(0f, top), Size(size.width, height), radius)
            drawRoundRect(loaded, Offset(0f, top), Size(size.width * buffered, height), radius)
            drawRoundRect(accent, Offset(0f, top), Size(size.width * played, height), radius)
            drawCircle(accent, radius = 6.dp.toPx(), center = Offset(size.width * played, size.height / 2f))
        }
    }
}

/** "10 s" in a round badge at the side a double tap skipped to. */
@Composable
private fun SkipBadge(seconds: Int, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = 24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.54f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (seconds < 0) ReaderIcons.FastRewind else ReaderIcons.FastForward, contentDescription = null, tint = Color.White)
        Text(stringResource(Res.string.reader_video_seconds, kotlin.math.abs(seconds)), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A change of quality that would not play, said for a moment over the variant that plays on (_video_controls.dart's snack bar). */
@Composable
private fun QualityFailed(playback: VideoPlayback, modifier: Modifier) {
    LaunchedEffect(Unit) {
        delay(3_000)
        playback.qualityFailureSeen()
    }
    Text(
        stringResource(Res.string.reader_video_failed),
        color = Color.White,
        modifier = modifier.padding(16.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** A video that would not start: why, and a way to start the player again (_video.dart's error poster). */
@Composable
private fun FailedOverlay(playback: VideoPlayback) {
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.54f)).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(ReaderIcons.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(stringResource(Res.string.reader_video_failed), color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = playback::retry) { Text(stringResource(Res.string.reader_video_restart)) }
    }
}

/** The ⋮ menu, and the speed and quality lists it leads to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSheets(sheet: VideoSheet?, state: VideoState, playback: VideoPlayback, chrome: VideoChrome, show: (VideoSheet?) -> Unit) {
    sheet ?: return
    ModalBottomSheet(onDismissRequest = { show(null) }) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            when (sheet) {
                VideoSheet.MORE -> MoreRows(state, playback, chrome, show)
                VideoSheet.SPEED -> VideoRules.SPEEDS.reversed().forEach { speed ->
                    CheckRow(stringResource(Res.string.reader_video_speed_value, VideoRules.speedText(speed)), kotlin.math.abs(speed - state.speed) < 0.01) {
                        show(null)
                        playback.setSpeed(speed)
                    }
                }
                VideoSheet.QUALITY -> state.qualities.forEach { quality ->
                    CheckRow(quality.label, quality.url == state.url) {
                        show(null)
                        playback.setQuality(quality.url)
                    }
                }
            }
        }
    }
}

/** The ⋮ menu's rows, in XTA's order: speed, quality, subtitles, fit or fill, download. */
@Composable
private fun MoreRows(state: VideoState, playback: VideoPlayback, chrome: VideoChrome, show: (VideoSheet?) -> Unit) {
    SheetRow(ReaderIcons.Speed, stringResource(Res.string.reader_video_speed)) { show(VideoSheet.SPEED) }
    if (state.qualities.size > 1) SheetRow(ReaderIcons.HighQuality, stringResource(Res.string.reader_video_quality)) { show(VideoSheet.QUALITY) }
    if (state.hasSubtitles) {
        SheetRow(if (state.subtitles) ReaderIcons.ClosedCaption else ReaderIcons.ClosedCaptionOff, stringResource(Res.string.reader_video_subtitles), checked = state.subtitles) {
            show(null)
            playback.setSubtitles(!state.subtitles)
        }
    }
    chrome.onToggleFill?.let { toggle ->
        SheetRow(if (chrome.fill) ReaderIcons.FullscreenExit else ReaderIcons.AspectRatio, stringResource(if (chrome.fill) Res.string.reader_video_fit else Res.string.reader_video_fill)) {
            show(null)
            toggle()
        }
    }
    chrome.onDownload?.let { download ->
        SheetRow(ReaderIcons.Download, stringResource(Res.string.reader_download)) {
            show(null)
            download()
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, checked: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 16.dp))
        if (checked) Icon(Icons.Filled.Check, contentDescription = null)
    }
}

@Composable
private fun CheckRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null) else Spacer(Modifier.width(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}
