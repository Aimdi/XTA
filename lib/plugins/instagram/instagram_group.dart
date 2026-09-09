import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/instagram/instagram_post_card.dart';
import 'package:xta/plugins/instagram/instagram_store.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';
import 'package:xta/subscriptions/plugin_group_action.dart';
import 'package:xta/tweet/interleaved_items.dart';

Future<void> addInstagramToGroup(BuildContext context, InstagramFollow follow) =>
    editPluginAccountGroups(context,
      subscription: PluginAccountSubscription(pluginIdInstagram, follow.toMap()),
      ensureFollowed: () async {
        final store = context.read<InstagramFollowsStore>();
        if (store.containsHandle(follow.id)) return;
        final profile = await context.read<InstagramClient>().profile(follow.id);
        await store.follow(profile);
      });

Future<List<InterleavedItem>> loadInstagramGroupPosts(BuildContext context, List<String> ids) async {
  final client = context.read<InstagramClient>();
  final items = <InterleavedItem>[];
  for (final id in ids) {
    try {
      final page = await client.profileMedia(id.substring('$pluginIdInstagram:'.length));
      for (final post in page.posts) {
        items.add((date: post.createdAt, build: (_) => InstagramPostCard(post: post)));
      }
    } catch (_) {
      // Preserve other accounts when one private/deleted profile cannot be read.
    }
  }
  return items;
}
