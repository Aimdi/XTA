import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_paged_feed.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixivision_article_screen.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/plugins/plugin_gallery_layout.dart';

typedef PixivSpotlightStore = PixivPagedListStore<PixivSpotlightArticle>;

PixivSpotlightStore pixivSpotlightStore(PixivDiscoveryApi api) =>
    PixivPagedListStore(({nextUrl}) => api.spotlightArticles(nextUrl: nextUrl), keyOf: (article) => article.id);

Future<void> openPixivisionList(BuildContext context) =>
    Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const PixivisionListScreen()));

/// Every Pixivision article, newest first, as a paged grid of cards.
class PixivisionListScreen extends StatefulWidget {
  const PixivisionListScreen({super.key});

  @override
  State<PixivisionListScreen> createState() => _PixivisionListScreenState();
}

class _PixivisionListScreenState extends State<PixivisionListScreen> {
  late final PixivSpotlightStore _articles;

  @override
  void initState() {
    super.initState();
    _articles = pixivSpotlightStore(PixivDiscoveryApi.of(context))..refresh();
  }

  @override
  void dispose() {
    _articles.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_pixiv_pixivision_articles)),
      body: PixivPagedFeed<PixivSpotlightArticle>(
        store: _articles,
        emptyMessage: l10n.plugin_pixiv_pixivision_empty,
        emptyIcon: Icons.article_outlined,
        placeholder: const PluginGridSkeleton(columns: 2),
        padding: const EdgeInsets.all(8),
        sliver: (context, articles) => SliverLayoutBuilder(
          builder: (context, constraints) => SliverMasonryGrid.count(
            crossAxisCount: pluginGalleryColumns(constraints.crossAxisExtent, MediaQuery.textScalerOf(context)),
            mainAxisSpacing: 8,
            crossAxisSpacing: 8,
            childCount: articles.length,
            itemBuilder: (context, index) => PixivisionArticleCard(article: articles[index]),
          ),
        ),
      ),
    );
  }
}

/// One article: its picture, a two-line title and the day it went up.
class PixivisionArticleCard extends StatelessWidget {
  final PixivSpotlightArticle article;

  const PixivisionArticleCard({super.key, required this.article});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final published = article.publishedAt;
    return Card.outlined(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        key: ValueKey('pixivision-article-${article.id}'),
        onTap: () => openPixivisionArticle(context, article.id, article: article),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            AspectRatio(
              aspectRatio: 16 / 9,
              child: ColoredBox(
                color: theme.colorScheme.surfaceContainerHighest,
                child: article.thumbnailUrl.isEmpty
                    ? null
                    : PixivNetworkImage(url: article.thumbnailUrl, fit: BoxFit.cover),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 8, 10, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                spacing: 4,
                children: [
                  Text(
                    article.displayTitle,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700, height: 1.25),
                  ),
                  if (published != null)
                    Text(
                      MaterialLocalizations.of(context).formatMediumDate(published),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.labelSmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Pixivision articles side by side on Home, each card as tall as its text needs.
class PixivisionCarousel extends StatelessWidget {
  final List<PixivSpotlightArticle> articles;

  const PixivisionCarousel({super.key, required this.articles});

  static const _cardWidth = 240.0;

  /// The picture, two title lines, the date and the card's padding at the reader's text size.
  double _height(BuildContext context) {
    final theme = Theme.of(context);
    final scaler = MediaQuery.textScalerOf(context);
    final title = scaler.scale(theme.textTheme.titleSmall!.fontSize ?? 14) * 1.25 * 2;
    final date = scaler.scale(theme.textTheme.labelSmall!.fontSize ?? 11) * 1.5;
    return _cardWidth * 9 / 16 + title + date + 4 + 18 + 4;
  }

  @override
  Widget build(BuildContext context) => SizedBox(
    height: _height(context),
    child: ListView.separated(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      itemCount: articles.length,
      separatorBuilder: (_, _) => const SizedBox(width: 8),
      itemBuilder: (context, index) => SizedBox(
        width: _cardWidth,
        child: PixivisionArticleCard(article: articles[index]),
      ),
    ),
  );
}
