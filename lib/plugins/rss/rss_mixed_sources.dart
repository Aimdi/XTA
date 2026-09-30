import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/future_pool.dart';
import 'package:xta/plugins/rss/rss_card.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/reader_source_text.dart';

MixedEntry rssMixedEntry(RssItem item) =>
    MixedEntry(identity: 'rss:${item.feedId}:${item.id}', item: item, date: item.publishedAt);

/// RSS documents have no pages: each source is read once, newest first.
abstract class _RssMixedSource extends MixedSourceKind {
  const _RssMixedSource();

  @override
  String get pluginId => pluginIdRss;

  @override
  IconData get icon => Icons.rss_feed;

  @override
  Widget card(BuildContext context, MixedEntry entry) => RssItemCard(item: entry.item as RssItem);

  @override
  String filterText(MixedEntry entry) => rssFilterText(entry.item as RssItem);
}

class RssAllMixedSource extends _RssMixedSource {
  const RssAllMixedSource();

  @override
  String get id => 'rss.all';

  @override
  String title(BuildContext context) => L10n.of(context).mixed_feed_rss_all;

  @override
  String signature(BuildContext context, MixedFeedSource source) =>
      [for (final feed in context.read<RssFeedsStore>().state) feed.id].join('\n');

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final client = context.read<RssClient>();
    final feeds = context.read<RssFeedsStore>();
    return MixedFunctionReader((_) async {
      final followed = await feeds.saved();
      final read = await mapWithConcurrency(followed, rssFetchConcurrency, (feed) async {
        try {
          return await client.fetchItems(feed);
        } catch (_) {
          return null;
        }
      });
      if (followed.isNotEmpty && read.every((items) => items == null)) throw StateError('No feed could be read');
      return MixedPage([
        for (final item in mergeRssItems(const [], read.nonNulls.expand((items) => items))) rssMixedEntry(item),
      ]);
    });
  }
}

class RssFeedMixedSource extends _RssMixedSource {
  const RssFeedMixedSource();

  @override
  String get id => 'rss.feed';

  @override
  MixedSourceInput get input => MixedSourceInput.choice;

  @override
  String title(BuildContext context) => L10n.of(context).mixed_feed_rss_feed;

  @override
  Future<List<MixedFeedSource>> choices(BuildContext context) async {
    final feeds = context.read<RssFeedsStore>();
    return [for (final feed in await feeds.saved()) MixedFeedSource(kind: id, value: feed.feedUrl, label: feed.name)];
  }

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final client = context.read<RssClient>();
    final feeds = context.read<RssFeedsStore>();
    return MixedFunctionReader((_) async {
      await feeds.saved();
      final feed =
          feeds.followedFeed(source.value) ??
          RssFeed(id: rssFeedId(source.value), feedUrl: source.value, name: source.label);
      return MixedPage([for (final item in await client.fetchItems(feed)) rssMixedEntry(item)]);
    });
  }
}

const rssMixedSourceKinds = <MixedSourceKind>[RssAllMixedSource(), RssFeedMixedSource()];
