import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';

/// Live guest TikTok search. Opt in with:
///   fvm flutter test test/live/tiktok_search_smoke_test.dart --dart-define=RUN_LIVE=true
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'guest search finds people and suggestions without signing',
    () async {
      final client = TikTokClient(PrefServiceCache(cache: {}));
      final trending = await client.trendingQueries();
      expect(trending, isNotEmpty);

      final discover = await client.discoverUsers();
      expect(discover, isNotEmpty);
      expect(discover.first.uniqueId, isNotEmpty);

      final page = await client.search('nba');
      expect(page.users.map((u) => u.uniqueId.toLowerCase()), contains('nba'));
      expect(page.suggestions, isNotEmpty);
    },
    skip: enabled ? false : 'Set RUN_LIVE=true',
  );
}
