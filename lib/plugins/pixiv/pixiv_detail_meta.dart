import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_author.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_caption.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_series.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_stats.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_tags.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// Everything under a work's pages: author, title, series, stats, caption and tags.
class PixivDetailMeta extends StatelessWidget {
  final PixivIllust illust;

  const PixivDetailMeta({super.key, required this.illust});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          PixivDetailAuthor(illust: illust),
          if (illust.title.isNotEmpty) ...[
            const SizedBox(height: 14),
            Text(illust.title, style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w800)),
          ],
          if (illust.series case final series?)
            PixivDetailSeries(key: ValueKey('pixiv-detail-series-${illust.id}'), illust: illust, series: series),
          const SizedBox(height: 8),
          PixivDetailStats(illust: illust),
          if (illust.caption.isNotEmpty) ...[const SizedBox(height: 12), PixivDetailCaption(illust: illust)],
          if (illust.tags.isNotEmpty) ...[const SizedBox(height: 12), PixivDetailTags(tags: illust.tags)],
        ],
      ),
    );
  }
}
