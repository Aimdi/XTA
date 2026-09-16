/// Unfollowing a subscription on whatever network it came from.
///
/// [SubscriptionsModel.toggleSubscribe] only ever knew X accounts and saved
/// searches, so the unsubscribe item on a followed subreddit, publication or
/// Threads account silently did nothing. Each source's own store owns its
/// removal — it has to, or its own tab would go on listing what was removed.
library;

import 'package:pref/pref.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/utils/local_undo.dart';
import 'package:xta/plugins/subscription_source.dart';

import 'package:provider/provider.dart';
import 'package:xta/group/group_model.dart';

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

  final groups = context.read<GroupsModel>();
  final undo = await _removeWithUndo(context, source, subscription);
  UndoStore.shared.offer(undo);
  await groups.reloadGroups();
  return true;
}

Future<PendingUndo?> _removeWithUndo(BuildContext context, SubscriptionSource source, Subscription subscription) async {
  final preference = source.subscriptionPreferenceKey;
  if (preference != null) {
    final prefs = PrefService.of(context, listen: false);
    final before = prefs.get(preference);
    await source.unfollow(context, subscription);
    final after = prefs.get(preference);
    if (before == after) return null;
    return PendingUndo(() async {
      if (prefs.get(preference) != after) return false;
      await prefs.set(preference, before);
      return true;
    });
  }
  final database = await Repository.writable();
  final rows = await database.query(source.subscriptionTable);
  final matches = rows.where((row) => source.subscriptionFromMap(row).id == subscription.id);
  if (matches.isEmpty || !context.mounted) return null;
  final row = matches.first;
  final schema = await database.rawQuery('PRAGMA table_info(${source.subscriptionTable})');
  final keys = schema
      .where((column) => (column['pk'] as int? ?? 0) > 0)
      .map((column) => column['name'] as String)
      .toList();
  final slices = [
    UndoSlice(
      source.subscriptionTable,
      keys.isEmpty ? '1 = 1' : keys.map((key) => '$key IS ?').join(' AND '),
      keys.map((key) => row[key]).toList(),
    ),
    UndoSlice(tableSubscriptionGroupMember, 'profile_id = ?', [subscription.id]),
  ];
  final before = [for (final slice in slices) await slice.read(database)];
  if (!context.mounted) return null;
  await source.unfollow(context, subscription);
  final after = [for (final slice in slices) await slice.read(database)];
  return undoFromSnapshots(database, slices, before, after);
}
