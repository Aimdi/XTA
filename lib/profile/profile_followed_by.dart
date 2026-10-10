/// "Followed by A, B and N others you subscribe to" on an X profile.
///
/// Answered only from following lists the app has already read (group
/// Discover, or a subscription's Following list opened by the reader), so
/// opening a profile never asks X for anything. A list holds a member's
/// latest follows only, so a match is reliable but a miss proves nothing.
library;

import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_discovery_follows.dart';

@immutable
class ProfileFollowedBy {
  /// Subscriptions known to follow the profile, most recent follow first.
  final List<UserSubscription> followers;

  /// X subscriptions whose following list the app has read.
  final int checked;

  /// Every X subscription other than the profile itself.
  final int total;

  const ProfileFollowedBy({this.followers = const [], this.checked = 0, this.total = 0});

  static const none = ProfileFollowedBy();
}

/// Which of [subscriptions] follow [profileId], according to [remembered].
///
/// Ordered by how recently each one followed — a following list is newest
/// first — and then by the reader's own subscription order.
ProfileFollowedBy profileFollowedBy(
  String profileId,
  Map<String, RememberedFollows> remembered,
  Iterable<Subscription> subscriptions,
) {
  final members = subscriptions.whereType<UserSubscription>().where((member) => member.id != profileId).toList();
  final checked = members.where((member) => remembered.containsKey(member.id)).toList();
  final ranked = [
    for (final (order, member) in checked.indexed)
      if (_followPosition(remembered[member.id]!.follows, profileId) case final position?)
        (member: member, position: position, order: order),
  ]..sort((a, b) => a.position != b.position ? a.position.compareTo(b.position) : a.order.compareTo(b.order));
  return ProfileFollowedBy(
    followers: [for (final entry in ranked) entry.member],
    checked: checked.length,
    total: members.length,
  );
}

int? _followPosition(List<DiscoveryFollow> follows, String profileId) {
  final index = follows.indexWhere((follow) => follow.id == profileId);
  return index < 0 ? null : index;
}

/// The sentence for [names], X style: up to three names, then two and a count.
String profileFollowedByLabel(L10n l10n, List<String> names) => switch (names) {
  [] => '',
  [final a] => l10n.profile_followed_by_one(a),
  [final a, final b] => l10n.profile_followed_by_two(a, b),
  [final a, final b, final c] => l10n.profile_followed_by_three(a, b, c),
  [final a, final b, ...final rest] => l10n.profile_followed_by_many(rest.length, a, b),
};

typedef RememberedFollowsReader = Future<Map<String, RememberedFollows>> Function();

Future<Map<String, RememberedFollows>> _sharedRemembered() => DiscoveryFollowsCache.shared.remembered();

class ProfileFollowedByStore extends Store<ProfileFollowedBy> {
  final RememberedFollowsReader remembered;

  ProfileFollowedByStore({this.remembered = _sharedRemembered}) : super(ProfileFollowedBy.none);

  Future<void> load(String profileId, List<Subscription> subscriptions) =>
      execute(() async => profileFollowedBy(profileId, await remembered(), subscriptions));
}
