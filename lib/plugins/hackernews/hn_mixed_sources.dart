import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_story_card.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/reader_source_text.dart';

MixedEntry hnMixedEntry(HnStory story) => MixedEntry(identity: 'hn:${story.id}', item: story, date: story.createdAt);

String _feedLabel(L10n l10n, HnFeed feed) => switch (feed) {
  HnFeed.top => l10n.plugin_hn_tab_top,
  HnFeed.newest => l10n.plugin_hn_tab_new,
  HnFeed.best => l10n.plugin_hn_tab_best,
  HnFeed.ask => l10n.plugin_hn_tab_ask,
  HnFeed.show => l10n.plugin_hn_tab_show,
  HnFeed.jobs => l10n.plugin_hn_tab_jobs,
};

/// One of the Hacker News story lists, paged the way the Hacker News tab pages it.
class HnFeedMixedSource extends MixedSourceKind {
  const HnFeedMixedSource();

  @override
  String get id => 'hackernews.feed';

  @override
  String get pluginId => pluginIdHackerNews;

  @override
  IconData get icon => Icons.whatshot_outlined;

  @override
  MixedSourceInput get input => MixedSourceInput.choice;

  @override
  String title(BuildContext context) => L10n.of(context).mixed_feed_hn_stories;

  @override
  Future<List<MixedFeedSource>> choices(BuildContext context) async {
    final plugin = pluginById(pluginId)?.title(context);
    return [
      for (final feed in HnFeed.values)
        MixedFeedSource(kind: id, value: feed.name, label: [?plugin, _feedLabel(L10n.of(context), feed)].join(' · ')),
    ];
  }

  @override
  MixedSourceReader? reader(BuildContext context, MixedFeedSource source) {
    final feed = HnFeed.values.asNameMap()[source.value];
    if (feed == null) return null;
    final client = context.read<HackerNewsClient>();
    return MixedFunctionReader((cursor) async {
      final page = await client.feed(feed, page: cursor as int? ?? 0);
      return MixedPage([
        for (final story in page.stories) hnMixedEntry(story),
      ], next: page.hasMore ? page.page + 1 : null);
    });
  }

  @override
  Widget card(BuildContext context, MixedEntry entry) => HnStoryCard(story: entry.item as HnStory);

  @override
  String filterText(MixedEntry entry) => hnFilterText(entry.item as HnStory);
}

const hackerNewsMixedSourceKinds = <MixedSourceKind>[HnFeedMixedSource()];
