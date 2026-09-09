import 'dart:convert';
import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/hackernews/hn_story_card.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';
import 'package:xta/subscriptions/plugin_group_action.dart';
import 'package:xta/tweet/interleaved_items.dart';

PluginAccountSubscription hnSubscription(String id) =>
    PluginAccountSubscription(pluginIdHackerNews, {'id': id, 'name': id});

List<PluginAccountSubscription> readHnSubscriptions(BasePrefService prefs) {
  try {
    final value = jsonDecode(prefs.get<String>(optionPluginHnFollows) ?? '[]');
    return value is List ? value.whereType<String>().map(hnSubscription).toList() : const [];
  } on FormatException {
    return const [];
  }
}

Future<void> addHnToGroup(BuildContext context, String id) => editPluginAccountGroups(
  context,
  subscription: hnSubscription(id),
  ensureFollowed: () async {
    final follows = context.read<HnFollowsStore>();
    await follows.load();
    if (!follows.isFollowing(id)) await follows.toggle(id);
  },
);

Future<List<InterleavedItem>> loadHnGroupPosts(BuildContext context, List<String> ids) async {
  final client = context.read<HackerNewsClient>();
  final items = <InterleavedItem>[];
  for (final id in ids) {
    try {
      final page = await client.submissions(id.substring('$pluginIdHackerNews:'.length));
      for (final story in page.stories) {
        if (story.createdAt case final date?) {
          items.add((date: date, build: (_) => HnStoryCard(story: story)));
        }
      }
    } catch (_) {
      // Failed accounts do not hide stories from the rest of the group.
    }
  }
  return items;
}
