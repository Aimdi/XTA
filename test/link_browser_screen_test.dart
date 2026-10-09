import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/links/link_browser_page.dart';
import 'package:xta/links/link_browser_screen.dart';
import 'package:xta/links/link_browser_store.dart';
import 'package:xta/links/link_opening.dart';
import 'package:xta/links/link_post_context.dart';
import 'package:xta/links/link_post_context_bar.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/ui/x_look_theme.dart';

const _article = 'https://www.washingtonpost.com/politics/budget-vote/';

/// Stands in for the webview: reports a finished load straight away and
/// remembers what the chrome asked of it.
class _FakePage implements LinkBrowserPage {
  final LinkBrowserStore store;
  final loaded = <String>[];
  var reloads = 0;
  var history = 0;

  _FakePage(this.store);

  @override
  Widget build(BuildContext context) => const ColoredBox(key: ValueKey('page'), color: Colors.white);

  @override
  Future<void> load(String url) async {
    loaded.add(url);
    store.finished(url);
  }

  @override
  Future<void> reload() async => reloads++;

  @override
  Future<bool> back() async {
    if (history == 0) return false;
    history--;
    return true;
  }
}

class _Harness {
  _FakePage? page;
  final shared = <String>[];
  var postOpened = 0;

  LinkBrowserPage factory(LinkBrowserStore store, LinkNavigationGuard _) => page = _FakePage(store);

  LinkPostContext post({bool canOpen = true, int? likes = 26}) => LinkPostContext(
    sourceId: 'threads',
    author: 'Daily Reader',
    replies: 33,
    reposts: 9,
    likes: likes,
    postUrl: 'https://www.threads.com/@reader/post/abc',
    openPost: canOpen ? () => postOpened++ : null,
  );

  LinkBrowserScreen screen({LinkPostContext? post, String? title}) => LinkBrowserScreen(
    url: _article,
    title: title,
    post: post,
    pageFactory: factory,
    share: (url) async => shared.add(url),
  );
}

Widget _app(Widget home, {Map<String, Object> prefs = const {}, double textScale = 1, bool dark = true}) => PrefService(
  service: PrefServiceCache(cache: prefs),
  child: MaterialApp(
    theme: dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
      child: child!,
    ),
    home: home,
  ),
);

/// A post screen with a button that opens the browser over it.
Widget _postScreen(Widget Function() browser) => Builder(
  builder: (context) => Scaffold(
    body: Center(
      child: TextButton(
        onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => browser())),
        child: const Text('the post'),
      ),
    ),
  ),
);

Future<void> _openBrowser(WidgetTester tester) async {
  await tester.tap(find.text('the post'));
  await tester.pumpAndSettle();
}

void _phone(WidgetTester tester, {double width = 400}) {
  tester.view.physicalSize = Size(width * 3, 2400);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
}

