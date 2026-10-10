import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_api.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_tag_picker.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_favorites_section.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_plugin.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

import 'support/pixiv_bookmark_fakes.dart';
import 'support/pixiv_reader_harness.dart';

FakePixivBookmarkApi _api() => FakePixivBookmarkApi(
  tagLists: {
    'public': [
      (name: 'Favs', count: 12),
      for (var i = 0; i < 10; i++) (name: 'Cat$i', count: i),
      (name: 'Dog', count: 1),
    ],
    'private': const [(name: 'Secret', count: 2)],
  },
);

/// A button that opens the picker on [current] and keeps what it returned.
class _PickerHost extends StatelessWidget {
  final PixivBookmarkFilter current;
  final ValueChanged<PixivBookmarkFilter?> onPicked;

  const _PickerHost({required this.current, required this.onPicked});

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(
      child: TextButton(
        onPressed: () async => onPicked(await showPixivBookmarkTagPicker(context, current)),
        child: const Text('pick'),
      ),
    ),
  );
}

Future<List<PixivBookmarkFilter?>> _openPicker(
  WidgetTester tester,
  FakePixivBookmarkApi api, {
  PixivBookmarkFilter current = (restrict: 'public', tag: null),
  Size size = const Size(390, 844),
}) async {
  final picked = <PixivBookmarkFilter?>[];
  await pumpPixiv(
    tester,
    _PickerHost(current: current, onPicked: picked.add),
    size: size,
    extraProviders: [api.provider],
  );
  await tester.tap(find.text('pick'));
  await settlePixiv(tester);
  return picked;
}

Finder _pick(String? tag) => find.byKey(ValueKey('pixiv-bookmark-tag-pick-${tag ?? ''}'));

/// A reader with a full first page of public tags and [later] on the next.
FakePixivBookmarkApi _paged({List<PixivBookmarkTag> later = const [(name: 'Zeta', count: 1)]}) => FakePixivBookmarkApi(
  tagPages: {
    'public': [
      [for (var i = 0; i < 30; i++) (name: 'Tag$i', count: i)],
      later,
    ],
  },
);

int _pagesAsked(FakePixivBookmarkApi api) => api.calls.where((call) => call == 'tags:public:1').length;

