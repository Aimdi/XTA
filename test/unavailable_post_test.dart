import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/unavailable_post.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/urls.dart';

const _launcher = MethodChannel('plugins.flutter.io/url_launcher');
const _browserResolver = MethodChannel('browser_resolver');

const _archive = 'https://web.archive.org/web/*/twitter.com/quax_tests/status/2095934459606376826*';

Future<void> _pump(
  WidgetTester tester,
  Widget tile, {
  double width = 411,
  double textScale = 1,
  bool dark = false,
}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = Size(width, 800);
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetPhysicalSize);

  await tester.pumpWidget(
    PrefService(
      // The reader picked a real browser, so the link leaves the app and the
      // launch can be read off the platform channel.
      service: PrefServiceCache(cache: {optionOpenLinksInEmbeddedBrowser: false}),
      child: MaterialApp(
        theme: dark ? xLookLightsOutTheme(null) : ThemeData.light(),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: const [Locale('en')],
        home: MediaQuery(
          data: MediaQueryData(size: Size(width, 800), textScaler: TextScaler.linear(textScale)),
          child: Scaffold(body: SingleChildScrollView(child: tile)),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  group('waybackSearchUri', () {
    test('lists the captures of the post under its twitter.com address', () {
      expect(
        waybackSearchUri('quax_tests', '2095934459606376826').toString(),
        _archive,
        reason: 'most captures predate x.com, and the star also matches the addresses with a query',
      );
    });

    test('refuses what is not a handle or a post id', () {
      expect(waybackSearchUri('quax tests', '1'), isNull);
      expect(waybackSearchUri('../x', '1'), isNull);
      expect(waybackSearchUri('quax_tests', '12a'), isNull);
      expect(waybackSearchUri('', '1'), isNull);
    });
  });

  group('parsePostLink', () {
    test('reads the author and id of a quoted post permalink', () {
      final post = parsePostLink(Uri.parse('https://twitter.com/quax_tests/status/2095934459606376826'));

      expect(post?.screenName, 'quax_tests');
      expect(post?.id, '2095934459606376826');
    });

    test('a profile link is not a post', () {
      expect(parsePostLink(Uri.parse('https://x.com/quax_tests')), isNull);
    });
  });

  group('UnavailablePostTile', () {
    final launched = <String>[];

    setUp(() {
      final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(_launcher, (call) async {
        launched.add((call.arguments as Map)['url'] as String);
        return true;
      });
      messenger.setMockMethodCallHandler(_browserResolver, (_) async => null);
    });

    tearDown(() {
      final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(_launcher, null);
      messenger.setMockMethodCallHandler(_browserResolver, null);
      launched.clear();
    });

    testWidgets('without the author there is nothing to look up', (tester) async {
      await _pump(tester, const UnavailablePostTile(message: 'This post is unavailable.', id: '2095934459606376826'));

      expect(find.text('This post is unavailable.'), findsOneWidget);
      expect(find.text('Open on web.archive.org'), findsNothing);
    });

    testWidgets('one tap opens its captures through the reader\'s browser choice', (tester) async {
      await _pump(
        tester,
        const UnavailablePostTile(
          message: 'This Post was deleted by the Post author.',
          screenName: 'quax_tests',
          id: '2095934459606376826',
        ),
      );

      final button = find.widgetWithText(TextButton, 'Open on web.archive.org');
      expect(button, findsOneWidget);
      expect(tester.getSize(button).height, greaterThanOrEqualTo(48), reason: 'an Android touch target');

      await tester.tap(button);
      await tester.pumpAndSettle();

      expect(launched, [_archive]);
    });

    for (final (name, dark) in [('light', false), ('dark', true)]) {
      testWidgets('fits a 320dp phone at twice the text size ($name)', (tester) async {
        await _pump(
          tester,
          const UnavailablePostTile(
            message: 'This post is unavailable. It was probably deleted.',
            screenName: 'quax_tests',
            id: '2095934459606376826',
          ),
          width: 320,
          textScale: 2,
          dark: dark,
        );

        expect(tester.takeException(), isNull, reason: 'no overflow');
        final button = tester.getRect(find.widgetWithText(TextButton, 'Open on web.archive.org'));
        expect(button.right, lessThanOrEqualTo(320));
        expect(
          button.top,
          greaterThan(tester.getRect(find.textContaining('probably deleted')).bottom - 1),
          reason: 'the action sits under the message rather than squeezing it',
        );
      });
    }
  });
}
