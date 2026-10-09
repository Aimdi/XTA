import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_model.dart';
import 'package:xta/intro/intro_screen.dart';
import 'package:xta/intro/intro_store.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/ui/x_look_theme.dart';

class _CountingHome extends HomeModel {
  var loads = 0;

  _CountingHome(super.prefs, super.groupsModel);

  @override
  Future<void> loadPages() async {
    loads++;
  }
}

Future<List<Account>> _noAccounts() async => const [];

Future<List<Account>> _oneAccount() async => [Account(id: '1', authHeader: null, screenName: 'reader')];

/// A fresh install, replayed the way `main` writes it.
class _Harness {
  final BasePrefService prefs;
  final _CountingHome home;
  final FeedStripStore strip;
  final IntroStore intro;

  _Harness._(this.prefs, this.home, this.strip, this.intro);

  static Future<_Harness> freshInstall() async {
    final prefs = PrefServiceCache();
    await prefs.setDefaultValues({
      ...introLaunchDefaults(firstLaunch: true),
      optionHomePages: ['feed', 'subscriptions', 'trending', 'saved'],
      optionSeededPluginTabs: <String>[],
      optionSeededStripPlugins: <String>[],
      for (final plugin in builtInPlugins) plugin.enabledPrefKey: false,
      optionDisableAnimations: true,
      optionConfirmClose: true,
      optionXLookBackground: xLookBackgroundSystem,
      optionXLookAccent: xLookAccentBlue,
    });
    await migrateFeedStripPins(prefs, firstLaunch: true);
    final groups = GroupsModel(prefs);
    final home = _CountingHome(prefs, groups);
    final strip = FeedStripStore(prefs);
    final intro = IntroStore(prefs);
    addTearDown(() async {
      await home.destroy();
      await groups.destroy();
      await strip.destroy();
      await intro.destroy();
    });
    return _Harness._(prefs, home, strip, intro);
  }

  Future<void> pump(
    WidgetTester tester, {
    int page = 0,
    Future<List<Account>> Function() accounts = _noAccounts,
  }) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<HomeModel>.value(value: home),
            Provider<FeedStripStore>.value(value: strip),
            Provider<IntroStore>.value(value: intro),
          ],
          child: MaterialApp(
            theme: xLookLightsOutTheme(null),
            locale: const Locale('en'),
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: IntroScreen(initialPage: page, accountsLoader: accounts),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }
}

Finder _key(String value) => find.byKey(ValueKey(value));

Future<void> _tap(WidgetTester tester, String key) async {
  await tester.tap(_key(key));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('Skip marks the intro seen, reloads Home once and changes no setting', (tester) async {
    final h = await _Harness.freshInstall();
    await h.pump(tester);

    await _tap(tester, 'intro-skip');

    expect(h.intro.state, isTrue);
    expect(h.prefs.get<bool>(optionIntroSeen), isTrue);
    // A source toggled on an earlier card reaches Home's tabs now, not on the next launch.
    expect(h.home.loads, 1);
    expect(feedStripPluginIds(h.prefs), isEmpty);
  });

  testWidgets('Next advances and the indicator announces the page', (tester) async {
    final h = await _Harness.freshInstall();
    final semantics = tester.ensureSemantics();
    await h.pump(tester);
    expect(find.bySemanticsLabel('Page 1 of 5'), findsOneWidget);

    await _tap(tester, 'intro-next');

    expect(find.text('Stays on this device'), findsOneWidget);
    expect(
      tester.getSemantics(find.bySemanticsLabel('Page 2 of 5')),
      isSemantics(label: 'Page 2 of 5', isLiveRegion: true),
    );
    semantics.dispose();
  });

  testWidgets('system Back on the third card returns to the second', (tester) async {
    final h = await _Harness.freshInstall();
    await h.pump(tester, page: 2);
    expect(find.text('Pick a look'), findsOneWidget);

    await tester.state<NavigatorState>(find.byType(Navigator)).maybePop();
    await tester.pumpAndSettle();

    expect(find.text('Stays on this device'), findsOneWidget);
    expect(h.intro.state, isFalse);
  });

  testWidgets('a source tile enables the plugin and pins it; again reverts both', (tester) async {
    final h = await _Harness.freshInstall();
    await h.pump(tester, page: 4);
    final plugin = pluginById(pluginIdBluesky)!;

    await _tap(tester, 'intro-source-$pluginIdBluesky');
    expect(plugin.isEnabled(h.prefs), isTrue);
    expect(feedStripPluginIds(h.prefs), contains(pluginIdBluesky));
    expect(h.strip.state, contains(pluginIdBluesky));

    await _tap(tester, 'intro-source-$pluginIdBluesky');
    expect(plugin.isEnabled(h.prefs), isFalse);
    expect(feedStripPluginIds(h.prefs), isNot(contains(pluginIdBluesky)));
  });

  testWidgets('the look picker writes the background and the accent', (tester) async {
    final h = await _Harness.freshInstall();
    await h.pump(tester, page: 2);

    await _tap(tester, 'intro-look-$xLookBackgroundDim');
    expect(h.prefs.get<String>(optionXLookBackground), xLookBackgroundDim);

    await _tap(tester, 'intro-accent-green');
    expect(h.prefs.get<String>(optionXLookAccent), 'green');
  });

  testWidgets('Start reading reloads Home once and marks the intro seen', (tester) async {
    final h = await _Harness.freshInstall();
    await h.pump(tester, page: 4);
    final before = h.home.loads;

    await _tap(tester, 'intro-start-reading');

    expect(h.home.loads, before + 1);
    expect(h.prefs.get<bool>(optionIntroSeen), isTrue);
  });

  testWidgets('the account card names who is signed in and moves on with Next', (tester) async {
    final h = await _Harness.freshInstall();
    await h.pump(tester, page: 3, accounts: _oneAccount);

    expect(find.text('Signed in as @reader'), findsOneWidget);
    expect(_key('intro-next'), findsOneWidget);
    expect(_key('intro-add-account'), findsNothing);
  });

  testWidgets('without an account the card offers sign-in, Not now and a backup import', (tester) async {
    final h = await _Harness.freshInstall();
    await h.pump(tester, page: 3);

    expect(_key('intro-add-account'), findsOneWidget);
    expect(_key('intro-not-now'), findsOneWidget);
    expect(_key('intro-import-backup'), findsOneWidget);

    await _tap(tester, 'intro-not-now');
    expect(find.text('Pick your sources'), findsOneWidget);
    expect(h.intro.state, isFalse);
  });
}
