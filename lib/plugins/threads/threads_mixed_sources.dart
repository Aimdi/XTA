import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/reader_source_text.dart';

MixedEntry threadsMixedEntry(ThreadsPost post) =>
    MixedEntry(identity: 'threads:${post.url ?? post.id}', item: post, date: post.publishedAt);

/// Posts of the accounts the reader follows on Threads, read the way the Threads tab reads them.
class ThreadsFollowingMixedSource extends MixedSourceKind {
  const ThreadsFollowingMixedSource();

  @override
  String get id => 'threads.following';

  @override
  String get pluginId => pluginIdThreads;

  @override
  IconData get icon => Icons.people_outline;

  @override
  String title(BuildContext context) => L10n.of(context).following;

  @override
  String signature(BuildContext context, MixedFeedSource source) =>
      [for (final account in context.read<ThreadsAccountsStore>().state) account.handle].join('\n');

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final feed = context.read<ThreadsFeedStore>();
    final accounts = context.read<ThreadsAccountsStore>();
    return MixedFunctionReader((_) async {
      final posts = await feed.postsFor([for (final account in accounts.state) account.handle]);
      return MixedPage([for (final post in posts) threadsMixedEntry(post)]);
    });
  }

  @override
  Widget card(BuildContext context, MixedEntry entry) => ThreadsPostCard(post: entry.item as ThreadsPost);

  @override
  String filterText(MixedEntry entry) => threadsFilterText(entry.item as ThreadsPost);
}

const threadsMixedSourceKinds = <MixedSourceKind>[ThreadsFollowingMixedSource()];
