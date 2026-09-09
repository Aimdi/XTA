import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_post_card.dart';
import 'package:xta/plugins/tiktok/tiktok_store.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';
import 'package:xta/subscriptions/plugin_group_action.dart';
import 'package:xta/tweet/interleaved_items.dart';

Future<void> addTikTokToGroup(BuildContext context, TikTokFollow follow) => editPluginAccountGroups(
  context,
  subscription: PluginAccountSubscription(pluginIdTiktok, follow.toMap()),
  ensureFollowed: () async {
    final store = context.read<TikTokFollowsStore>();
    if (store.containsHandle(follow.id)) return;
    final profile = await context.read<TikTokClient>().profile(follow.id);
    await store.follow(profile);
  },
);

Future<List<InterleavedItem>> loadTikTokGroupPosts(BuildContext context, List<String> ids) async {
  final client = context.read<TikTokClient>();
  final follows = context.read<TikTokFollowsStore>();
  if (follows.state.isEmpty) await follows.load();
  final selected = ids.toSet();
  final items = <InterleavedItem>[];
  for (final follow in follows.state.where((f) => selected.contains('$pluginIdTiktok:${f.id}'))) {
    try {
      final page = await client.creatorItems(secUid: follow.secUid);
      for (final post in page.posts) {
        items.add((date: post.createdAt, build: (_) => TikTokPostCard(post: post)));
      }
    } catch (_) {
      // Preserve other accounts when one private/deleted profile cannot be read.
    }
  }
  return items;
}
