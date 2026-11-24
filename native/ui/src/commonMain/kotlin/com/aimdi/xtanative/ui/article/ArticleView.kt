package com.aimdi.xtanative.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.services.article.Article
import com.aimdi.xtanative.services.article.ArticleBlock
import com.aimdi.xtanative.services.article.ArticleEntity
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.foundIn
import com.aimdi.xtanative.ui.post.MediaGrid
import com.aimdi.xtanative.ui.post.PostActions

private val CardShape = RoundedCornerShape(12.dp)

/** A post's article as a card, before it is read (ArticleWidget collapsed): cover, title, preview. */
@Composable
fun ArticlePreviewCard(post: XPost, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val article = remember(post) { ArticleLayout.preview(post) } ?: return
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .clickable(onClick = onClick),
    ) {
        article.coverUrl?.let { Cover(it) }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TipText(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (article.previewText.isNotEmpty()) {
                TipText(
                    article.previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Cover(url: String) {
    AsyncImage(
        model = photoUrl(url, PhotoSize.MEDIUM),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().aspectRatio(5f / 2f).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** How an article's text is set: the body's style and a heading's. */
class ArticleType(val body: TextStyle, val heading: TextStyle)

/** The type of an article in a post: the app's own. */
@Composable
private fun plainType(): ArticleType = ArticleType(MaterialTheme.typography.bodyLarge, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

/** The type of an article in the reader: the size and line height every reader shares, headings in step with the body. */
@Composable
fun readerType(fontSize: Double, lineHeight: Double): ArticleType {
    val plain = plainType()
    return remember(plain, fontSize, lineHeight) {
        ArticleType(
            plain.body.copy(fontSize = fontSize.sp, lineHeight = lineHeight.em),
            plain.heading.copy(fontSize = (fontSize * HEADING_SCALE).sp, lineHeight = 1.3.em),
        )
    }
}

/** A heading's size beside the body's, as the app's own type has them (22 to 16). */
private const val HEADING_SCALE = 1.375

/**
 * An article read in full (ArticleWidget expanded): the cover, the title,
 * [header] under it (the post's footer, in XTA), then each block as its
 * kind lays it out, with what it embeds under it. Its text can be selected.
 */
@Composable
fun ArticleBody(article: Article, actions: PostActions, modifier: Modifier = Modifier, header: (@Composable () -> Unit)? = null) {
    val items = remember(article) { ArticleLayout.items(article) }
    val type = plainType()
    Column(modifier.fillMaxWidth()) {
        article.coverUrl?.let { ArticleCover(it) }
        ArticleTitle(article)
        if (header != null) ArticleHeader(header)
        SelectionContainer {
            Column {
                items.forEachIndexed { index, item -> ArticleRow(item, ArticleLayout.rowKey(index), article, actions, type) }
            }
        }
    }
}

/**
 * [article] as the rows of a list, for the reader: what [lead] names (the
 * cover, the title, [header]), then [items], one row each, set in [type].
 * Inside a selection, only the text's rows are selected.
 */
fun LazyListScope.articleRows(
    article: Article,
    lead: List<ArticleLead>,
    items: List<ArticleItem>,
    actions: PostActions,
    type: ArticleType,
    header: @Composable () -> Unit,
) {
    items(lead, key = { it.name }, contentType = { it }) { row ->
        DisableSelection {
            when (row) {
                ArticleLead.COVER -> article.coverUrl?.let { ArticleCover(it) }
                ArticleLead.TITLE -> ArticleTitle(article)
                ArticleLead.HEADER -> Column { ArticleHeader(header) }
            }
        }
    }
    itemsIndexed(items, contentType = { _, item -> item::class.simpleName }) { index, item ->
        ArticleRow(item, ArticleLayout.rowKey(index), article, actions, type)
    }
}

@Composable
private fun ArticleCover(url: String) {
    Box(Modifier.padding(bottom = 16.dp).clip(CardShape)) { Cover(url) }
}

@Composable
private fun ArticleTitle(article: Article) {
    val title = foundIn(ArticleLayout.TITLE_KEY, remember(article.title) { AnnotatedString(article.title) })
    Text(
        title.text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        onTextLayout = title.laidOut,
        modifier = Modifier.padding(vertical = 8.dp).then(title.modifier),
    )
}

@Composable
private fun ArticleHeader(header: @Composable () -> Unit) {
    header()
    HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun ArticleRow(item: ArticleItem, key: String, article: Article, actions: PostActions, type: ArticleType) {
    val body = type.body
    when (item) {
        is ArticleItem.Paragraph -> BlockText(item.block, key, article, actions, body, Modifier.padding(vertical = 8.dp))
        is ArticleItem.Heading -> BlockText(item.block, key, article, actions, type.heading, Modifier.padding(vertical = 16.dp))
        is ArticleItem.Bullet -> ListItem("•", item.block, key, article, actions, body)
        is ArticleItem.Numbered -> ListItem("${item.number}.", item.block, key, article, actions, body)
        is ArticleItem.Quote -> Row(Modifier.padding(vertical = 8.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
            Box(Modifier.width(4.dp).heightIn(min = 24.dp).background(MaterialTheme.colorScheme.outlineVariant))
            BlockText(item.block, key, article, actions, body, Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp))
        }
        is ArticleItem.Embed -> Embedded(item.entity, actions, Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun ListItem(mark: String, block: ArticleBlock, key: String, article: Article, actions: PostActions, body: TextStyle) {
    Row(Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp)) {
        Text("$mark ", style = body)
        BlockText(block, key, article, actions, body, Modifier.weight(1f))
    }
}

@Composable
private fun BlockText(block: ArticleBlock, key: String, article: Article, actions: PostActions, style: TextStyle, modifier: Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(block, article, linkColor, actions) { annotated(block, article, linkColor, actions) }
    val found = foundIn(key, text)
    Text(found.text, style = style, color = MaterialTheme.colorScheme.onSurface, onTextLayout = found.laidOut, modifier = modifier.then(found.modifier))
}

private fun annotated(block: ArticleBlock, article: Article, linkColor: Color, actions: PostActions): AnnotatedString = buildAnnotatedString {
    block.spans.forEach { span ->
        val look = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            textDecoration = if (span.underline) TextDecoration.Underline else null,
        )
        when (val tap = ArticleLayout.tap(span, article)) {
            is ArticleTap.Link -> withLink(LinkAnnotation.Clickable(tap.url, TextLinkStyles(look.merge(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                actions.openLink(tap.url)
            }) { append(span.text) }
            is ArticleTap.Profile -> withLink(LinkAnnotation.Clickable("@" + tap.screenName, TextLinkStyles(look.merge(SpanStyle(color = linkColor)))) {
                actions.openProfile(tap.screenName)
            }) { append(span.text) }
            null -> withStyle(look) { append(span.text) }
        }
    }
}

/** What a block embeds: a picture, a video (played as a post's is), code, or a divider. */
@Composable
private fun Embedded(entity: ArticleEntity, actions: PostActions, modifier: Modifier) {
    when (entity) {
        is ArticleEntity.Image -> AsyncImage(
            model = photoUrl(entity.url, PhotoSize.LARGE),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        is ArticleEntity.Video -> MediaGrid(listOf(entity.media), onOpen = { entity.streamUrl?.let(actions.openInBrowser) }, modifier = modifier)
        is ArticleEntity.Markdown -> Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp),
        ) {
            if (entity.language.isNotEmpty()) {
                Text(entity.language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Text(entity.code, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
        }
        is ArticleEntity.Divider -> HorizontalDivider(modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        is ArticleEntity.Link -> Unit
    }
}
