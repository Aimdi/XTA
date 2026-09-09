import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart' as bluesky;
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart' as mastodon;
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_store.dart' as threads;
import 'package:xta/subscriptions/plugin_group_action.dart';

Future<void> addBlueskyAccountToGroup(BuildContext context, BlueskyAccount account) {
  final store = context.read<bluesky.BlueskyAccountsStore>();
  return editPluginAccountGroups(
    context,
    subscription: bluesky.subscriptionOf(account),
    ensureFollowed: () async {
      if (!store.follows(account.handle)) await store.add(account);
    },
  );
}

Future<void> addMastodonAccountToGroup(BuildContext context, MastodonAccount account) {
  final store = context.read<mastodon.MastodonAccountsStore>();
  return editPluginAccountGroups(
    context,
    subscription: mastodon.subscriptionOf(account),
    ensureFollowed: () async {
      if (!store.follows(account.acct)) await store.add(account);
    },
  );
}

Future<void> addThreadsAccountToGroup(BuildContext context, ThreadsAccount account) {
  final store = context.read<threads.ThreadsAccountsStore>();
  return editPluginAccountGroups(
    context,
    subscription: threads.subscriptionOf(account),
    ensureFollowed: () async {
      if (!store.follows(account.handle)) await store.add(account);
    },
  );
}
