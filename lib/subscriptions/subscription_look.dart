/// How a subscription is drawn and where tapping it leads, whichever network
/// it belongs to.
///
/// The group editor had worked this out for its member list; the subscriptions
/// list had not, so everything that was not an X account fell through to a
/// saved-search row — a followed subreddit wore a search icon, said it was a
/// search term, and opened X's search for its own name.
///
/// Both read this now, and this asks the source that owns the subscription
/// rather than switching on its type, so a network added to the plugin folder
/// is drawn correctly here without this file being touched.
library;

import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/search/search.dart';
import 'package:xta/user.dart';

/// The plugin that owns [subscription], or null when it is one of X's own.
SubscriptionSource? sourceOf(Subscription subscription) {
  for (final source in subscriptionSources) {
    if (source.owns(subscription)) {
      return source;
    }
  }
  return null;
}

/// What a subscription is, under its name.
String subscriptionSubtitle(Subscription subscription) =>
    sourceOf(subscription)?.subtitleFor(subscription) ??
    switch (subscription) {
      SearchSubscription() => L10n.current.search_term,
      _ => '@${subscription.screenName}',
    };

/// The mark shown beside a subscription's name, [size] across when given; an
/// X account's otherwise follows the theme's avatar size.
Widget subscriptionAvatar(Subscription subscription, {double? size}) =>
    sourceOf(subscription)?.avatarFor(subscription, size: size ?? 40) ??
    switch (subscription) {
      SearchSubscription() => SizedBox(width: (size ?? 40) + 8, child: const Icon(Icons.search)),
      _ => UserAvatar(uri: subscription.profileImageUrlHttps, size: size ?? 48),
    };

/// Where tapping a subscription goes, or null when it has no screen to open.
Widget Function()? subscriptionDestination(Subscription subscription) =>
    sourceOf(subscription)?.destinationFor(subscription);

/// Opens [subscription]'s own screen: an X profile, its network's page, or X's
/// search for anything without one.
void openSubscription(BuildContext context, Subscription subscription) {
  if (subscription is UserSubscription) {
    Navigator.pushNamed(
      context,
      routeProfile,
      arguments: ProfileScreenArguments(subscription.id, subscription.screenName, null),
    );
    return;
  }
  final destination = subscriptionDestination(subscription);
  if (destination != null) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => destination()));
    return;
  }
  Navigator.pushNamed(
    context,
    routeSearch,
    arguments: SearchArguments(0, focusInputOnOpen: false, query: subscription.id),
  );
}
