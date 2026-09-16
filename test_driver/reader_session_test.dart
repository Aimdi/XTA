import 'dart:convert';
import 'dart:io';
import 'package:integration_test/integration_test_driver.dart';

Future<void> main() async {
  await integrationDriver(
    responseDataCallback: (data) async {
      final output = File('build/reader_session_summary.json');
      await output.parent.create(recursive: true);
      await output.writeAsString(const JsonEncoder.withIndent('  ').convert(data));
    },
  );
}
