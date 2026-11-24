package com.aimdi.xtanative.ui.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.AbsoluteAlignment
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * The address under the pointer, shown at the window's foot as a browser
 * shows a link's target: what [LinkHovers] decides, as plain Kotlin.
 */
internal object LinkHovers {
    /** How long the pointer rests on a link before its address shows. */
    const val SHOW_AFTER_MS = 400L

    /** How long the address stays once the pointer left its link: moving on to the next link keeps it up without a blink. */
    const val HIDE_AFTER_MS = 120L

    /** Within this long of the last address going, the next one shows at once, as it does while one is up. */
    const val QUICK_AGAIN_MS = 600L

    /** How long the pill waits before showing an address: at once while one is up or just went, else [SHOW_AFTER_MS]. */
    fun showDelay(showing: Boolean, sinceHiddenMs: Long?): Long = when {
        showing -> 0L
        sinceHiddenMs != null && sinceHiddenMs in 0 until QUICK_AGAIN_MS -> 0L
        else -> SHOW_AFTER_MS
    }

    /** What the pill does next: after [waitMs], show [show] (null: show nothing). */
    data class Step(val waitMs: Long, val show: String?)

    /**
     * The pill's next step once the pointer is on [target] (null: on no link)
     * while it shows [shown]: the address after [showDelay], or nothing after
     * [HIDE_AFTER_MS]; null when there is nothing to change.
     */
    fun step(target: String?, shown: String?, sinceHiddenMs: Long?): Step? = when {
        target == shown -> null
        target != null -> Step(showDelay(shown != null, sinceHiddenMs), target)
        else -> Step(HIDE_AFTER_MS, null)
    }

    /** Which bottom corner the pill sits in. */
    enum class Corner { LEFT, RIGHT }

    /**
     * Bottom left, as a browser's; bottom right while the [pointer] is where
     * the pill at the left would be (it then covers nothing the reader points
     * at). [margin] is the pill's gap to the window's edges.
     */
    fun corner(pointer: Offset?, frame: IntSize, pill: IntSize, margin: Float): Corner {
        pointer ?: return Corner.LEFT
        val overLeft = pointer.x <= pill.width + margin * 2 && pointer.y >= frame.height - pill.height - margin * 2
        return if (overLeft) Corner.RIGHT else Corner.LEFT
    }

    /**
     * [address] (an ASCII address, see [LinkDrags.addressOf]) as the reader
     * reads it: escaped letters of other scripts shown as themselves, as a
     * browser shows them. Escaped ASCII, spaces, controls and invisible
     * marks (which could make one address look like another) stay escaped.
     */
    fun readable(address: String): String {
        val out = StringBuilder(address.length)
        var i = 0
        while (i < address.length) {
            val end = escapesEnd(address, i)
            if (end == i) {
                out.append(address[i])
                i++
                continue
            }
            out.append(decodedRun(address.substring(i, end)))
            i = end
        }
        return out.toString()
    }

    /** Where the run of `%XX` escapes starting at [from] ends ([from] itself when there is none). */
    private fun escapesEnd(text: String, from: Int): Int {
        var at = from
        while (at + 2 < text.length && text[at] == '%' && text[at + 1].isHex() && text[at + 2].isHex()) at += 3
        return at
    }

    private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** One run of escapes: its non-ASCII letters decoded where they are whole UTF-8 and safe to show, the rest left as written. */
    private fun decodedRun(run: String): String {
        val escapes = run.chunked(3)
        val bytes = escapes.map { it.substring(1).toInt(16).toByte() }
        val out = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val length = utf8Length(bytes[i].toInt() and 0xff)
            val char = if (length > 1 && i + length <= bytes.size) decoded(bytes.subList(i, i + length)) else null
            if (char != null && shows(char)) {
                out.append(char)
                i += length
            } else {
                out.append(escapes[i])
                i++
            }
        }
        return out.toString()
    }

    private fun utf8Length(lead: Int): Int = when {
        lead < 0x80 -> 1
        lead in 0xc2..0xdf -> 2
        lead in 0xe0..0xef -> 3
        lead in 0xf0..0xf4 -> 4
        else -> 1
    }

    private fun decoded(bytes: List<Byte>): String? = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(ByteArrayOutputStream().apply { bytes.forEach { write(it.toInt()) } }.toByteArray()))
            .toString()
    }.getOrNull()

    private fun shows(text: String): Boolean {
        val point = text.codePointAt(0)
        return when (Character.getType(point).toByte()) {
            Character.CONTROL, Character.FORMAT, Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR,
            Character.PARAGRAPH_SEPARATOR, Character.PRIVATE_USE, Character.SURROGATE, Character.UNASSIGNED,
            -> false
            else -> true
        }
    }
}

