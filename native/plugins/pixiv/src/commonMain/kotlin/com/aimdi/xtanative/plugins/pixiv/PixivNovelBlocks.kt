package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.pixiv.PixivLinks
import com.aimdi.xtanative.networks.pixiv.PixivNovelBlock
import com.aimdi.xtanative.networks.pixiv.PixivNovelContent
import com.aimdi.xtanative.networks.pixiv.PixivNovelPicture
import com.aimdi.xtanative.networks.pixiv.PixivNovelSpan
import com.aimdi.xtanative.store.reading.ReadingAppearance
import com.aimdi.xtanative.ui.common.Graphemes
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString

/** One block of a novel's text in the reader's text size and spacing; [jump] goes to a page of it. */
@Composable
internal fun NovelBlockView(block: PixivNovelBlock, content: PixivNovelContent, look: ReadingAppearance, jump: (Int) -> Unit) {
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = look.fontSize.sp, lineHeight = (look.fontSize * look.lineHeight).sp)
    when (block) {
        is PixivNovelBlock.Paragraph -> if (block.isBlank) Text("", style = style) else SpansText(block.spans, style, jump)
        is PixivNovelBlock.Heading -> SpansText(block.spans, style.copy(fontWeight = FontWeight.Bold, fontSize = (look.fontSize * 1.2).sp), jump, Modifier.padding(vertical = 12.dp))
        is PixivNovelBlock.PageBreak -> PageDivider(block.page)
        is PixivNovelBlock.Illust -> NovelPicture(content.illust(block)) { runtime -> runtime.open(PixivRoute.Work(block.illustId)) }
        is PixivNovelBlock.Upload -> NovelPicture(content.upload(block), onOpen = null)
    }
}

/** Words, ruby over its base, links (Pixiv's open here, others after asking) and jumps to another page. */
@Composable
private fun SpansText(spans: List<PixivNovelSpan>, style: TextStyle, jump: (Int) -> Unit, modifier: Modifier = Modifier) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val outside = rememberOutside()
    val language = currentLanguage()
    val link = SpanStyle(color = MaterialTheme.colorScheme.primary)
    val rubies = spans.filterIsInstance<PixivNovelSpan.Ruby>().distinct()
    val inline = rubies.associate { ruby -> rubyKey(ruby) to rubyContent(ruby, style) }
    val text = buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is PixivNovelSpan.Plain -> append(span.text)
                is PixivNovelSpan.Ruby -> appendInlineContent(rubyKey(span), span.base)
                is PixivNovelSpan.Link -> clickable(span.label, link) { openNovelLink(runtime, sheets, outside, language, span.url) }
                is PixivNovelSpan.PageJump -> clickable(Strings.novel_jump_to_page.get(language, span.page), link) { jump(span.page) }
            }
        }
    }
    Text(text, style = style, inlineContent = inline, modifier = modifier.fillMaxWidth())
}

private fun AnnotatedString.Builder.clickable(label: String, style: SpanStyle, onClick: () -> Unit) {
    withLink(LinkAnnotation.Clickable(label, TextLinkStyles(style)) { onClick() }) { append(label) }
}

/** Pixiv's links open here; any other is opened after the reader says so. */
private fun openNovelLink(runtime: PixivRuntime, sheets: PixivSheets, outside: PixivOutside, language: String, url: String) {
    if (PixivLinks.parse(url) != null) return runtime.openHref(url)
    sheets.ask.ask(Strings.novel_link_confirm.get(language, url), Strings.novel_link_open.get(language)) { outside.open(url) }
}

private fun rubyKey(ruby: PixivNovelSpan.Ruby): String = "ruby:" + ruby.base + "\u0000" + ruby.ruby

/** How wide a run of text is in ems, near enough: a full em for a CJK character, a little over half for others. */
private fun ems(text: String): Float = Graphemes.of(text).sumOf { if (it.codePointAt(0) >= 0x2E80) 1.0 else 0.6 }.toFloat()

/** Ruby drawn over its base, half the size, as the inline content of a line. */
private fun rubyContent(ruby: PixivNovelSpan.Ruby, style: TextStyle): InlineTextContent {
    val width = maxOf(ems(ruby.base), ems(ruby.ruby) / 2f, 0.5f)
    return InlineTextContent(Placeholder(width.em, 1.6.em, PlaceholderVerticalAlign.TextBottom)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            Text(ruby.ruby, style = style.copy(fontSize = style.fontSize * 0.5f, lineHeight = style.fontSize * 0.55f), maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            Text(ruby.base, style = style.copy(lineHeight = style.fontSize * 1.05f), maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PageDivider(page: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(pluginString(Strings.novel_page, page), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(Modifier.weight(1f))
    }
}

/** A picture in the text: a tap opens it (the work, or the picture full screen), a long press saves it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelPicture(picture: PixivNovelPicture?, onOpen: ((PixivRuntime) -> Unit)?) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val haptics = rememberHaptics()
    val open = onOpen ?: picture?.let { shown -> { r: PixivRuntime -> r.open(PixivRoute.Picture(r.shelf.put(shown.saveUrl))) } }
    PixivPicture(
        picture?.url,
        Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(
            enabled = picture != null,
            onClickLabel = pluginString(if (onOpen != null) Strings.novel_image_open else Strings.novel_image_view),
            onClick = { open?.invoke(runtime) },
            onLongClickLabel = pluginString(Strings.novel_image_save),
            onLongClick = {
                haptics.medium()
                picture?.let { sheets.saveImage(it.saveUrl) }
            },
        ),
        contentScale = ContentScale.FillWidth,
        contentDescription = pluginString(Strings.novel_image),
    )
}
