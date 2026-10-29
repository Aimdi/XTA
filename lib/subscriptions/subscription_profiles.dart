/// Followed X accounts as X shows them now: every page of posts names the
/// accounts behind it, with their current handle, name and picture.
library;

import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/user.dart';

/// The X accounts behind [chains]' posts: each post's author and the authors
/// of what it reposts and quotes, and of what those quote.
Iterable<UserWithExtra> postAuthors(Iterable<TweetChain> chains) =>
    chains.expand((chain) => chain.tweets).expand(_shown).map((tweet) => tweet.user).whereType<UserWithExtra>();

Iterable<TweetWithCard> _shown(TweetWithCard? tweet) =>
    tweet == null ? const [] : [tweet, ..._shown(tweet.retweetedStatusWithCard), ..._shown(tweet.quotedStatusWithCard)];

final _pictureSize = RegExp(r'_(?:normal|bigger|mini|reasonably_small|\d+x\d+)(?=\.\w+$)');

/// A profile picture's address without its size, so that the same picture
/// served at another size is the same picture.
String? _pictureKey(String? url) => url?.replaceFirst(_pictureSize, '');

String? _nonEmpty(String? value) => value == null || value.trim().isEmpty ? null : value;

/// The followed X accounts among [users] whose handle, name or picture now
/// differ from [subscriptions], by id, as X sends them. What X left out is
/// kept as it was.
Map<String, UserSubscription> refreshedSubscriptions(List<Subscription> subscriptions, Iterable<UserWithExtra> users) {
  final byId = {for (final user in users) ?user.idStr: user};
  return {
    for (final stored in subscriptions.whereType<UserSubscription>())
      if (byId[stored.id] case final user?) stored.id: ?_refreshed(stored, user),
  };
}

UserSubscription? _refreshed(UserSubscription stored, UserWithExtra user) {
  final screenName = _nonEmpty(user.screenName) ?? stored.screenName;
  final name = _nonEmpty(user.name) ?? stored.name;
  final picture = _nonEmpty(user.profileImageUrlHttps) ?? stored.profileImageUrlHttps;
  if (screenName == stored.screenName &&
      name == stored.name &&
      _pictureKey(picture) == _pictureKey(stored.profileImageUrlHttps)) {
    return null;
  }
  return _withProfile(stored, screenName: screenName, name: name, picture: picture);
}

UserSubscription _withProfile(
  UserSubscription subscription, {
  required String screenName,
  required String name,
  required String? picture,
}) => UserSubscription(
  id: subscription.id,
  screenName: screenName,
  name: name,
  profileImageUrlHttps: picture,
  verified: subscription.verified,
  createdAt: subscription.createdAt,
  inFeed: subscription.inFeed,
  maxPostsPerLoad: subscription.maxPostsPerLoad,
);

/// [subscriptions] with the handle, name and picture of each of [refreshed]
/// and everything else as it is now.
List<Subscription> withProfiles(List<Subscription> subscriptions, Map<String, UserSubscription> refreshed) => [
  for (final subscription in subscriptions)
    switch ((subscription, refreshed[subscription.id])) {
      (final UserSubscription current, final fresh?) => _withProfile(
        current,
        screenName: fresh.screenName,
        name: fresh.name,
        picture: fresh.profileImageUrlHttps,
      ),
      _ => subscription,
    },
];

/// Whether [fresh] is [stored]'s account under another handle, which the
/// feeds' searches name it by. A change of case is the same handle to X.
bool subscriptionRenamed(UserSubscription stored, UserSubscription fresh) =>
    stored.screenName.toLowerCase() != fresh.screenName.toLowerCase();
