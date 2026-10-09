/// "Add to this group" for a discovered account, on whichever network it is on.
///
/// Each network keeps its own local follow list; the group row is the same for
/// all of them. The branching lives here so the Discover row stays one widget.
library;

import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart' as bluesky;
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart' as mastodon;
import 'package:xta/plugins/pixiv/pixiv_group_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/subscriptions/plugin_group_action.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/user.dart';

/// The local writes one tap needs, so the tap can be exercised without a database.
abstract interface class DiscoveryGroupWriter {
  /// Follows [account] locally unless already followed; answers the id a group row carries.
  Future<String> follow(DiscoveryAccount account);

  Future<List<String>> listGroupsForUser(String id);

  Future<void> saveUserGroupMembership(String id, List<String> memberships);
}

/// Adds [account] to [groupId], keeping every group it already belongs to.
Future<void> addDiscoveryToGroup(DiscoveryGroupWriter writer, DiscoveryAccount account, String groupId) async {
  final id = await writer.follow(account);
  final memberships = {...await writer.listGroupsForUser(id), groupId}.toList();
  await writer.saveUserGroupMembership(id, memberships);
}

/// What a group row stores for [account] on its network.
Subscription discoverySubscription(DiscoveryAccount account) => switch (account.source) {
  DiscoverySource.x => UserSubscription(
    id: account.id,
    screenName: account.handle,
    name: account.name,
    profileImageUrlHttps: account.avatarUrl,
    verified: false,
    createdAt: DateTime.now(),
    inFeed: true,
  ),
  // Bluesky and Mastodon rows are keyed by the handle, not the DID the AppView answers with.
  DiscoverySource.bluesky => bluesky.subscriptionOf(_blueskyAccount(account)),
  DiscoverySource.mastodon => mastodon.subscriptionOf(_mastodonAccount(account)),
  DiscoverySource.pixiv => pixivSubscription(_pixivUser(account)),
};

BlueskyAccount _blueskyAccount(DiscoveryAccount account) => BlueskyAccount(
  handle: account.handle,
  name: account.name,
  avatarUrl: account.avatarUrl,
  did: account.id.startsWith('did:') ? account.id : null,
);

MastodonAccount _mastodonAccount(DiscoveryAccount account) =>
    MastodonAccount(acct: account.handle, name: account.name, avatarUrl: account.avatarUrl);

PixivUser _pixivUser(DiscoveryAccount account) => PixivUser(
  id: int.tryParse(account.id) ?? 0,
  name: account.name,
  account: account.handle,
  comment: account.bio ?? '',
  avatarUrl: account.avatarUrl,
);

/// The real stores behind [DiscoveryGroupWriter]. Pixiv stays local: a group
/// member is a reader's choice, never a follow on the Pixiv account itself.
class ContextDiscoveryGroupWriter implements DiscoveryGroupWriter {
  final BuildContext context;

  const ContextDiscoveryGroupWriter(this.context);

  @override
  Future<String> follow(DiscoveryAccount account) async {
    final subscription = discoverySubscription(account);
    switch (account.source) {
      case DiscoverySource.x:
        final model = context.read<SubscriptionsModel>();
        if (!model.state.any((s) => s.id == account.id)) await model.toggleSubscribe(subscription, false);
      case DiscoverySource.bluesky:
        final store = context.read<bluesky.BlueskyAccountsStore>();
        if (!store.follows(account.handle)) await store.add(_blueskyAccount(account));
      case DiscoverySource.mastodon:
        final store = context.read<mastodon.MastodonAccountsStore>();
        if (!store.follows(account.handle)) await store.add(_mastodonAccount(account));
      case DiscoverySource.pixiv:
        final store = PixivGroupSubscriptionsStore(PrefService.of(context, listen: false));
        try {
          await store.add(_pixivUser(account));
        } finally {
          store.destroy();
        }
    }
    return subscription.id;
  }

  @override
  Future<List<String>> listGroupsForUser(String id) => context.read<GroupsModel>().listGroupsForUser(id);

  @override
  Future<void> saveUserGroupMembership(String id, List<String> memberships) =>
      context.read<GroupsModel>().saveUserGroupMembership(id, memberships);
}

/// "Other groups…": the membership picker with [groupId] already ticked.
Future<void> pickDiscoveryGroups(BuildContext context, DiscoveryAccount account, String groupId) async {
  final writer = ContextDiscoveryGroupWriter(context);
  final subscription = discoverySubscription(account);
  final selected = {...await writer.listGroupsForUser(subscription.id), groupId}.toList();
  if (!context.mounted) return;
  if (account.source == DiscoverySource.x) {
    final followed = context.read<SubscriptionsModel>().state.any((s) => s.id == account.id);
    return pickUserGroups(context, user: subscription, followed: followed, groupsForUser: selected);
  }
  return editPluginAccountGroups(
    context,
    subscription: subscription,
    ensureFollowed: () => writer.follow(account),
    preselected: selected,
  );
}

typedef DiscoveryAddState = ({Set<String> added, Set<String> adding});

/// Which rows have been added to the group, and which are on their way.
class DiscoveryAddStore extends Store<DiscoveryAddState> {
  bool _closed = false;

  DiscoveryAddStore() : super((added: const {}, adding: const {}));

  Future<bool> add(String key, Future<void> Function() run) async {
    if (_closed || state.adding.contains(key) || state.added.contains(key)) return false;
    update((added: state.added, adding: {...state.adding, key}));
    try {
      await run();
      if (!_closed) update((added: {...state.added, key}, adding: {...state.adding}..remove(key)));
      return true;
    } catch (_) {
      if (!_closed) update((added: state.added, adding: {...state.adding}..remove(key)));
      return false;
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}