void main() {
  group('tag picker', () {
    testWidgets('lists All, Uncategorized and the reader\'s tags with counts, and returns the one picked', (
      tester,
    ) async {
      final api = _api();
      final picked = await _openPicker(tester, api);

      expect(find.text('All'), findsOneWidget);
      expect(find.text('Uncategorized'), findsOneWidget);
      expect(find.descendant(of: _pick('Favs'), matching: find.text('12')), findsOneWidget);
      expect(api.calls, ['tags:public']);
      expect(tester.widget<ListTile>(_pick(null)).selected, isTrue);

      await tester.tap(_pick('Favs'));
      await settlePixiv(tester);
      expect(picked, [(restrict: 'public', tag: 'Favs')]);
      await disposePixiv(tester);
    });

    testWidgets('Uncategorized filters by Pixiv\'s 未分類 tag', (tester) async {
      final picked = await _openPicker(tester, _api());
      await tester.tap(_pick(pixivUnclassifiedTag));
      await settlePixiv(tester);
      expect(picked, [(restrict: 'public', tag: '未分類')]);
      await disposePixiv(tester);
    });

    testWidgets('typing suggests at most eight matches, ignoring case, and takes any typed tag', (tester) async {
      final picked = await _openPicker(tester, _api(), size: const Size(390, 1600));
      await tester.enterText(find.byKey(const ValueKey('pixiv-bookmark-tag-search')), 'cAT');
      await settlePixiv(tester);

      final shown = [
        for (var i = 0; i < 10; i++)
          if (tester.any(_pick('Cat$i'))) i,
      ];
      expect(shown, [0, 1, 2, 3, 4, 5, 6, 7]);
      expect(find.text('All'), findsNothing);

      await tester.tap(find.byKey(const ValueKey('pixiv-bookmark-tag-use')));
      await settlePixiv(tester);
      expect(picked, [(restrict: 'public', tag: 'cAT')]);
      await disposePixiv(tester);
    });

    testWidgets('the Private tab lists private tags and picks privately', (tester) async {
      final api = _api();
      final picked = await _openPicker(tester, api);
      await tester.tap(find.widgetWithText(Tab, 'Private'));
      await settlePixiv(tester);
      expect(api.calls, ['tags:public', 'tags:private']);

      await tester.tap(_pick('Secret'));
      await settlePixiv(tester);
      expect(picked, [(restrict: 'private', tag: 'Secret')]);
      await disposePixiv(tester);
    });

    testWidgets('large text on a small screen still lays out', (tester) async {
      final picked = <PixivBookmarkFilter?>[];
      await pumpPixiv(
        tester,
        _PickerHost(current: (restrict: 'public', tag: null), onPicked: picked.add),
        size: const Size(320, 568),
        textScale: 2,
        extraProviders: [_api().provider],
      );
      await tester.tap(find.text('pick'));
      await settlePixiv(tester);
      expect(tester.takeException(), isNull);
      await tester.tap(_pick(null));
      await settlePixiv(tester);
      expect(picked, [(restrict: 'public', tag: null)]);
      await disposePixiv(tester);
    });

    testWidgets('landscape with large text and the keyboard up does not overflow', (tester) async {
      final api = _api();
      await pumpPixiv(
        tester,
        _PickerHost(current: (restrict: 'public', tag: null), onPicked: (_) {}),
        size: const Size(640, 360),
        textScale: 2,
        extraProviders: [api.provider],
      );
      await tester.tap(find.text('pick'));
      await settlePixiv(tester);
      await tester.showKeyboard(find.byKey(const ValueKey('pixiv-bookmark-tag-search')));
      tester.view.viewInsets = const FakeViewPadding(bottom: 200);
      addTearDown(tester.view.resetViewInsets);
      await tester.enterText(find.byKey(const ValueKey('pixiv-bookmark-tag-search')), 'cat');
      await settlePixiv(tester);
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });

    testWidgets('scrolling pages on through next_url', (tester) async {
      final api = _paged();
      await _openPicker(tester, api);
      expect(api.calls, ['tags:public']);

      await tester.drag(_pick('Tag2'), const Offset(0, -1500));
      await settlePixiv(tester);
      expect(api.calls, ['tags:public', 'tags:public:1']);
      await tester.drag(_pick('Tag25'), const Offset(0, -600));
      await settlePixiv(tester);
      expect(_pick('Zeta'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a first page that does not fill the sheet asks for the next by itself', (tester) async {
      final api = FakePixivBookmarkApi(
        tagPages: {
          'public': [
            [(name: 'Favs', count: 3)],
            [(name: 'Zeta', count: 1)],
          ],
        },
      );
      await _openPicker(tester, api);
      expect(api.calls, ['tags:public', 'tags:public:1']);
      expect(_pick('Zeta'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a search pages on until it finds what is typed', (tester) async {
      final api = _paged();
      await _openPicker(tester, api);
      await tester.enterText(find.byKey(const ValueKey('pixiv-bookmark-tag-search')), 'zeta');
      await settlePixiv(tester);
      expect(api.calls, contains('tags:public:1'));
      expect(_pick('Zeta'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a page that fails stops a search from asking until the next keystroke', (tester) async {
      final api = _paged()..failingTagPages.add(1);
      await _openPicker(tester, api);
      final search = find.byKey(const ValueKey('pixiv-bookmark-tag-search'));
      await tester.enterText(search, 'zeta');
      await settlePixiv(tester);
      final asked = _pagesAsked(api);
      expect(asked, greaterThan(0));

      await tester.pump(const Duration(seconds: 5));
      expect(_pagesAsked(api), asked);
      await tester.enterText(search, 'zet');
      await settlePixiv(tester);
      expect(_pagesAsked(api), greaterThan(asked));
      await disposePixiv(tester);
    });

    testWidgets('closing it while the tags load throws nothing', (tester) async {
      final api = _api()..tagsGate = Completer<void>();
      await pumpPixiv(
        tester,
        _PickerHost(current: (restrict: 'public', tag: null), onPicked: (_) {}),
        extraProviders: [api.provider],
      );
      await tester.tap(find.text('pick'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(api.calls, ['tags:public']);
      // Twice: the test binding drops the first tap on a barrier that has just come up.
      for (var i = 0; i < 2; i++) {
        await tester.tapAt(const Offset(200, 20));
        await tester.pump(const Duration(seconds: 1));
      }
      expect(find.byType(BottomSheet), findsNothing);

      api.tagsGate!.complete();
      await settlePixiv(tester);
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });

    testWidgets('opens on the visibility being shown, with its tag selected', (tester) async {
      final api = _api();
      await _openPicker(tester, api, current: (restrict: 'private', tag: 'Secret'));
      expect(api.calls, ['tags:private']);
      expect(tester.widget<ListTile>(_pick('Secret')).selected, isTrue);
      await disposePixiv(tester);
    });
  });

  group('Favorites', () {
    Future<List<PixivBookmarkFilter>> pumpFavorites(WidgetTester tester, {String? tag}) async {
      final filters = <PixivBookmarkFilter>[];
      final store = PixivIllustListStore(({nextUrl}) async => const PixivIllustPage(illusts: []));
      addTearDown(store.destroy);
      await pumpPixiv(
        tester,
        Scaffold(
          body: PixivFavoritesSection(restrict: 'public', tag: tag, onFilter: filters.add, store: store),
        ),
        extraProviders: [_api().provider],
      );
      return filters;
    }

    testWidgets('the tag chip shows All and opens the picker', (tester) async {
      final filters = await pumpFavorites(tester);
      final chip = find.byKey(const ValueKey('pixiv-favorites-tag'));
      expect(find.descendant(of: chip, matching: find.text('All')), findsOneWidget);

      await tester.tap(chip);
      await settlePixiv(tester);
      await tester.tap(_pick('Favs'));
      await settlePixiv(tester);
      expect(filters, [(restrict: 'public', tag: 'Favs')]);
      await disposePixiv(tester);
    });

    testWidgets('a chosen tag shows on the chip, which clears back to All', (tester) async {
      final filters = await pumpFavorites(tester, tag: 'Favs');
      expect(
        find.descendant(of: find.byKey(const ValueKey('pixiv-favorites-tag')), matching: find.text('Favs')),
        findsOne,
      );
      expect(find.text('No bookmarks with this tag'), findsOneWidget);

      await tester.ensureVisible(find.byTooltip('Show all bookmarks'));
      await tester.tap(find.byTooltip('Show all bookmarks'));
      await tester.tap(find.text('Private'));
      await settlePixiv(tester);
      expect(filters, [(restrict: 'public', tag: null), (restrict: 'private', tag: null)]);
      await disposePixiv(tester);
    });
  });

  testWidgets('the Favorites list loads the visibility and tag picked', (tester) async {
    final prefs = PrefServiceCache(defaults: {optionPluginPixivRefreshToken: 'fixture-only'});
    final client = _FavoritesClient(prefs);
    final mute = PixivMuteStore(prefs);
    final feed = PixivFeedStore(client);
    addTearDown(mute.destroy);
    addTearDown(feed.destroy);
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<PixivClient>.value(value: client),
            Provider<PixivMuteStore>.value(value: mute),
            Provider<PixivFeedStore>.value(value: feed),
            _api().provider,
          ],
          child: MaterialApp(
            localizationsDelegates: const [L10n.delegate, GlobalMaterialLocalizations.delegate],
            supportedLocales: L10n.delegate.supportedLocales,
            home: PixivPlugin().homeScreen(scrollController: ScrollController()),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.descendant(of: find.byType(PluginHomeChrome), matching: find.byTooltip('Favorites')));
    await tester.pumpAndSettle();
    expect(client.favorites.last, 'public:-');

    await tester.tap(find.byKey(const ValueKey('pixiv-favorites-tag')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(Tab, 'Private'));
    await tester.pumpAndSettle();
    await tester.tap(_pick('Secret'));
    await tester.pumpAndSettle();
    expect(client.favorites.last, 'private:Secret');
    await tester.pumpWidget(const SizedBox());
  });
}

class _FavoritesClient extends PixivClient {
  _FavoritesClient(super.prefs);

  final favorites = <String>[];

  @override
  Future<void> ensureAccessToken() async {}

  @override
  Future<int> ensureUserId() async => 1;

  @override
  Future<PixivIllustPage> following({String? nextUrl}) async => const PixivIllustPage(illusts: []);

  @override
  Future<PixivIllustPage> bookmarks({
    required int userId,
    String restrict = 'public',
    String? tag,
    String? nextUrl,
  }) async {
    favorites.add('$restrict:${tag ?? '-'}');
    return const PixivIllustPage(illusts: []);
  }
}
