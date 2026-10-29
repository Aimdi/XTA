/// How a web source's posts are drawn and opened, with XTA's own pieces:
/// the card rows, plugin media, the post actions sheet and the RSS reader.
library;

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/links/link_opening.dart';
import 'package:xta/plugins/plugin_card_row.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_reader_screen.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_dialogs.dart';
import 'package:xta/plugins/websources/web_source_items.dart';
import 'package:xta/plugins/websources/web_source_mark.dart';
import 'package:xta/ui/dates.dart';

/// Opens [item]: its full text in the reader when the source says where it
/// is, else its link, else its discussion.
Future<void> openWebSourceItem(BuildContext context, WebSourceDefinition source, WebSourceItem item) async {
  final html = item.html ?? await _articleHtml(context, source, item);
  if (!context.mounted) return;
  if (html != null && html.isNotEmpty) {
    final reading = _readerItem(source, item, html);
    await pushWebViewScreen(
      context,
      url: item.link,
      screen: () => RssReaderScreen(item: reading),
    );
    return;
  }
  final address = item.link ?? item.discussion;
  if (address != null) await openLink(context, address);
}

/// The full text the source's article rule names, read with a progress
/// dialog; null without a rule, or when it cannot be read.
Future<String?> _articleHtml(BuildContext context, WebSourceDefinition source, WebSourceItem item) async {
  final article = source.article;
  final url = article == null ? null : webSourceArticleUrl(article, item);
  if (article == null || url == null || !source.allows(url)) return null;
  final client = context.read<WebSourceClient>();
  final body = await withWebSourceProgress(context, () => client.page(source, url, article.type));
  return body == null ? null : webSourceArticleHtml(article, body);
}

RssItem _readerItem(WebSourceDefinition source, WebSourceItem item, String html) => RssItem(
  id: item.key,
  title: item.title ?? item.text ?? source.name,
  feedId: '$pluginIdWebSources:${source.id}',
  feedTitle: source.name,
  link: item.link ?? item.discussion,
  excerpt: item.text,
  bodyHtml: html,
  publishedAt: item.date,
  author: item.author,
  imageUrl: item.image,
);

/// What every plugin post offers on a long press: a folder, share, the
/// browser.
Future<void> showWebSourceItemActions(BuildContext context, WebSourceDefinition source, WebSourceItem item) =>
    showPluginLinkPostActions(
      context,
      source: pluginIdWebSources,
      url: item.link ?? item.discussion ?? source.homepage.toString(),
      author: item.author ?? source.name,
      text: [?item.title, ?item.text].join('\n'),
      images: item.pictures,
    );

class WebSourceItemCard extends StatelessWidget {
  final WebSourceDefinition source;
  final WebSourceItem item;

  /// Names the source above the post, where posts of several sources mix.
  final bool showSource;

  const WebSourceItemCard({super.key, required this.source, required this.item, this.showSource = false});

  @override
  Widget build(BuildContext context) => RepaintBoundary(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        InkWell(
          onTap: () => openWebSourceItem(context, source, item),
          onLongPress: () => showWebSourceItemActions(context, source, item),
          child: Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(16, 14, 16, 12),
            child: switch (source.layout) {
              WebSourceLayout.links => _LinkBody(source: source, item: item, showSource: showSource),
              WebSourceLayout.articles => _ArticleBody(source: source, item: item, showSource: showSource),
              WebSourceLayout.posts ||
              WebSourceLayout.gallery => _PostBody(source: source, item: item, showSource: showSource),
            },
          ),
        ),
        const Divider(height: 1),
      ],
    ),
  );
}

String? _host(String? link) => link == null ? null : Uri.tryParse(link)?.host;

class _LinkBody extends StatelessWidget {
  final WebSourceDefinition source;
  final WebSourceItem item;
  final bool showSource;

  const _LinkBody({required this.source, required this.item, required this.showSource});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        PluginMetaLine(
          parts: [
            if (showSource) source.name,
            ?_host(item.link),
            if (item.score case final score?) l10n.plugin_websources_points(score),
            ?item.author,
            if (item.date case final date?) createCompactDate(date),
          ],
          style: theme.textTheme.bodySmall,
        ),
        const SizedBox(height: 6),
        Text(
          item.title ?? item.text!,
          maxLines: 4,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
        ),
        if (item.discussion case final discussion?)
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: TextButton.icon(
              onPressed: () => openLink(context, discussion),
              style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
              icon: const Icon(Icons.chat_bubble_outline, size: 18),
              label: Text(
                item.comments == null
                    ? l10n.plugin_websources_discussion
                    : l10n.plugin_websources_comments(item.comments!),
              ),
            ),
          ),
      ],
    );
  }
}