/**
 * One window's link under the pointer: the links in it ([dragsLink],
 * [dragsLinks]) say when the pointer rests on them and leaves them, and the
 * window's [LinkHoverPill] shows the address. Each window has its own.
 */
internal class LinkHover(private val now: () -> Long = System::currentTimeMillis) {
    /** The link the pointer is on now, and who said so. */
    private var hovered by mutableStateOf<Pair<Any, String>?>(null)

    /** The address the pill shows; null while it shows none. */
    var shown by mutableStateOf<String?>(null)
        private set

    /** The pointer in the window's frame, while a link has it or the pill is up. */
    var pointer by mutableStateOf<Offset?>(null)
        private set

    private var hiddenAt: Long? = null

    /** [owner]'s link at [address] has the pointer. */
    fun over(owner: Any, address: String) {
        if (hovered?.first !== owner || hovered?.second != address) hovered = owner to address
    }

    /** The pointer is at [at] in the frame (any move); kept only while it matters. */
    fun moved(at: Offset) {
        if (hovered != null || shown != null) pointer = at
    }

    /** [owner]'s link no longer has the pointer; another link's later word stands. */
    fun left(owner: Any) {
        if (hovered?.first === owner) hovered = null
    }

    /** Shows and hides the address after [LinkHovers]'s delays, for as long as the window is there. */
    suspend fun run() {
        // Each change of link restarts the wait, so a quick pass over a link shows nothing.
        snapshotFlow { hovered?.second }.collectLatest { target ->
            val step = LinkHovers.step(target, shown, hiddenAt?.let { now() - it }) ?: return@collectLatest
            delay(step.waitMs)
            if (step.show == null) hiddenAt = now()
            shown = step.show
        }
    }
}

/** The window's [LinkHover]; null where there is none (Android, a preview). */
internal val LocalLinkHover = staticCompositionLocalOf<LinkHover?> { null }

/**
 * The address under the pointer, in a small pill at the window's bottom
 * left (right while the pointer is there), as a browser's status bubble.
 * Draws above the frame's content and takes no clicks.
 */
@Composable
internal fun LinkHoverPill(hover: LinkHover) {
    LaunchedEffect(hover) { hover.run() }
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val margin = with(density) { PILL_MARGIN.toPx() }
    var pill by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    val corner by remember(hover) { derivedStateOf { LinkHovers.corner(hover.pointer, frame, pill, margin) } }
    var lastShown by remember { mutableStateOf("") }
    hover.shown?.let { lastShown = it }
    BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged { frame = it }) {
        AnimatedVisibility(
            visible = hover.shown != null,
            enter = if (reduced) EnterTransition.None else fadeIn(tween(FADE_MS)),
            exit = if (reduced) ExitTransition.None else fadeOut(tween(FADE_MS)),
            modifier = Modifier
                .align(if (corner == LinkHovers.Corner.LEFT) AbsoluteAlignment.BottomLeft else AbsoluteAlignment.BottomRight)
                .padding(PILL_MARGIN)
                .widthIn(max = maxWidth * MAX_WIDTH_SHARE)
                .onSizeChanged { pill = it },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraSmall,
                shadowElevation = 2.dp,
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
            ) {
                Text(
                    LinkHovers.readable(lastShown),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private val PILL_MARGIN = 6.dp

/** The pill is at most this share of the window's width; a longer address ends in an ellipsis. */
private const val MAX_WIDTH_SHARE = 0.5f

private const val FADE_MS = 120
