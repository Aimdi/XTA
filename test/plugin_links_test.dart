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

  testWidgets('openNativeLink handles X profiles without browser fallback', (
    tester,
  ) async {
    final prefs = PrefServiceCache(cache: {
      optionPluginSubstackEnabled: false,
    });
    var handled = false;

    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MaterialApp(
          routes: {
            routeProfile: (_) => const Scaffold(body: Text('native profile')),
          },
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () async {
                handled = await openNativeLink(
                  context,
                  'https://x.com/example',
                );
              },
              child: const Text('open native'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('open native'));
    await tester.pumpAndSettle();

    expect(handled, isTrue);
    expect(find.text('native profile'), findsOneWidget);
  });

  testWidgets('openNativeLink leaves ordinary web links for the caller', (
    tester,
  ) async {
    final prefs = PrefServiceCache(cache: {
      optionPluginSubstackEnabled: false,
    });
    var handled = true;

    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MaterialApp(
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () async {
                handled = await openNativeLink(
                  context,
                  'https://example.com/article',
                );
              },
              child: const Text('open web'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('open web'));
    await tester.pump();

    expect(handled, isFalse);
  });

}
