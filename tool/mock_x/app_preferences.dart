/// What XTA starts with on the emulator: past the first-run intro and without
/// the update check, so no card or dialog covers the screens under test.
const emulatorPreferences = {'intro.seen': true, 'should_check_for_updates': false};

/// The shared_prefs/FlutterSharedPreferences.xml from which the pref package
/// (prefix `pref_`) reads [booleans].
String sharedPreferencesXml(Map<String, bool> booleans) => [
  "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>",
  '<map>',
  for (final MapEntry(:key, :value) in booleans.entries) '    <boolean name="flutter.pref_$key" value="$value" />',
  '</map>',
  '',
].join('\n');
