/// A plugin whose followed accounts are subscriptions, and whose posts belong
/// in a shared timeline.
///
/// `XtaPlugin` could already say what a plugin installs, stores and uninstalls,
/// but not the two things that keep changing: that its followed accounts are
/// subscriptions like any other, and that its posts mix into a group's feed.
/// Both were written out longhand instead — a fixed list of tables in the
/// subscriptions model, a fixed list of membership queries in the group model,
/// a `whereType<…Subscription>()` per network in the group screen, and in the
/// group feed a field, a constructor parameter, a loader, a merge line and
/// three call sites, once per network. Adding one network meant editing files
/// in four packages, none of which should have had to learn a new word.
library;

import 'package:flutter/widgets.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/tweet/interleaved_items.dart';

mixin SubscriptionSource {
  /// Where this source's followed accounts are stored.
  String get subscriptionTable;

  /// A stored row, as a subscription the rest of the app can hold.
  Subscription subscriptionFromMap(Map<String, Object?> row);

  /// Whether [subscription] is one this source owns.
  ///
  /// A one-line `is` check per source, so a group can sort its members by who
  /// will fetch their posts without anything downstream naming a network.
  bool owns(Subscription subscription);

  /// One page of posts for [ids], as dated items a timeline can slot between
  /// its chains.
  ///
  /// Returns nothing rather than throwing when the network is unreachable: one
  /// source being down must not empty a feed of everything else in it.
  Future<List<InterleavedItem>> interleavedPosts(BuildContext context, List<String> ids);

  /// What one of this source's subscriptions is, under its name. A subreddit
  /// and a publication have no `@handle`, and labelling them with one made a
  /// subreddit read as an X account that had lost its avatar.
  String subtitleFor(Subscription subscription) => '@${subscription.screenName}';

  /// The mark shown beside its name, when the plain avatar will not do.
  Widget? avatarFor(Subscription subscription, {double size = 40}) => null;

  /// Where tapping it leads, or null when this source has no screen to open —
  /// better nothing than the wrong network's search results.
  Widget Function()? destinationFor(Subscription subscription) => null;

  /// Stops following it. The source's own store has to do this, or its tab
  /// would go on listing what was just unfollowed.
  Future<void> unfollow(BuildContext context, Subscription subscription);

  /// Re-reads this source's followed accounts from the database.
  ///
  /// Its store outlives the screens, so after an import that replaced the table
  /// underneath it the tab would otherwise go on showing the old list until the
  /// app was restarted — which is what happened to everything but Reddit and
  /// Substack, the only two the import remembered to reload.
  Future<void> reloadFromDatabase(BuildContext context);

  /// Whether the reader asked for this source's posts in the home timeline.
  /// Sources with no such setting never volunteer for it.
  bool inHomeFeed(BuildContext context) => false;

  /// The ids the home timeline should mix in — none unless [inHomeFeed].
  List<String> homeFeedIds(BuildContext context) => const [];
}
