/// Unfollowing a subscription on whatever network it came from.
///
/// [SubscriptionsModel.toggleSubscribe] only ever knew X accounts and saved
/// searches, so the unsubscribe item on a followed subreddit, publication or
/// Threads account silently did nothing. Each source's own store owns its
/// removal — it has to, or its own tab would go on listing what was removed.
library;

import 'package:flutter/widgets.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/subscriptions/subscription_look.dart';

/// Unfollows [subscription], or returns false when it is not a plugin's to
/// remove — an X account or a saved search, which the subscriptions model
/// handles itself.
Future<bool> unfollowSubscription(BuildContext context, Subscription subscription) async {
  final source = sourceOf(subscription);
  if (source == null) {
    return false;
  }

  await source.unfollow(context, subscription);
  return true;
}
