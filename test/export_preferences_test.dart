import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/hackernews/hn_group.dart';
import 'package:xta/plugins/pixiv/pixiv_group_store.dart';
import 'package:xta/settings/backup_data.dart';
import 'package:xta/settings/export_preferences.dart';

void main() {
  final preferences = <String, dynamic>{
    optionPluginPixivGroupSubscriptions: jsonEncode([
      {'id': '123', 'name': 'Artist', 'screen_name': 'artist'},
    ]),
    optionPluginHnFollows: jsonEncode(['author']),
    optionPluginPixivAccessToken: 'secret',
    optionThemeTrueBlack: true,
  };

  test('subscription export includes preference-backed authors without other settings', () {
    final selected = preferencesForExport(preferences, includeSettings: false, includeSubscriptions: true);
    expect(selected!.keys.toSet(), {optionPluginPixivGroupSubscriptions, optionPluginHnFollows});
    expect(selected.containsKey(optionPluginPixivAccessToken), isFalse);
    expect(selected.containsKey(optionThemeTrueBlack), isFalse);
  });

  test('group membership ids still resolve after subscription-only export round trip', () {
    final data = SettingsData(
      settings: preferencesForExport(preferences, includeSettings: false, includeSubscriptions: true),
      subscriptionGroupMembers: [
        SubscriptionGroupMember(group: 'art', profile: 'pixiv:123'),
        SubscriptionGroupMember(group: 'tech', profile: '$pluginIdHackerNews:author'),
      ],
    );
    final restored = SettingsData.fromJson(jsonDecode(jsonEncode(data.toJson())) as Map<String, dynamic>);
    final prefs = PrefServiceCache(cache: restored.settings!);
    final ids = {
      ...readPixivGroupSubscriptions(prefs).map((item) => item.id),
      ...readHnSubscriptions(prefs).map((item) => item.id),
    };
    for (final member in restored.subscriptionGroupMembers!) {
      expect(ids, contains(member.profile));
    }
  });

  test('settings choice keeps normal preferences and still strips credentials', () {
    final selected = preferencesForExport(preferences, includeSettings: true, includeSubscriptions: false);
    expect(selected![optionThemeTrueBlack], isTrue);
    expect(selected.containsKey(optionPluginPixivAccessToken), isFalse);
  });

  test('excluding both settings and subscriptions exports no preferences', () {
    expect(preferencesForExport(preferences, includeSettings: false, includeSubscriptions: false), isNull);
  });
}
