import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/ui/provenance_accent.dart';

/// How many posts each Threads account contributes to a shared timeline.
///
/// Small on purpose: the X side pages on and on, and an account that dropped
/// its whole page in would own the top of the feed rather than joining it.
const int kThreadsInterleavedPageSize = 10;

/// Whether followed Threads accounts belong in the home timeline. Off unless
/// asked for: a reader who turned the plugin on wanted a Threads tab, not a
/// different Following feed.
bool threadsInHomeFeed(BasePrefService prefs) =>
    prefs.get<bool>(optionPluginThreadsEnabled) == true && prefs.get<bool>(optionPluginThreadsInHomeFeed) == true;

/// The handles the home timeline should mix in — none unless the option is on.
List<String> threadsHomeHandles(BuildContext context) {
  final prefs = PrefService.of(context, listen: false);
  if (!threadsInHomeFeed(prefs)) {
    return const [];
  }

  return context.read<ThreadsAccountsStore>().state.map((e) => e.handle).toList(growable: false);
}

/// One page of each account, as dated items a tweet list can slot between its
/// chains.
///
/// Read through [ThreadsFeedStore.postsFor], so which source answers — a
/// session, an RSSHub instance, or the guest path — is decided in the same
/// place the tab decides it. A failure here returns nothing rather than
/// throwing: Threads being unreachable must not empty a timeline of its posts.
Future<List<InterleavedItem>> loadThreadsInterleaved(
  BuildContext context,
  List<String> handles, {
  int limit = kThreadsInterleavedPageSize,
}) async {
  if (handles.isEmpty) {
    return const [];
  }

  final store = context.read<ThreadsFeedStore>();
  try {
    final posts = await store.postsFor(handles);
    return threadsInterleavedItems(posts, limit: limit);
  } catch (_) {
    return const [];
  }
}

/// Posts as dated items, each wearing the Threads provenance accent so a mixed
/// timeline shows where the card came from. One with no date is dropped rather
/// than guessed at: there is nowhere in a chronological feed to put it.
List<InterleavedItem> threadsInterleavedItems(Iterable<ThreadsPost> posts, {int limit = kThreadsInterleavedPageSize}) =>
    [
      for (final post in posts.take(limit))
        if (post.publishedAt case final date?)
          provenanceInterleavedItem(
            date: date,
            pluginId: pluginIdThreads,
            build: (_) => ThreadsPostCard(post: post, showSourceBadge: false),
          ),
    ];
