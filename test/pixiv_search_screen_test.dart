import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_tags.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_store.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_saucenao.dart';
import 'package:xta/plugins/pixiv/pixiv_saucenao_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';

import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_search_fakes.dart';

FakePixivSearchApi _api({bool premium = false}) => FakePixivSearchApi(
  premium: premium,
  works: [pixivWork(id: 1, type: 'illust', title: 'Found')],
  preview: [pixivWork(id: 7, type: 'illust', title: 'Preview')],
);

/// Seeds string prefs before the screens read them.
FakePixivClient Function(PrefServiceCache prefs) _seeded(Map<String, String> values) => (prefs) {
  for (final MapEntry(:key, :value) in values.entries) {
    prefs.set<String>(key, value);
  }
  return FakePixivClient(prefs);
};

void _captureClipboard(WidgetTester tester, void Function(String? text) onCopy) {
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
    if (call.method == 'Clipboard.setData') onCopy((call.arguments as Map)['text'] as String?);
    return null;
  });
  addTearDown(() => messenger.setMockMethodCallHandler(SystemChannels.platform, null));
}

Future<void> _applyFromSheet(WidgetTester tester, List<String> choices) async {
  await tester.tap(find.byKey(const ValueKey('pixiv-search-filters')));
  await settlePixiv(tester);
  for (final choice in choices) {
    await tester.ensureVisible(find.text(choice));
    await tester.tap(find.text(choice));
    await tester.pump();
  }
  final apply = find.byKey(const ValueKey('pixiv-search-filters-apply'));
  await tester.ensureVisible(apply);
  await tester.tap(apply);
  await settlePixiv(tester);
}

