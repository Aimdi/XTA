/// The followed lists of web sources, kept in preferences as subscriptions,
/// so the settings backup carries them and groups can hold them.
library;

import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/utils/json.dart';

/// A followed list's own id: its source and its list.
String webSourceFollowKey(String sourceId, String listId) => '$sourceId/$listId';

/// The subscription id of a followed list.
String webSourceSubscriptionId(String sourceId, String listId) =>
    '$pluginIdWebSources:${webSourceFollowKey(sourceId, listId)}';

/// The source and list a followed list's [key] names.
({String sourceId, String listId})? webSourceFollowParts(String key) {
  final at = key.indexOf('/');
  return at <= 0 || at == key.length - 1 ? null : (sourceId: key.substring(0, at), listId: key.substring(at + 1));
}

Map<String, Object?> webSourceFollowRow(WebSourceDefinition source, WebSourceList list, DateTime now) => {
  'id': webSourceFollowKey(source.id, list.id),
  'name': source.name,
  'screen_name': list.name,
  'avatar_url': source.icon?.toString(),
  'in_feed': 1,
  'created_at': now.toIso8601String(),
};

List<PluginAccountSubscription> readWebSourceFollows(BasePrefService prefs) {
  final Object? decoded;
  try {
    decoded = jsonDecode(prefs.get<String>(optionPluginWebSourcesFollows) ?? '[]');
  } on FormatException {
    return const [];
  }
  return [
    for (final row in Json(decoded).list)
      if (row.raw case final Map<String, Object?> map when webSourceFollowParts('${map['id']}') != null)
        PluginAccountSubscription(pluginIdWebSources, map),
  ];
}

class WebSourceFollowsStore extends Store<List<PluginAccountSubscription>> {
  final BasePrefService prefs;

  WebSourceFollowsStore(this.prefs) : super(readWebSourceFollows(prefs));

  Future<void> load() async => update(readWebSourceFollows(prefs));

  bool isFollowing(String sourceId, String listId) =>
      state.any((follow) => follow.accountId == webSourceFollowKey(sourceId, listId));

  Future<void> follow(WebSourceDefinition source, WebSourceList list, {DateTime? now}) async {
    final row = webSourceFollowRow(source, list, now ?? DateTime.now());
    await _save([
      for (final follow in readWebSourceFollows(prefs))
        if (follow.accountId != row['id']) follow,
      PluginAccountSubscription(pluginIdWebSources, row),
    ]);
  }

  /// Stops following [subscriptionId], and takes it out of every group.
  Future<void> unfollow(String subscriptionId) => _remove({subscriptionId});

  /// Stops following every list of [sourceId], as removing it does.
  Future<void> removeSource(String sourceId) => _remove({
    for (final follow in readWebSourceFollows(prefs))
      if (webSourceFollowParts(follow.accountId)?.sourceId == sourceId) follow.id,
  });

  Future<void> _remove(Set<String> ids) async {
    if (ids.isEmpty) return;
    await _save(readWebSourceFollows(prefs).where((follow) => !ids.contains(follow.id)).toList());
    final database = await Repository.writable();
    final batch = database.batch();
    for (final id in ids) {
      batch.delete(tableSubscriptionGroupMember, where: 'profile_id = ?', whereArgs: [id]);
    }
    await batch.commit(noResult: true);
  }

  Future<void> _save(List<PluginAccountSubscription> follows) async {
    await prefs.set(optionPluginWebSourcesFollows, jsonEncode([for (final follow in follows) follow.toMap()]));
    update(follows);
  }
}
