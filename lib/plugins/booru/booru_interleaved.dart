import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_post_card.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/tweet/interleaved_items.dart';

const int kBooruInterleavedPageSize = 8;

bool booruInHomeFeed(BasePrefService prefs) =>
    prefs.get<bool>(optionPluginBooruEnabled) == true &&
    prefs.get<bool>(optionPluginBooruInHomeFeed) == true;

List<String> booruHomeTags(BuildContext context) {
  final prefs = PrefService.of(context, listen: false);
  if (!booruInHomeFeed(prefs)) return const [];
  return context.read<BooruTagsStore>().state;
}

Future<List<InterleavedItem>> loadBooruInterleaved(
  BuildContext context,
  List<String> tags, {
  int limitPerTag = kBooruInterleavedPageSize,
}) async {
  if (tags.isEmpty) return const [];

  try {
    final client = context.read<BooruClient>();
    final posts = await client.postsForTags(tags, limitPerTag: limitPerTag);
    return booruInterleavedItems(posts);
  } catch (_) {
    return const [];
  }
}
