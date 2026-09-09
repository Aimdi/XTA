import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_group_store.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/subscriptions/plugin_group_action.dart';
import 'package:xta/tweet/interleaved_items.dart';

Future<void> addPixivToGroup(BuildContext context, PixivUser user) async {
  final store = PixivGroupSubscriptionsStore(context.read<PixivClient>().prefs);
  try {
    await editPluginAccountGroups(
      context,
      subscription: pixivSubscription(user),
      ensureFollowed: () => store.add(user),
    );
  } finally {
    store.destroy();
  }
}

Future<List<InterleavedItem>> loadPixivGroupPosts(BuildContext context, List<String> ids) async {
  final client = context.read<PixivClient>();
  final mute = context.read<PixivMuteStore>();
  final items = <InterleavedItem>[];
  for (final id in ids) {
    final userId = int.tryParse(id.substring('$pluginIdPixiv:'.length));
    if (userId == null) continue;
    try {
      final page = await client.userIllusts(userId);
      for (final illust in mute.filter(page.illusts)) {
        if (illust.createdAt case final date?) {
          items.add((
            date: date,
            build: (_) => Padding(
              padding: const EdgeInsets.all(8),
              child: PixivIllustTile(illust: illust),
            ),
          ));
        }
      }
    } catch (_) {
      // One unavailable artist should not empty the rest of the group.
    }
  }
  return items;
}
