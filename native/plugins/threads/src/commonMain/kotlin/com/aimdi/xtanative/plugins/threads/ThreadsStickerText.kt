package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Scale
import com.aimdi.xtanative.networks.threads.ThreadsCaption
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsSticker
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment.Kind
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlin.math.roundToInt

/**
 * How stickers sit among a caption's words, as threads.com draws them: each
 * a placeholder in the line about one and a half times the text's size, two
 * where a post or bio is stickers alone; each distinct picture loaded once,
 * at the pixels it is drawn at, however often it repeats.
 */
internal object ThreadsStickerRules {
    /** A sticker's height among words, in the text's em. */
    const val INLINE_EM = 1.5f

    /** A sticker's height where there are no words beside it. */
    const val ALONE_EM = 2f

    /** How many characters of a caption a sticker weighs when telling a long one ([ThreadsCardRules.isLong]). */
    const val CHARS = 4

    /** How tall [runs]' stickers are drawn: larger when nothing but stickers (and spaces) is written. */
    fun em(runs: List<ThreadsTextFragment>): Float = if (runs.any { !it.isSticker && it.text.isNotBlank() }) INLINE_EM else ALONE_EM

    /** Each distinct picture among [runs], first seen first: one placeholder and one load each. */
    fun distinct(runs: List<ThreadsTextFragment>): List<ThreadsSticker> = runs.mapNotNull { it.sticker }.distinctBy { it.url }

    /** The placeholder a sticker is drawn in: one per picture. */
    fun id(sticker: ThreadsSticker): String = "sticker:" + sticker.url

    /** What selecting and copying a sticker gives: its label, else [generic], in brackets. */
    fun alternate(sticker: ThreadsSticker, generic: String): String = "[" + (sticker.label ?: generic) + "]"

    /**
     * [runs] as a screen reader hears them: the words, each sticker by its
     * label or [generic], a row of stickers with the same name said once so
     * a post of a hundred unnamed stickers is not a hundred words.
     */
    fun spoken(runs: List<ThreadsTextFragment>, generic: String): String {
        val parts = mutableListOf<String>()
        var last: String? = null
        for (run in runs) {
            val sticker = run.sticker
            when {
                run.isSticker && sticker != null -> {
                    val name = sticker.label ?: generic
                    if (name != last) parts += " $name "
                    last = name
                }
                run.text.isBlank() -> parts += run.text
                else -> { parts += run.text; last = null }
            }
        }
        return parts.joinToString("").replace(Regex("\\s{2,}"), " ").trim()
    }

    /** The pixels a sticker is loaded at: its drawn height ([em] of [fontPixels]) and the width its shape gives. */
    fun pixels(sticker: ThreadsSticker, fontPixels: Float, em: Float): Pair<Int, Int> {
        val height = (fontPixels * em).roundToInt().coerceAtLeast(1)
        return (height * sticker.aspect).roundToInt().coerceAtLeast(1) to height
    }

    /**
     * The runs a translated caption is drawn with: none when [fragments] hold
     * no sticker (patterns then find its links), else the [translation]
     * followed by the stickers, which a translation does not carry.
     */
    fun translated(translation: String, fragments: List<ThreadsTextFragment>): List<ThreadsTextFragment> {
        val stickers = fragments.filter { it.isSticker }
        return if (stickers.isEmpty()) emptyList() else ThreadsCaption.runs(translation, emptyList()) + ThreadsTextFragment(Kind.TEXT, " ") + stickers
    }

    /** How many characters [post]'s caption weighs, its stickers included. */
    fun weight(post: ThreadsPost): Int = post.text.length + post.fragments.count { it.isSticker } * CHARS

    /**
     * [runs] as a line of text: words, mentions, links and tags as the app's
     * linked text draws them, and each sticker the placeholder [id] names,
     * copied as [alternate].
     */
    fun annotated(runs: List<ThreadsTextFragment>, linkColor: Color, generic: String, onMention: (String) -> Unit, onTag: (String) -> Unit): AnnotatedString =
        buildAnnotatedString {
            val styles = TextLinkStyles(SpanStyle(color = linkColor))
            for (run in runs) {
                val target = run.target
                val sticker = run.sticker
                when {
                    run.isSticker && sticker != null -> appendInlineContent(id(sticker), alternate(sticker, generic))
                    target == null -> append(run.text)
                    run.kind == Kind.MENTION -> withLink(LinkAnnotation.Clickable("@$target", styles) { onMention(target) }) { append(run.text) }
                    run.kind == Kind.LINK -> withLink(LinkAnnotation.Url(target, styles)) { append(run.text) }
                    run.kind == Kind.TAG -> withLink(LinkAnnotation.Clickable(target, styles) { onTag(target) }) { append(run.text) }
                    else -> append(run.text)
                }
            }
        }
}

/**
 * A caption or bio with stickers among its words: one Compose [Text] whose
 * placeholders draw the pictures, so they flow and wrap as words do and a
 * long caption still folds at [maxLines].
 */
@Composable
internal fun ThreadsStickerText(
    runs: List<ThreadsTextFragment>,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
    onMention: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    val generic = pluginString(Strings.sticker)
    val link = MaterialTheme.colorScheme.primary
    val text = remember(runs, link, generic, onMention, onTag) { ThreadsStickerRules.annotated(runs, link, generic, onMention, onTag) }
    val spoken = remember(runs, generic) { ThreadsStickerRules.spoken(runs, generic) }
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = rememberStickerContent(runs, style),
        modifier = modifier.semantics { contentDescription = spoken },
    )
}

/**
 * The placeholders [runs]' stickers are drawn in: one per distinct picture,
 * its painter shared by every place it repeats, its picture asked for at
 * the pixels it is drawn at, kept whole on a transparent ground.
 */
@Composable
internal fun rememberStickerContent(runs: List<ThreadsTextFragment>, style: TextStyle): Map<String, InlineTextContent> {
    val stickers = remember(runs) { ThreadsStickerRules.distinct(runs) }
    if (stickers.isEmpty()) return emptyMap()
    val em = remember(runs) { ThreadsStickerRules.em(runs) }
    val fontPixels = with(LocalDensity.current) { (if (style.fontSize.isSp) style.fontSize else FALLBACK_SIZE).toPx() }
    val context = LocalPlatformContext.current
    val painters: Map<String, Painter> = stickers.associate { sticker ->
        key(sticker.url) {
            val request = remember(context, sticker.url, fontPixels, em) { stickerRequest(context, sticker, fontPixels, em) }
            sticker.url to rememberAsyncImagePainter(request, contentScale = ContentScale.Fit)
        }
    }
    return remember(stickers, painters, em) {
        stickers.associate { sticker ->
            val painter = painters.getValue(sticker.url)
            ThreadsStickerRules.id(sticker) to InlineTextContent(Placeholder((em * sticker.aspect).em, em.em, PlaceholderVerticalAlign.TextCenter)) {
                Spacer(Modifier.fillMaxSize().paint(painter, contentScale = ContentScale.Fit))
            }
        }
    }
}

private fun stickerRequest(context: PlatformContext, sticker: ThreadsSticker, fontPixels: Float, em: Float): ImageRequest {
    val (width, height) = ThreadsStickerRules.pixels(sticker, fontPixels, em)
    return ImageRequest.Builder(context).data(sticker.url).size(width, height).scale(Scale.FIT).build()
}

private val FALLBACK_SIZE = 15.sp
