package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Material's [Text], cut to an ellipsis by default, that on the desktop names
 * its whole text beside the pointer while it rests there, as a desktop's own
 * widgets and a page's title do, but only while the text is actually cut.
 * Nothing more on Android, where there is no pointer to rest.
 */
@Composable
internal fun TipText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) = TipText(
    AnnotatedString(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign, lineHeight, overflow, softWrap, maxLines, minLines,
    style = style,
)

/** [TipText] for styled text; the tip names its plain characters. */
@Composable
internal fun TipText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    style: TextStyle = LocalTextStyle.current,
) {
    val text0: @Composable (Modifier, (TextLayoutResult) -> Unit) -> Unit = { m, layout ->
        Text(
            text, m, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
            overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, inlineContent = inlineContent, onTextLayout = layout, style = style,
        )
    }
    if (!TextTipsShown) return text0(modifier) {}
    var cut by remember(text) { mutableStateOf(false) }
    TextTip(TextTips.tip(text.text, cut), modifier) { inner -> text0(inner) { cut = TextTips.isCut(it) } }
}

/** Whether a text's whole wording is named where the pointer rests on it when cut ([TipText]). */
internal expect val TextTipsShown: Boolean

/**
 * [content], laid out with the modifier it is handed, showing [tip] beside the
 * pointer while the pointer rests there; just [content] with [modifier] when
 * [tip] is null.
 */
@Composable
internal expect fun TextTip(tip: String?, modifier: Modifier, content: @Composable (Modifier) -> Unit)

/** When a [TipText] names its whole text. */
internal object TextTips {
    /** Whether a laid-out text lost characters: it overflowed its room, or a line ends in an ellipsis. */
    fun isCut(layout: TextLayoutResult): Boolean =
        isCut(layout.hasVisualOverflow, layout.lineCount) { layout.isLineEllipsized(it) }

    fun isCut(overflowed: Boolean, lineCount: Int, ellipsized: (Int) -> Boolean): Boolean =
        overflowed || (0 until lineCount).any(ellipsized)

    /** The tip a text shows: its whole wording when [cut], with nothing to show for blank text. */
    fun tip(text: String, cut: Boolean): String? = text.trim().takeIf { cut && it.isNotEmpty() }
}
