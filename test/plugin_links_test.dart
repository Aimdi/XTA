import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_links.dart';

void main() {
  testWidgets('openWithPlugins ignores plugin URLs when plugins are off', (
    tester,
  ) async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginRedditEnabled: false,
        optionPluginBlueskyEnabled: false,
        optionPluginSubstackEnabled: false,
      },
    );
    var handled = true;

    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MaterialApp(
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () async {
                handled = await openWithPlugins(
                  context,
                  'https://www.reddit.com/r/dartlang',
                );
              },
              child: const Text('open'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(handled, isFalse);
  });
}