void main() {
  testWidgets('the top bar names the domain, the network and the padlock', (tester) async {
    _phone(tester);
    final harness = _Harness();
    await tester.pumpWidget(_app(harness.screen(post: harness.post())));
    await tester.pumpAndSettle();

    expect(harness.page!.loaded, [_article]);
    expect(find.text('washingtonpost.com'), findsOneWidget);
    expect(find.text('Threads'), findsOneWidget);
    expect(find.byIcon(Icons.lock_outline), findsOneWidget);
    expect(find.byTooltip('Close'), findsOneWidget);
    expect(find.byTooltip('More options'), findsOneWidget);
    expect(find.byKey(const ValueKey('page')), findsOneWidget);
  });

  testWidgets('tracking is stripped before the page loads', (tester) async {
    _phone(tester);
    final harness = _Harness();
    await tester.pumpWidget(_app(LinkBrowserScreen(url: '$_article?utm_source=x&id=7', pageFactory: harness.factory)));
    await tester.pumpAndSettle();

    expect(harness.page!.loaded, ['$_article?id=7']);
  });

  testWidgets('the bottom bar carries the post: counts, share and the mark', (tester) async {
    _phone(tester);
    final harness = _Harness();
    await tester.pumpWidget(_app(harness.screen(post: harness.post())));
    await tester.pumpAndSettle();

    final bar = find.byType(LinkPostContextBar);
    expect(bar, findsOneWidget);
    for (final count in ['26', '33', '9']) {
      expect(find.descendant(of: bar, matching: find.text(count)), findsOneWidget);
    }
    expect(find.byIcon(Icons.favorite_border), findsOneWidget);
    expect(find.byIcon(Icons.mode_comment_outlined), findsOneWidget);
    expect(find.byIcon(Icons.repeat), findsOneWidget);

    await tester.tap(find.byTooltip('Share post link'));
    expect(harness.shared, ['https://www.threads.com/@reader/post/abc']);
  });

  testWidgets('the bar reads as one "back to post" control', (tester) async {
    _phone(tester);
    final handle = tester.ensureSemantics();
    final harness = _Harness();
    await tester.pumpWidget(_app(harness.screen(post: harness.post())));
    await tester.pumpAndSettle();

    expect(find.bySemanticsLabel('Back to post, Daily Reader, Likes: 26, Replies: 33, Reposts: 9'), findsOneWidget);
    handle.dispose();
  });

  testWidgets('zen mode hides the counts here too', (tester) async {
    _phone(tester);
    final harness = _Harness();
    await tester.pumpWidget(_app(harness.screen(post: harness.post()), prefs: {optionZenMode: true}));
    await tester.pumpAndSettle();

    expect(find.text('26'), findsNothing);
    expect(find.byIcon(Icons.favorite_border), findsOneWidget);
  });

  testWidgets('tapping the bar closes the browser and opens the post', (tester) async {
    _phone(tester);
    final harness = _Harness();
    await tester.pumpWidget(_app(_postScreen(() => harness.screen(post: harness.post()))));
    await _openBrowser(tester);

    await tester.tap(find.text('26'));
    await tester.pumpAndSettle();

    expect(find.byType(LinkBrowserScreen), findsNothing);
    expect(find.text('the post'), findsOneWidget);
    expect(harness.postOpened, 1);
  });

  testWidgets('on the post\'s own screen the bar just goes back to it', (tester) async {
    _phone(tester);
    final harness = _Harness();
    await tester.pumpWidget(_app(_postScreen(() => harness.screen(post: harness.post(canOpen: false)))));
    await _openBrowser(tester);

    await tester.tap(find.byType(LinkPostContextBar));
    await tester.pumpAndSettle();

    expect(find.byType(LinkBrowserScreen), findsNothing);
    expect(find.text('the post'), findsOneWidget);
    expect(harness.postOpened, 0);
  });

  testWidgets('back steps through the page before closing the browser', (tester) async {
    _phone(tester);
    final harness = _Harness();
    await tester.pumpWidget(_app(_postScreen(() => harness.screen())));
    await _openBrowser(tester);
    harness.page!.history = 1;

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.byType(LinkBrowserScreen), findsOneWidget);
    expect(harness.page!.history, 0);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.byType(LinkBrowserScreen), findsNothing);
  });

  testWidgets('the close button leaves at once', (tester) async {
    _phone(tester);
    final harness = _Harness();
    await tester.pumpWidget(_app(_postScreen(() => harness.screen())));
    await _openBrowser(tester);
    harness.page!.history = 3;

    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();

    expect(find.byType(LinkBrowserScreen), findsNothing);
  });

  testWidgets('the menu offers the browser, copy, share and reload', (tester) async {
    _phone(tester);
    final copied = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') {
        copied.add((call.arguments as Map)['text'] as String);
      }
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
    final harness = _Harness();
    await tester.pumpWidget(_app(harness.screen(post: harness.post())));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('More options'));
    await tester.pumpAndSettle();
    for (final label in ['Open in browser', 'Copy link', 'Share link', 'Reload page']) {
      expect(find.text(label), findsOneWidget, reason: label);
    }

    await tester.tap(find.text('Copy link'));
    await tester.pumpAndSettle();
    expect(copied, [_article]);
    expect(find.text('Link copied'), findsOneWidget);

    await tester.tap(find.byTooltip('More options'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Share link'));
    await tester.pumpAndSettle();
    expect(harness.shared, [_article]);

    await tester.tap(find.byTooltip('More options'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Reload page'));
    await tester.pumpAndSettle();
    expect(harness.page!.reloads, 1);
  });

  testWidgets('without a post there is no bar; the title names the page', (tester) async {
    _phone(tester);
    final harness = _Harness();
    await tester.pumpWidget(_app(harness.screen(title: 'Article on X')));
    await tester.pumpAndSettle();

    expect(find.byType(LinkPostContextBar), findsNothing);
    expect(find.text('Article on X'), findsOneWidget);
  });

  testWidgets('chrome fits 320dp at double text size in light and dark', (tester) async {
    for (final dark in [true, false]) {
      _phone(tester, width: 320);
      final harness = _Harness();
      await tester.pumpWidget(_app(harness.screen(post: harness.post(likes: 1234567)), textScale: 2, dark: dark));
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(find.text(compactCount(1234567)), findsOneWidget);
      final close = tester.getSize(find.ancestor(of: find.byIcon(Icons.close), matching: find.byType(IconButton)));
      expect(close.width, greaterThanOrEqualTo(48));
      expect(close.height, greaterThanOrEqualTo(48));
      final bar = tester.getSize(find.byType(LinkPostContextBar));
      expect(bar.height, greaterThanOrEqualTo(48));
    }
  });

  group('openPostLink honours the browser setting', () {
    const launcher = MethodChannel('plugins.flutter.io/url_launcher');
    const resolver = MethodChannel('browser_resolver');
    final launched = <String>[];

    setUp(() {
      final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(launcher, (call) async {
        launched.add((call.arguments as Map)['url'] as String);
        return true;
      });
      messenger.setMockMethodCallHandler(resolver, (_) async => null);
    });

    tearDown(() {
      final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(launcher, null);
      messenger.setMockMethodCallHandler(resolver, null);
      launched.clear();
    });

    Future<_Harness> tapLink(WidgetTester tester, bool embedded) async {
      _phone(tester);
      final harness = _Harness();
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => TextButton(
              onPressed: () => openPostLink(
                context,
                _article,
                post: harness.post(),
                openNative: (_, _) async => false,
                pageFactory: harness.factory,
              ),
              child: const Text('link'),
            ),
          ),
          prefs: {optionOpenLinksInEmbeddedBrowser: embedded},
        ),
      );
      await tester.tap(find.text('link'));
      await tester.pumpAndSettle();
      return harness;
    }

    testWidgets('in the app: the browser with the post bar', (tester) async {
      await tapLink(tester, true);

      expect(find.byType(LinkBrowserScreen), findsOneWidget);
      expect(find.byType(LinkPostContextBar), findsOneWidget);
      expect(launched, isEmpty);
    });

    testWidgets('a chosen browser: the link leaves the app', (tester) async {
      await tapLink(tester, false);

      expect(find.byType(LinkBrowserScreen), findsNothing);
      expect(launched, [_article]);
    });

    testWidgets('a link an XTA screen reads never reaches a browser', (tester) async {
      _phone(tester);
      final opened = <String>[];
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => TextButton(
              onPressed: () => openPostLink(
                context,
                'https://bsky.app/profile/a/post/b',
                openNative: (_, url) async {
                  opened.add(url);
                  return true;
                },
              ),
              child: const Text('link'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('link'));
      await tester.pumpAndSettle();

      expect(opened, ['https://bsky.app/profile/a/post/b']);
      expect(find.byType(LinkBrowserScreen), findsNothing);
      expect(launched, isEmpty);
    });
  });
}
