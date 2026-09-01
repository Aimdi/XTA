import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';

/// Live Instagram guest probe. Opt in with:
///   fvm flutter test test/live/instagram_search_smoke_test.dart --dart-define=RUN_LIVE=true
///
/// Datacenter IPs usually get 429 / login HTML. The test must not fail on
/// that — it only asserts the client maps the response instead of crashing.
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'guest profile either answers or maps Instagram\'s block',
    () async {
      final client = InstagramClient(PrefServiceCache(cache: {}));
      try {
        final profile = await client.profile('instagram');
        expect(profile.username.toLowerCase(), 'instagram');
        expect(profile.id, isNotEmpty);
      } on InstagramException catch (e) {
        expect(
          e.kind,
          anyOf(
            InstagramErrorKind.rateLimited,
            InstagramErrorKind.loginRequired,
            InstagramErrorKind.network,
          ),
        );
      }
    },
    skip: enabled ? false : 'Set RUN_LIVE=true',
  );
}
