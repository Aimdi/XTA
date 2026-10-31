import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_repairs.dart';

/// The repairs file installs read, as they read it. Opt in with:
///   flutter test test/live/published_repairs_test.dart --dart-define=RUN_LIVE=true
///
/// Fails when the default branch's repairs.json cannot be read, or holds an
/// entry this build would refuse (docs/specs/published-repairs.md).
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'the published repairs file is readable and every entry fits',
    () async {
      final response = await http.get(Uri.parse(defaultPluginRepairsUrl));
      expect(response.statusCode, 200, reason: '$defaultPluginRepairsUrl answered ${response.statusCode}');

      final published = parsePluginRepairs(response.body);
      final slots = {for (final slot in PluginRepairs.all) slot.key: slot};
      bool fits(MapEntry<String, String> entry) => switch (slots[entry.key]) {
        final slot? => fitsRepairKind(slot.kind, entry.value),
        null => false,
      };
      final refused = [
        for (final entry in published.entries)
          if (!fits(entry)) entry.key,
      ];
      // ignore: avoid_print
      print('plugin repairs: ${published.length} published, ${published.length - refused.length} would be used');
      expect(published, isNotEmpty);
      expect(refused, isEmpty, reason: 'entries this build would refuse: ${refused.join(', ')}');
    },
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to read the published file',
  );
}
