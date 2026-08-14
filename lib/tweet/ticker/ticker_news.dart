import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/ticker/ticker_search.dart';
import 'package:xta/utils/urls.dart';

/// Headlines for a symbol, read-only — tap opens the article, nothing is posted.
class TickerNewsList extends StatelessWidget {
  final List<TickerNewsItem> items;

  const TickerNewsList({super.key, required this.items});

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const SizedBox.shrink();
    }

    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 4, 8, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 4, 8, 4),
            child: Text(
              L10n.of(context).plugin_stocks_news,
              style: theme.textTheme.titleSmall!.copyWith(
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          for (final item in items.take(4))
            ListTile(
              dense: true,
              contentPadding: const EdgeInsets.symmetric(horizontal: 8),
              title: Text(
                item.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: item.publisher == null ? null : Text(item.publisher!),
              onTap: item.url == null
                  ? null
                  : () => openUri(context, item.url!),
            ),
        ],
      ),
    );
  }
}
