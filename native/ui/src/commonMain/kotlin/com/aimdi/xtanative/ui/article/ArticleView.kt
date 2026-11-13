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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.services.article.Article
import com.aimdi.xtanative.services.article.ArticleBlock
import com.aimdi.xtanative.services.article.ArticleEntity
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
            Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (article.previewText.isNotEmpty()) {
                Text(
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

/**
 * An article read in full (ArticleWidget expanded): the cover, the title,
 * [header] under it (the post's footer, in XTA), then each block as its
 * kind lays it out, with what it embeds under it. Its text can be selected.
 */
@Composable
fun ArticleBody(article: Article, actions: PostActions, modifier: Modifier = Modifier, header: (@Composable () -> Unit)? = null) {
    val items = remember(article) { ArticleLayout.items(article) }
    Column(modifier.fillMaxWidth()) {
        article.coverUrl?.let {
            Box(Modifier.padding(bottom = 16.dp).clip(CardShape)) { Cover(it) }
        }
        Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        if (header != null) {
            header()
            HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 16.dp))
        }
        SelectionContainer {
            Column {
                items.forEach { item -> ArticleRow(item, article, actions) }
            }
        }
    }
}

@Composable
private fun ArticleRow(item: ArticleItem, article: Article, actions: PostActions) {
    val body = MaterialTheme.typography.bodyLarge
    when (item) {
        is ArticleItem.Paragraph -> BlockText(item.block, article, actions, body, Modifier.padding(vertical = 8.dp))
        is ArticleItem.Heading -> BlockText(item.block, article, actions, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), Modifier.padding(vertical = 16.dp))
        is ArticleItem.Bullet -> ListItem("•", item.block, article, actions)
        is ArticleItem.Numbered -> ListItem("${item.number}.", item.block, article, actions)
        is ArticleItem.Quote -> Row(Modifier.padding(vertical = 8.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
            Box(Modifier.width(4.dp).heightIn(min = 24.dp).background(MaterialTheme.colorScheme.outlineVariant))
            BlockText(item.block, article, actions, body, Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp))
        }
        is ArticleItem.Embed -> Embedded(item.entity, actions, Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun ListItem(mark: String, block: ArticleBlock, article: Article, actions: PostActions) {
    Row(Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp)) {
        Text("$mark ", style = MaterialTheme.typography.bodyLarge)
        BlockText(block, article, actions, MaterialTheme.typography.bodyLarge, Modifier.weight(1f))
    }
}

@Composable
private fun BlockText(block: ArticleBlock, article: Article, actions: PostActions, style: TextStyle, modifier: Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(block, article, linkColor, actions) { annotated(block, article, linkColor, actions) }
    Text(text, style = style, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
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
