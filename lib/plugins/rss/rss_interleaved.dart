import 'package:flutter/widgets.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/rss/rss_card.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/ui/provenance_accent.dart';

final _log = Logger('RssInterleaved');

const int kRssInterleavedPageSize = 8;

bool rssInHomeFeed(BasePrefService prefs) =>
    prefs.get<bool>(optionPluginRssEnabled) == true &&
    prefs.get<bool>(optionPluginRssInHomeFeed) == true;

List<String> rssHomeIds(BuildContext context) {
  if (!rssInHomeFeed(PrefService.of(context, listen: false))) {
    return const [];
  }
  return context
      .read<RssFeedsStore>()
      .state
      .map((e) => e.id)
      .toList(growable: false);
}

/// Items for [feeds], as dated cards a timeline can slot between its chains.
///
/// One unreachable feed must not empty the rest of the timeline.
Future<List<InterleavedItem>> loadRssInterleaved(
  BuildContext context,
  List<RssSubscription> feeds,
) async {
  if (feeds.isEmpty) {
    return const [];
  }

  final client = context.read<RssClient>();
  final fetched = await Future.wait(
    feeds.map((feed) async {
      try {
        final items = await client.fetchItems(feedOf(feed));
        return (
          feed,
          items.take(kRssInterleavedPageSize).toList(growable: false),
        );
      } catch (e) {
        _log.warning('Unable to load RSS items for ${feed.id}: $e');
        return null;
      }
    }),
  );

  return [
    for (final pair in fetched.nonNulls)
      for (final item in pair.$2)
        if (item.publishedAt case final date?)
          provenanceInterleavedItem(
            date: date,
            pluginId: pluginIdRss,
            build: (_) => RssItemCard(item: item, showSourceBadge: true),
          ),
  ];
}
