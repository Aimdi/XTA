import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_caption.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_comments.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_series.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_stats.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_tags.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_card.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_content.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_link.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/ui/dates.dart';

/// Opens novel [novelId] in place of the reader on screen.
typedef PixivNovelChapterOpener = void Function(int novelId);

/// What sits above a novel's text: cover, title, author, series, figures,
/// tags, caption and the comments.
class PixivNovelReaderHeader extends StatelessWidget {
  final PixivNovel novel;

  const PixivNovelReaderHeader({super.key, required this.novel});

  @override
  Widget build(BuildContext context) {
    final series = novel.series;
    return Padding(
      padding: const EdgeInsets.only(top: 16, bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: 8,
        children: [
          _titleRow(context),
          if (series != null) PixivSeriesLink(series: series, onOpen: openPixivNovelSeries),
          PixivNovelReaderStats(novel: novel),
          if (novel.tags.isNotEmpty) PixivDetailTags(tags: novel.tags),
          if (novel.captionHtml.isNotEmpty || novel.caption.isNotEmpty)
            PixivHtmlText(html: novel.captionHtml, plainText: novel.caption),
          PixivCommentsLink(target: PixivCommentTarget.novel(novel.id), count: novel.totalComments),
          const Divider(height: 1),
        ],
      ),
    );
  }

  Widget _titleRow(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      spacing: 16,
      children: [
        PixivNovelCover(url: novel.coverUrl, width: 88),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            spacing: 4,
            children: [
              if (novel.title.isNotEmpty)
                Text(novel.title, style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w800)),
              PixivUserLink(
                userId: novel.user.id,
                name: novel.user.name,
                avatarUrl: novel.user.avatarUrl,
                style: theme.textTheme.titleSmall,
                borderRadius: BorderRadius.circular(8),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Bookmarks, views, date, length and the R-18 and AI marks under the title.
class PixivNovelReaderStats extends StatelessWidget {
  final PixivNovel novel;

  const PixivNovelReaderStats({super.key, required this.novel});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final created = novel.createdAt;
    return Wrap(
      spacing: 12,
      runSpacing: 4,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        _bookmarks(context),
        PixivDetailStat(icon: Icons.visibility_outlined, label: compactCount(novel.totalViews)),
        if (created != null)
          Text(
            createCompactDate(created),
            style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
        PixivDetailStat(
          icon: Icons.notes,
          label: l10n.plugin_pixiv_novel_characters(novel.textLength, compactCount(novel.textLength)),
        ),
        if (novel.isR18) PixivNovelRating(label: novel.isR18G ? l10n.plugin_pixiv_r18g : l10n.plugin_pixiv_r18),
        if (novel.isAi && pixivShowsAiBadge(pixivPrefsOf(context)))
          PixivNovelRating(label: l10n.plugin_pixiv_ai, ai: true),
      ],
    );
  }

  Widget _bookmarks(BuildContext context) {
    final bookmarks = context.read<PixivNovelBookmarkStore>();
    return ScopedBuilder<PixivNovelBookmarkStore, Map<int, bool>>(
      store: bookmarks,
      distinct: (_) => (bookmarks.isBookmarked(novel), bookmarks.bookmarkCount(novel)),
      onState: (context, _) => PixivDetailStat(
        icon: bookmarks.isBookmarked(novel) ? Icons.favorite : Icons.favorite_border,
        label: compactCount(bookmarks.bookmarkCount(novel)),
      ),
    );
  }
}

/// What follows the text: the comments again, then the chapters beside it.
class PixivNovelReaderFooter extends StatelessWidget {
  final PixivNovel novel;
  final PixivNovelContent content;
  final PixivNovelChapterOpener onOpenChapter;

  const PixivNovelReaderFooter({super.key, required this.novel, required this.content, required this.onOpenChapter});

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 24, bottom: 32),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: 8,
      children: [
        const Divider(height: 1),
        PixivCommentsLink(target: PixivCommentTarget.novel(novel.id), count: novel.totalComments),
        PixivNovelChapterButtons(content: content, onOpen: onOpenChapter),
      ],
    ),
  );
}

/// The previous and next chapters, each named, and off when Pixiv says the
/// reader cannot open it. Nothing at all outside a series.
class PixivNovelChapterButtons extends StatelessWidget {
  final PixivNovelContent content;
  final PixivNovelChapterOpener onOpen;

  const PixivNovelChapterButtons({super.key, required this.content, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    if (content.previous == null && content.next == null) return const SizedBox.shrink();
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      spacing: 12,
      children: [
        Expanded(child: _button(context, content.previous, l10n.plugin_pixiv_novel_previous, Icons.chevron_left)),
        Expanded(child: _button(context, content.next, l10n.plugin_pixiv_novel_next, Icons.chevron_right)),
      ],
    );
  }

  Widget _button(BuildContext context, PixivNovelNeighbour? chapter, String label, IconData icon) {
    if (chapter == null) return const SizedBox.shrink();
    final name = pixivNovelNeighbourName(L10n.of(context), chapter);
    return OutlinedButton.icon(
      key: ValueKey('pixiv-novel-chapter-${chapter.id}'),
      style: OutlinedButton.styleFrom(
        minimumSize: const Size.fromHeight(kMinInteractiveDimension),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      ),
      onPressed: chapter.viewable ? () => onOpen(chapter.id) : null,
      icon: Icon(icon),
      label: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label),
          if (name.isNotEmpty) Text(name, maxLines: 2, overflow: TextOverflow.ellipsis),
        ],
      ),
    );
  }
}

/// A neighbouring chapter's title, else its number in the series.
String pixivNovelNeighbourName(L10n l10n, PixivNovelNeighbour chapter) => chapter.title.isNotEmpty
    ? chapter.title
    : (chapter.order > 0 ? l10n.plugin_pixiv_novel_chapter_number(chapter.order) : '');