class _ArticleBody extends StatelessWidget {
  final WebSourceDefinition source;
  final WebSourceItem item;
  final bool showSource;

  const _ArticleBody({required this.source, required this.item, required this.showSource});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cover = item.image;
    final largeText = MediaQuery.textScalerOf(context).scale(14) > 20;
    final copy = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (item.title case final title?)
          Text(
            title,
            maxLines: 4,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700, height: 1.3),
          ),
        if (item.text case final text?) ...[
          const SizedBox(height: 6),
          Text(text, maxLines: 3, overflow: TextOverflow.ellipsis, style: theme.textTheme.bodyMedium),
        ],
      ],
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        PluginMetaLine(
          parts: [if (showSource) source.name, ?item.author, if (item.date case final date?) createCompactDate(date)],
          style: theme.textTheme.bodySmall,
        ),
        const SizedBox(height: 8),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: copy),
            if (cover != null && !largeText) ...[
              const SizedBox(width: 16),
              SizedBox.square(dimension: 88, child: _Cover(url: cover)),
            ],
          ],
        ),
        if (cover != null && largeText) ...[
          const SizedBox(height: 12),
          SizedBox(height: 140, child: _Cover(url: cover)),
        ],
      ],
    );
  }
}

class _PostBody extends StatelessWidget {
  final WebSourceDefinition source;
  final WebSourceItem item;
  final bool showSource;

  const _PostBody({required this.source, required this.item, required this.showSource});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final pictures = item.pictures;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Avatar(source: source, url: item.avatar),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              PluginNameMetaRow(
                name: Text(item.author ?? source.name, style: theme.textTheme.titleSmall),
                meta: [
                  if (showSource && item.author != null) source.name,
                  if (item.date case final date?) createCompactDate(date),
                ],
              ),
              if (item.title case final title?)
                Text(title, style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
              if (item.text case final text?)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(text, maxLines: 8, overflow: TextOverflow.ellipsis),
                ),
              if (pictures.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: PluginPostMedia(
                    items: [for (final url in pictures) PluginMediaItem(url: url)],
                    sourceName: source.name,
                  ),
                ),
              PluginMetaLine(
                parts: [
                  if (item.score case final score?) l10n.plugin_websources_points(score),
                  if (item.comments case final comments?) l10n.plugin_websources_comments(comments),
                ],
                style: theme.textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Avatar extends StatelessWidget {
  final WebSourceDefinition source;
  final String? url;

  const _Avatar({required this.source, required this.url});

  @override
  Widget build(BuildContext context) {
    final mark = WebSourceMark(source: source, size: 40);
    if (url == null) return mark;
    return ClipOval(
      child: ExtendedImage.network(
        url!,
        width: 40,
        height: 40,
        fit: BoxFit.cover,
        cache: true,
        loadStateChanged: (state) => state.extendedImageLoadState == LoadState.completed ? null : mark,
      ),
    );
  }
}

class _Cover extends StatelessWidget {
  final String url;

  const _Cover({required this.url});

  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(8),
    child: ExtendedImage.network(
      url,
      fit: BoxFit.cover,
      cache: true,
      loadStateChanged: (state) => state.extendedImageLoadState == LoadState.failed
          ? ColoredBox(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              child: const Icon(Icons.article_outlined),
            )
          : null,
    ),
  );
}

/// A gallery's picture: tapping it opens every picture of the list.
class WebSourcePictureTile extends StatelessWidget {
  final WebSourceDefinition source;
  final WebSourceItem item;
  final VoidCallback onTap;

  const WebSourcePictureTile({super.key, required this.source, required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    label: item.title ?? item.text,
    child: InkWell(
      onTap: onTap,
      onLongPress: () => showWebSourceItemActions(context, source, item),
      child: Padding(
        padding: const EdgeInsets.all(1),
        child: item.pictures.isEmpty
            ? const _PictureBox()
            : ExtendedImage.network(
                item.pictures.first,
                fit: BoxFit.cover,
                cache: true,
                loadStateChanged: (state) => switch (state.extendedImageLoadState) {
                  LoadState.completed => null,
                  LoadState.failed => const _PictureBox(icon: Icons.broken_image_outlined),
                  LoadState.loading => const _PictureBox(),
                },
              ),
      ),
    ),
  );
}

/// Where a picture is not shown: loading, or failed.
class _PictureBox extends StatelessWidget {
  final IconData? icon;

  const _PictureBox({this.icon});

  @override
  Widget build(BuildContext context) => ColoredBox(
    color: Theme.of(context).colorScheme.surfaceContainerHighest,
    child: icon == null ? const SizedBox.expand() : Center(child: Icon(icon)),
  );
}
