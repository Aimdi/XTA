import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _domains = {
  'root',
  'file',
  'database',
  'sharedpref',
  'external',
  'device_root',
  'device_file',
  'device_database',
  'device_sharedpref',
};

final _comments = RegExp(r'<!--.*?-->', dotAll: true);

String _read(String path) => File(path).readAsStringSync().replaceAll(_comments, '');

Set<String> _excluded(String rules, String section) {
  final body = RegExp('<$section>(.*?)</$section>', dotAll: true).firstMatch(rules)?.group(1) ?? '';
  return {for (final match in RegExp(r'<exclude domain="([a-z_]+)"\s*/>').allMatches(body)) match.group(1)!};
}

void main() {
  final manifest = _read('android/app/src/main/AndroidManifest.xml');
  final rules = _read('android/app/src/main/res/xml/data_extraction_rules.xml');

  test('the app opts out of backup and names its extraction rules', () {
    expect(manifest, contains('android:allowBackup="false"'));
    expect(manifest, contains('android:dataExtractionRules="@xml/data_extraction_rules"'));
  });

  test('nothing goes to a cloud backup or a device-to-device transfer', () {
    expect(_excluded(rules, 'cloud-backup'), _domains);
    expect(_excluded(rules, 'device-transfer'), _domains);
    expect(rules, isNot(contains('<include')));
  });
}
