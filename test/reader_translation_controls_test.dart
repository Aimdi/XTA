import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/reading/reader_translation_config.dart';
import 'package:xta/reading/reader_translation_controls.dart';
import 'package:xta/reading/reader_translation_service.dart';
import 'package:xta/reading/reader_translation_settings.dart';
import 'package:xta/reading/reader_translation_store.dart';

import 'support/reader_tools_harness.dart';

class _FakeService extends ReaderTranslationService {
  final requests = <String>[];
  bool fail = false;
  Completer<void>? hold;

  @override
  Future<String> translate(
    String text,
    ReaderTranslationConfig config, {
    required String language,
    bool Function()? current,
  }) async {
    requests.add(text);
    await hold?.future;
    if (fail) throw const ReaderTranslationException(ReaderTranslationFailure.provider);
    return 'ÜBERSETZT: $text';
  }
}

class _Host {
  final prefs = PrefServiceCache();
  final service = _FakeService();
  final cache = ReaderTranslationCache(MemoryJsonStore());

  Future<void> enable() async {
    final saved = await ReaderTranslationConfigStore.forPrefs(
      prefs,
    ).save(const ReaderTranslationConfig(provider: ReaderTranslationProvider.deepl, apiKey: 'k:fx'));
    expect(saved, isTrue);
  }

  Widget app(Widget child) => readerToolsApp(
    prefs,
    ReaderTranslationServiceScope(
      service: service,
      cache: cache,
      child: Scaffold(body: SingleChildScrollView(child: child)),
    ),
  );

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await ReaderTranslationConfigStore.forPrefs(prefs).destroy();
    await cache.destroy();
  }
}

Widget _plain(BuildContext context, String text) => Text(text, key: const ValueKey('body'));

MastodonPost _toot({String spoiler = ''}) => MastodonPost(
  id: '1',
  acct: 'someone@example.social',
  authorName: 'Someone',
  text: 'Guten Morgen aus Berlin',
  url: 'https://example.social/@someone/1',
  publishedAt: DateTime.now().subtract(const Duration(hours: 1)),
  spoilerText: spoiler,
);

void main() {
  testWidgets('nothing changes while translation is off', (tester) async {
    final host = _Host();
    await tester.pumpWidget(host.app(ReaderTranslation(text: 'Hallo', offer: true, builder: _plain)));
    await tester.pump();
    expect(find.text('Hallo'), findsOneWidget);
    expect(find.byKey(const ValueKey('reader-translate')), findsNothing);
    await host.close(tester);
  });

  testWidgets('tap to translate, show the original, and retry after a failure', (tester) async {
    final host = _Host();
    await host.enable();
    await tester.pumpWidget(host.app(ReaderTranslation(text: 'Hallo', offer: true, builder: _plain)));
    await tester.pump();
    host.service.fail = true;
    await tester.tap(find.byKey(const ValueKey('reader-translate')));
    await tester.pumpAndSettle();
    expect(find.text("Couldn't translate this"), findsOneWidget);
    expect(find.text('Hallo'), findsOneWidget);

    host.service.fail = false;
    await tester.tap(find.byKey(const ValueKey('reader-translate-retry')));
    await tester.pumpAndSettle();
    expect(find.text('ÜBERSETZT: Hallo'), findsOneWidget);
    expect(find.text('Translated'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('reader-translate-original')));
    await tester.pumpAndSettle();
    expect(find.text('Hallo'), findsOneWidget);
    expect(host.service.requests, ['Hallo', 'Hallo']);
    await host.close(tester);
  });

  testWidgets('touch targets stay at least 48dp at twice the text size', (tester) async {
    final host = _Host();
    await host.enable();
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(size: Size(320, 800), textScaler: TextScaler.linear(2)),
        child: host.app(ReaderTranslation(text: 'Hallo', offer: true, builder: _plain)),
      ),
    );
    await tester.pump();
    expect(tester.getSize(find.byKey(const ValueKey('reader-translate'))).height, greaterThanOrEqualTo(48));
    expect(tester.takeException(), isNull);
    await host.close(tester);
  });

  testWidgets('an action translates the text in place where it is shown, else in a sheet', (tester) async {
    final host = _Host();
    await host.enable();
    await tester.pumpWidget(
      host.app(
        Builder(
          builder: (context) => Column(
            children: [
              ReaderTranslation(text: 'Hallo', builder: _plain),
              TextButton(onPressed: () => toggleReaderTranslation(context, 'Hallo'), child: const Text('in place')),
              TextButton(onPressed: () => toggleReaderTranslation(context, 'Tschüss'), child: const Text('sheet')),
            ],
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.tap(find.text('in place'));
    await tester.pumpAndSettle();
    expect(find.text('ÜBERSETZT: Hallo'), findsOneWidget);
    await tester.tap(find.text('in place'));
    await tester.pumpAndSettle();
    expect(find.text('Hallo'), findsOneWidget);

    await tester.tap(find.text('sheet'));
    await tester.pumpAndSettle();
    expect(find.text('ÜBERSETZT: Tschüss'), findsOneWidget);
    expect(host.cache.requested('Tschüss'), isFalse);
    await host.close(tester);
  });

  testWidgets('an opened Mastodon post offers translation only once its warning is revealed', (tester) async {
    final host = _Host();
    await host.enable();
    await tester.pumpWidget(host.app(MastodonPostCard(post: _toot(spoiler: 'Spoiler'), openOnTap: false)));
    await tester.pump();
    expect(find.text('Guten Morgen aus Berlin'), findsNothing);
    expect(find.byKey(const ValueKey('reader-translate')), findsNothing);

    host.cache.request('Guten Morgen aus Berlin');
    await tester.pumpAndSettle();
    expect(find.textContaining('ÜBERSETZT'), findsNothing);
    expect(host.service.requests, isEmpty);

    await tester.tap(find.text('Show'));
    await tester.pumpAndSettle();
    expect(find.textContaining('ÜBERSETZT: Guten Morgen aus Berlin'), findsOneWidget);
    await host.close(tester);
  });

  testWidgets('a timeline Mastodon card keeps its layout until asked', (tester) async {
    final host = _Host();
    await host.enable();
    await tester.pumpWidget(host.app(MastodonPostCard(post: _toot())));
    await tester.pump();
    expect(find.byKey(const ValueKey('reader-translate')), findsNothing);
    expect(find.text('Guten Morgen aus Berlin'), findsOneWidget);
    await host.close(tester);
  });

  testWidgets('settings save a provider, its key and a target language', (tester) async {
    final host = _Host();
    tester.view.physicalSize = const Size(400, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(readerToolsApp(host.prefs, ReaderTranslationSettings(cacheStorage: MemoryJsonStore())));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('translation-provider')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('DeepL').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('translation-save')));
    await tester.pumpAndSettle();
    expect(find.text('Check the server address and API key'), findsOneWidget);
    expect(ReaderTranslationConfigStore.forPrefs(host.prefs).state.enabled, isFalse);

    await tester.enterText(find.byKey(const ValueKey('translation-key')), 'my-key:fx');
    await tester.tap(find.byKey(const ValueKey('translation-target')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Deutsch').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('translation-save')));
    await tester.pumpAndSettle();
    final config = ReaderTranslationConfigStore.forPrefs(host.prefs).state;
    expect(config.provider, ReaderTranslationProvider.deepl);
    expect(config.apiKey, 'my-key:fx');
    expect(config.target, 'de');
    expect(find.text('Translation settings saved'), findsOneWidget);
    await host.close(tester);
  });
}
