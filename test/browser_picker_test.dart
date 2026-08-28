import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/_browser_picker.dart';
import 'package:xta/utils/browsers.dart';

const MethodChannel _channel = MethodChannel('browser_resolver');

Widget _app(BasePrefService prefs) {
  return PrefService(
    service: prefs,
    child: MaterialApp(
      localizationsDelegates: [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      locale: const Locale('en'),
      home: Scaffold(
        body: Builder(
          builder: (context) => ListView(
            children: [
              const BrowserPickerTile(),
              PrefSwitch(
                title: Text(L10n.of(context).option_clean_links_label),
                subtitle: Text(L10n.of(context).option_clean_links_description),
                pref: optionCleanLinks,
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, (call) async {
      if (call.method == 'listBrowsers') {
        return [
          {'package': 'com.android.chrome', 'label': 'Chrome'},
          {'package': 'org.mozilla.firefox', 'label': 'Firefox'},
        ];
      }
      return null;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, null);
  });

  Future<PrefServiceCache> _pump(
    WidgetTester tester, {
    required Map<String, dynamic> cache,
  }) async {
    tester.view.physicalSize = const Size(800, 2000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final prefs = PrefServiceCache(cache: cache);
    await tester.pumpWidget(_app(prefs));
    await tester.pumpAndSettle();
    return prefs;
  }

  testWidgets('the destination tile sits above the clean-links switch', (tester) async {
    await _pump(tester, cache: {
      optionOpenLinksInEmbeddedBrowser: true,
      optionExternalBrowser: '',
      optionCleanLinks: true,
    });

    expect(find.text('Open links in'), findsOneWidget);
    expect(find.text('In the app'), findsOneWidget);
    expect(find.text('Clean tracking from links'), findsOneWidget);
    expect(
      find.text('Strip trackers and junk parameters before a link opens or is shared'),
      findsOneWidget,
    );

    final picker = tester.getTopLeft(find.text('Open links in'));
    final clean = tester.getTopLeft(find.text('Clean tracking from links'));
    expect(picker.dy, lessThan(clean.dy));
    expect(find.byType(DropdownButton<String?>), findsNothing);
  });

  testWidgets('picking a named browser turns the in-app view off', (tester) async {
    final prefs = await _pump(tester, cache: {
      optionOpenLinksInEmbeddedBrowser: true,
      optionExternalBrowser: '',
      optionCleanLinks: true,
    });

    await tester.tap(find.text('Open links in'));
    await tester.pumpAndSettle();

    expect(find.text('Firefox'), findsOneWidget);
    expect(find.text('Chrome'), findsOneWidget);
    expect(find.text('System default'), findsOneWidget);

    await tester.tap(find.text('Firefox'));
    await tester.pumpAndSettle();

    expect(prefs.get(optionOpenLinksInEmbeddedBrowser), isFalse);
    expect(prefs.get(optionExternalBrowser), 'org.mozilla.firefox');
    expect(find.text('Firefox'), findsOneWidget);
  });

  testWidgets('picking in-app again leaves the last browser stored', (tester) async {
    final prefs = await _pump(tester, cache: {
      optionOpenLinksInEmbeddedBrowser: false,
      optionExternalBrowser: 'org.mozilla.firefox',
      optionCleanLinks: true,
    });

    expect(find.text('Firefox'), findsOneWidget);

    await tester.tap(find.text('Open links in'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('In the app').last);
    await tester.pumpAndSettle();

    expect(prefs.get(optionOpenLinksInEmbeddedBrowser), isTrue);
    expect(prefs.get(optionExternalBrowser), 'org.mozilla.firefox');
  });

  test('installedBrowsers maps the platform list', () async {
    final browsers = await installedBrowsers();
    expect(browsers, [
      (package: 'com.android.chrome', label: 'Chrome'),
      (package: 'org.mozilla.firefox', label: 'Firefox'),
    ]);
  });
}