void main() {
  group('filter sheet', () {
    testWidgets('applies its choices and Remember keeps them', (tester) async {
      final api = _api();
      final harness = await pumpPixiv(
        tester,
        const PixivSearchScreen(initialQuery: 'miku'),
        extraProviders: [api.provider],
      );
      expect(api.queries.single['search_target'], 'partial_match_for_tags');

      await _applyFromSheet(tester, ['Tags (exact)', 'Remember these filters']);
      expect(api.queries.last['search_target'], 'exact_match_for_tags');
      final kept = jsonDecode(harness.prefs.get<String>(optionPluginPixivSearchFilters)!) as Map;
      expect(kept['target'], 'exact_match_for_tags');
      expect(find.text('Found'), findsWidgets);
      await disposePixiv(tester);
    });

    testWidgets('offers Oldest and the audience sorts only to Premium', (tester) async {
      await pumpPixiv(tester, const PixivSearchScreen(initialQuery: 'miku'), extraProviders: [_api().provider]);
      await tester.tap(find.byKey(const ValueKey('pixiv-search-filters')));
      await settlePixiv(tester);
      expect(find.widgetWithText(ChoiceChip, 'Oldest'), findsNothing);
      expect(find.widgetWithText(ChoiceChip, 'Popular with women'), findsNothing);
      expect(find.widgetWithText(ChoiceChip, 'Popular'), findsOneWidget);
      await disposePixiv(tester);

      await pumpPixiv(
        tester,
        const PixivSearchScreen(initialQuery: 'miku'),
        extraProviders: [_api(premium: true).provider],
      );
      await tester.tap(find.byKey(const ValueKey('pixiv-search-filters')));
      await settlePixiv(tester);
      expect(find.widgetWithText(ChoiceChip, 'Oldest'), findsOneWidget);
      expect(find.widgetWithText(ChoiceChip, 'Popular with men'), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-search-bookmarks')), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('Popular without Premium shows the free preview as the grid, with a note', (tester) async {
      final api = _api();
      await pumpPixiv(tester, const PixivSearchScreen(initialQuery: 'miku'), extraProviders: [api.provider]);
      expect(find.text('Most popular'), findsOneWidget, reason: 'date sorts keep the preview strip');

      await _applyFromSheet(tester, ['Popular']);
      expect(find.textContaining("Pixiv's free popular preview"), findsOneWidget);
      expect(find.text('Most popular'), findsNothing);
      expect(find.byKey(const ValueKey('pixiv-search-bookmarks')), findsNothing);
      expect(api.calls.last, 'preview:miku');
      await disposePixiv(tester);
    });

    testWidgets('the popularity menu narrows the sent word only', (tester) async {
      final api = _api();
      await pumpPixiv(tester, const PixivSearchScreen(initialQuery: 'miku'), extraProviders: [api.provider]);
      await tester.tap(find.byKey(const ValueKey('pixiv-search-popularity')));
      await settlePixiv(tester);
      await tester.tap(find.text('Bookmarked by 1,000+').last);
      await settlePixiv(tester);

      expect(api.queries.last['word'], 'miku 1000users入り');
      expect(tester.widget<TextField>(find.byKey(const ValueKey('pixiv-search-field'))).controller!.text, 'miku');
      expect(find.text('Bookmarked by 1,000+'), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  group('results', () {
    testWidgets('creators found show as preview cards with a follow button', (tester) async {
      final api = _api()
        ..creatorsFound = [
          PixivUserPreview(
            user: const PixivUser(id: 11, name: 'Rin', account: 'rin', comment: ''),
            illusts: [pixivWork(id: 5, type: 'illust')],
          ),
        ];
      await pumpPixiv(tester, const PixivSearchScreen(initialQuery: 'rin'), extraProviders: [api.provider]);
      await tester.tap(find.text('Users'));
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixiv-search-user-11')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-follow-11')), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('the filter bar keeps 48dp targets and scrolls instead of overflowing with large text', (tester) async {
      await pumpPixiv(
        tester,
        const PixivSearchScreen(initialQuery: 'miku'),
        size: const Size(320, 640),
        textScale: 2,
        extraProviders: [_api(premium: true).provider],
      );
      for (final key in ['pixiv-search-filters', 'pixiv-search-date', 'pixiv-search-popularity']) {
        expect(tester.getSize(find.byKey(ValueKey(key))).height, greaterThanOrEqualTo(48), reason: key);
      }
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });
  });

  group('the search field', () {
    testWidgets('a number offers to open it as an artwork or a user instead of opening it', (tester) async {
      await pumpPixiv(tester, const PixivSearchScreen(), extraProviders: [_api().provider]);
      await tester.enterText(find.byKey(const ValueKey('pixiv-search-field')), '12345');
      await tester.pump();

      expect(find.byKey(const ValueKey('pixiv-open-artwork-12345')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-open-user-12345')), findsOneWidget);
      expect(find.byType(PixivIllustScreen), findsNothing);

      await tester.tap(find.byKey(const ValueKey('pixiv-open-artwork-12345')));
      await settlePixiv(tester);
      expect(find.byType(PixivIllustScreen), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('paste opens a pasted Pixiv link at once', (tester) async {
      final messenger = tester.binding.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.getData') return {'text': 'https://www.pixiv.net/artworks/321'};
        return null;
      });
      addTearDown(() => messenger.setMockMethodCallHandler(SystemChannels.platform, null));
      await pumpPixiv(tester, const PixivSearchScreen(), extraProviders: [_api().provider]);

      await tester.tap(find.byTooltip('Paste'));
      await settlePixiv(tester);
      expect(find.byType(PixivIllustScreen), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a suggestion completes the last word; a long press copies it', (tester) async {
      String? copied;
      _captureClipboard(tester, (text) => copied = text);
      final api = _api()
        ..suggestions = {
          'ri': [const PixivTrendTag(name: '鏡音リン', translatedName: 'Kagamine Rin')],
        };
      await pumpPixiv(tester, const PixivSearchScreen(), extraProviders: [api.provider]);
      final field = find.byKey(const ValueKey('pixiv-search-field'));
      await tester.enterText(field, 'miku ri');
      await tester.pump(const Duration(milliseconds: 350));
      await settlePixiv(tester);

      await tester.longPress(find.text('鏡音リン'));
      await tester.pump();
      expect(copied, '鏡音リン');

      await tester.tap(find.text('鏡音リン'));
      await settlePixiv(tester);
      expect(tester.widget<TextField>(field).controller!.text, 'miku 鏡音リン ');
      expect(api.calls.where((call) => call.startsWith('illusts')), isEmpty);
      await disposePixiv(tester);
    });
  });

  group('landing', () {
    testWidgets('Clear recent searches asks first', (tester) async {
      final harness = await pumpPixiv(
        tester,
        const PixivSearchScreen(),
        client: _seeded({
          optionPluginPixivSearchHistory: jsonEncode(['cat', 'dog']),
        }),
        extraProviders: [_api().provider],
      );
      expect(find.widgetWithText(ActionChip, 'cat'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('pixiv-search-history-clear')));
      await settlePixiv(tester);
      await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
      await settlePixiv(tester);
      expect(find.widgetWithText(ActionChip, 'cat'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('pixiv-search-history-clear')));
      await settlePixiv(tester);
      await tester.tap(find.widgetWithText(FilledButton, 'Clear recent searches'));
      await settlePixiv(tester);
      expect(find.text('No recent searches'), findsOneWidget);
      expect(harness.prefs.get<String>(optionPluginPixivSearchHistory), '[]');
      await disposePixiv(tester);
    });

    testWidgets('more than twelve recent searches fold behind Show all', (tester) async {
      await pumpPixiv(
        tester,
        const PixivSearchScreen(),
        client: _seeded({
          optionPluginPixivSearchHistory: jsonEncode([for (var i = 0; i < 15; i++) 'q$i']),
        }),
        extraProviders: [_api().provider],
      );
      expect(find.widgetWithText(ActionChip, 'q11'), findsOneWidget);
      expect(find.widgetWithText(ActionChip, 'q12'), findsNothing);

      await tester.tap(find.text('Show all (15)'));
      await settlePixiv(tester);
      expect(find.widgetWithText(ActionChip, 'q14'), findsOneWidget);
      expect(find.text('Show fewer'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('trending fails and retries on its own; tiles show the translation', (tester) async {
      final api = _api()
        ..creators = [const PixivUser(id: 3, name: 'Rin', account: 'rin', comment: '')]
        ..trendingError = Exception('offline');
      await pumpPixiv(tester, const PixivSearchScreen(), extraProviders: [api.provider]);
      expect(find.text('Rin'), findsOneWidget);
      expect(find.text('Trending now'), findsOneWidget);
      expect(find.widgetWithText(TextButton, 'Retry'), findsOneWidget);

      api
        ..trendingError = null
        ..trending = [const PixivTrendTag(name: '風景', translatedName: 'landscape')];
      await tester.tap(find.widgetWithText(TextButton, 'Retry'));
      await settlePixiv(tester);
      expect(find.text('#風景'), findsOneWidget);
      expect(find.text('landscape'), findsOneWidget);

      await tester.tap(find.text('#風景'));
      await settlePixiv(tester);
      expect(api.calls, contains('illusts:風景'));
      await disposePixiv(tester);
    });
  });

  group('tags on a work', () {
    const tag = PixivTag(name: 'オリジナル', translatedName: 'original');
    Widget host() => const Scaffold(
      body: Center(child: PixivDetailTags(tags: [tag])),
    );

    testWidgets('a long press offers mute, favourite and copy', (tester) async {
      String? copied;
      _captureClipboard(tester, (text) => copied = text);
      final harness = await pumpPixiv(tester, host());
      final chip = find.byKey(const ValueKey('pixiv-tag-オリジナル'));

      await tester.longPress(chip);
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixiv-tag-action-mute')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-tag-action-copy')), findsOneWidget);
      await tester.tap(find.text('Add to favorite tags'));
      await settlePixiv(tester);
      expect(readPixivFavoriteTags(harness.prefs).single.translatedName, 'original');
      expect(find.text('Added to favorite tags'), findsOneWidget);

      await tester.longPress(chip);
      await settlePixiv(tester);
      expect(find.text('Remove from favorite tags'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('pixiv-tag-action-copy')));
      await settlePixiv(tester);
      expect(copied, 'オリジナル');

      await tester.longPress(chip);
      await settlePixiv(tester);
      await tester.tap(find.byKey(const ValueKey('pixiv-tag-action-mute')));
      await settlePixiv(tester);
      await tester.tap(find.widgetWithText(FilledButton, 'Mute tag "original"'));
      await settlePixiv(tester);
      final mute = Provider.of<PixivMuteStore>(tester.element(find.byType(PixivDetailTags)), listen: false);
      expect(mute.state.tags, {'オリジナル'});
      await disposePixiv(tester);
    });
  });

  group('favorite tags', () {
    testWidgets('each tag gets a search tab; edit mode removes after asking', (tester) async {
      final api = _api();
      final harness = await pumpPixiv(
        tester,
        const PixivFavoriteTagsScreen(),
        client: _seeded({
          optionPluginPixivFavoriteTags: jsonEncode(['風景', 'cat']),
        }),
        extraProviders: [api.provider],
      );
      expect(find.widgetWithText(Tab, '#風景'), findsOneWidget);
      expect(find.widgetWithText(Tab, '#cat'), findsOneWidget);
      expect(api.calls, contains('illusts:風景'));

      await tester.tap(find.byKey(const ValueKey('pixiv-favorite-tags-edit')));
      await settlePixiv(tester);
      await tester.tap(find.byTooltip('Remove from favorite tags').first);
      await settlePixiv(tester);
      await tester.tap(find.widgetWithText(FilledButton, 'Remove from favorite tags'));
      await settlePixiv(tester);
      expect(readPixivFavoriteTags(harness.prefs).map((tag) => tag.name), ['cat']);

      await tester.tap(find.byKey(const ValueKey('pixiv-favorite-tags-edit')));
      await settlePixiv(tester);
      expect(find.widgetWithText(Tab, '#風景'), findsNothing);
      await disposePixiv(tester);
    });

    testWidgets('with none saved, says how to add one', (tester) async {
      await pumpPixiv(tester, const PixivFavoriteTagsScreen(), extraProviders: [_api().provider]);
      expect(find.textContaining('Long-press a tag'), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  group('SauceNAO', () {
    testWidgets('searches a chosen image and lists its Pixiv matches', (tester) async {
      final sauce = PixivSauceNaoApi(
        MockClient(
          (_) async => http.Response(
            '<div class="result"><div class="resultsimilarityinfo">91.5%</div>'
            '<div class="resulttitle">Sky</div>'
            '<a href="https://www.pixiv.net/artworks/55">55</a>'
            '<a href="https://www.pixiv.net/users/9">Painter</a></div>',
            200,
          ),
        ),
      );
      await pumpPixiv(
        tester,
        Scaffold(
          body: PixivSauceNaoSheet(
            pickImage: () async => Uint8List.fromList([1, 2, 3]),
            prepare: (bytes) async => bytes,
          ),
        ),
        extraProviders: [Provider<PixivSauceNaoApi>.value(value: sauce)],
      );
      expect(find.textContaining('saucenao.com'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('pixiv-saucenao-pick')));
      await settlePixiv(tester);
      expect(find.text('Sky'), findsOneWidget);
      expect(find.text('91.5% similar · Painter'), findsOneWidget);

      await tester.tap(find.text('Sky'));
      await settlePixiv(tester);
      expect(find.byType(PixivIllustScreen), findsOneWidget);
      await disposePixiv(tester);
    });
  });
}
