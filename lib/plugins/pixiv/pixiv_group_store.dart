import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';

PluginAccountSubscription pixivSubscription(PixivUser user) => PluginAccountSubscription(pluginIdPixiv, {
  'id': '${user.id}',
  'screen_name': user.account,
  'name': user.name,
  'avatar_url': user.avatarUrl,
  'created_at': DateTime.now().toIso8601String(),
});

List<PluginAccountSubscription> readPixivGroupSubscriptions(BasePrefService prefs) {
  Object? value;
  try {
    value = jsonDecode(prefs.get<String>(optionPluginPixivGroupSubscriptions) ?? '[]');
  } on FormatException {
    return const [];
  }
  if (value is! List) return const [];
  return [
    for (final row in value.whereType<Map>())
      if (int.tryParse('${row['id']}') case final id? when id > 0)
        PluginAccountSubscription(pluginIdPixiv, Map<String, Object?>.from(row)),
  ];
}

/// Local author selections survive in the existing settings backup.
class PixivGroupSubscriptionsStore extends Store<List<PluginAccountSubscription>> {
  final BasePrefService prefs;

  PixivGroupSubscriptionsStore(this.prefs) : super(readPixivGroupSubscriptions(prefs));

  Future<void> add(PixivUser user) async {
    final next = pixivSubscription(user);
    final current = readPixivGroupSubscriptions(prefs);
    await _save([
      for (final item in current)
        if (item.id != next.id) item,
      next,
    ]);
  }

  Future<void> remove(String id) async {
    final current = readPixivGroupSubscriptions(prefs);
    await _save(current.where((item) => item.id != id).toList());
    final database = await Repository.writable();
    await database.delete(tableSubscriptionGroupMember, where: 'profile_id = ?', whereArgs: [id]);
  }

  Future<void> _save(List<PluginAccountSubscription> items) async {
    await prefs.set(optionPluginPixivGroupSubscriptions, jsonEncode(items.map((item) => item.toMap()).toList()));
    update(items);
  }
}
