import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/deepmarks/deepmarks_client.dart';
import 'package:xta/plugins/immich/immich_client.dart';
import 'package:xta/plugins/karakeep/karakeep_client.dart';
import 'package:xta/plugins/plugin_connection_store.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/settings/settings_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

class _Probe {
  var calls = 0;
  final response = Completer<void>();
  Future<void> run() async {
    calls++;
    await response.future;
  }
}

class _Karakeep extends KarakeepClient {
  final _Probe probe;
  _Karakeep(this.probe);
  @override
  Future<bool> verify({required String baseUrl, required String apiKey}) async {
    await probe.run();
    return true;
  }
}

class _Immich extends ImmichClient {
  final _Probe probe;
  _Immich(this.probe);
  @override
  Future<bool> verify({required String baseUrl, required String apiKey}) async {
    await probe.run();
    return true;
  }
}

class _Deepmarks extends DeepmarksClient {
  final _Probe probe;
  _Deepmarks(this.probe);
  @override
  Future<String?> verify({required String baseUrl, required String apiKey}) async {
    await probe.run();
    return null;
  }
}

void main() {
  for (final id in ['karakeep', 'deepmarks', 'immich']) {
    testWidgets('$id settings: large text, reachable test/save, stale probe invalidation', (tester) async {
      tester.view.physicalSize = const Size(320, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetViewInsets);
      final probe = _Probe();
      final prefs = PrefServiceCache(
        defaults: {
          optionPluginKarakeepServerUrl: 'https://example.test',
          optionPluginKarakeepApiKey: 'test-key',
          optionPluginImmichServerUrl: 'https://example.test',
          optionPluginImmichApiKey: 'test-key',
          optionPluginImmichAlbumPerFolder: false,
          optionPluginImmichIncludeVideos: false,
          optionPluginDeepmarksApiKey: 'test-key',
        },
      );
      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MultiProvider(
            providers: [
              Provider<KarakeepClient>(create: (_) => _Karakeep(probe)),
              Provider<ImmichClient>(create: (_) => _Immich(probe)),
              Provider<DeepmarksClient>(create: (_) => _Deepmarks(probe)),
            ],
            child: MaterialApp(
              theme: xLookThemeData(XLookTokens.lightsOut, null),
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(1.6)),
                child: child!,
              ),
              localizationsDelegates: const [
                L10n.delegate,
                GlobalMaterialLocalizations.delegate,
                GlobalWidgetsLocalizations.delegate,
                GlobalCupertinoLocalizations.delegate,
              ],
              supportedLocales: L10n.delegate.supportedLocales,
              home: Scaffold(
                body: Builder(
                  builder: (context) => TextButton(
                    onPressed: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (context) => pluginById(id)!.settingsScreen(context)!),
                    ),
                    child: const Text('Open setup'),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open setup'));
      await tester.pumpAndSettle();
      expect(probe.calls, 0, reason: 'Opening settings must not contact a service.');
      expect(tester.takeException(), isNull);
      Future<void> reveal(Finder target, {bool upward = false}) async {
        await tester.scrollUntilVisible(
          target,
          upward ? -220 : 220,
          scrollable: find.descendant(of: find.byType(SettingsList), matching: find.byType(Scrollable)).first,
          maxScrolls: 25,
        );
        await tester.ensureVisible(target);
        // A pending connection probe deliberately keeps a spinner animating.
        await tester.pump(const Duration(milliseconds: 200));
      }

      final testButton = find.byKey(const ValueKey('plugin-test-connection'));
      await reveal(testButton);
      await tester.pumpAndSettle();
      expect(tester.getSize(testButton).height, greaterThanOrEqualTo(48));
      await tester.tap(testButton);
      await tester.pump();
      expect(probe.calls, 1);
      final firstField = find.byKey(const ValueKey('plugin-connection-primary-field'));
      await reveal(firstField, upward: true);
      await tester.enterText(firstField, 'edited-value');
      tester.view.viewInsets = const FakeViewPadding(bottom: 240);
      probe.response.complete();
      await tester.pumpAndSettle();
      await reveal(testButton);
      var feedback = tester.widget<PluginConnectionFeedback>(find.byType(PluginConnectionFeedback));
      expect(feedback.state.status, PluginConnectionStatus.idle);
      expect(feedback.state.message, isNull, reason: 'An old request cannot certify edited credentials.');
      await tester.tap(testButton);
      await tester.pumpAndSettle();
      feedback = tester.widget<PluginConnectionFeedback>(find.byType(PluginConnectionFeedback));
      expect(feedback.state.status, id == 'deepmarks' ? PluginConnectionStatus.warning : PluginConnectionStatus.ok);
      await reveal(firstField, upward: true);
      await tester.enterText(firstField, 'final-value');
      await tester.pumpAndSettle();
      await reveal(testButton);
      expect(tester.widget<PluginConnectionFeedback>(find.byType(PluginConnectionFeedback)).state.message, isNull);
      final save = find.widgetWithText(OutlinedButton, 'Save');
      await reveal(save);
      await tester.tap(save);
      await tester.pumpAndSettle();
      expect(find.text('Open setup'), findsOneWidget);
      expect(probe.calls, 2);
      expect(tester.takeException(), isNull);
    });
  }
}
