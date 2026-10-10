import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_api.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_editor_store.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_haptics.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

import 'support/pixiv_bookmark_fakes.dart';
import 'support/pixiv_reader_harness.dart';

const _workTags = [PixivTag(name: 'オリジナル'), PixivTag(name: '1000users入り')];

PixivBookmarkDetail _detail({
  bool bookmarked = false,
  String restrict = 'public',
  List<PixivBookmarkTagChoice>? tags,
}) => PixivBookmarkDetail(
  isBookmarked: bookmarked,
  restrict: restrict,
  tags: tags ?? const [(name: 'オリジナル', checked: false), (name: '1000users入り', checked: false)],
);

final _heart = find.byKey(const ValueKey('pixiv-bookmark-120'));

Future<PixivHarness> _pumpTile(
  WidgetTester tester,
  FakePixivBookmarkApi api, {
  Map<String, Object> prefs = const {},
  List<SingleChildWidget> extra = const [],
}) => pumpPixiv(
  tester,
  Scaffold(
    body: Align(
      alignment: Alignment.topLeft,
      child: SizedBox(
        width: 220,
        child: PixivIllustTile(illust: pixivWork(pages: 1, tags: _workTags)),
      ),
    ),
  ),
  client: (cache) {
    for (final entry in prefs.entries) {
      cache.set(entry.key, entry.value);
    }
    return FakePixivClient(cache);
  },
  extraProviders: [api.provider, ...extra],
);

Future<void> _openEditor(WidgetTester tester) async {
  await tester.longPress(_heart);
  await settlePixiv(tester);
}

PixivBookmarkStore _bookmarks(WidgetTester tester) =>
    Provider.of<PixivBookmarkStore>(tester.element(find.byType(PixivIllustTile)), listen: false);

void main() {
  group('draft', () {
    const draft = PixivBookmarkDraft(
      isBookmarked: false,
      restrict: 'public',
      tags: [(name: 'a', checked: false), (name: 'b', checked: true)],
    );

    test('a work not bookmarked yet takes the default visibility and pre-checks auto-tags', () {
      final opened = draft.fromDetail(_detail(), defaultRestrict: 'private', autoTags: const ['オリジナル']);
      expect((opened.loaded, opened.isBookmarked, opened.restrict), (true, false, 'private'));
      expect(opened.checkedTags, ['オリジナル']);
    });

    test('an existing bookmark keeps its own visibility and tags', () {
      final opened = draft.fromDetail(
        _detail(
          bookmarked: true,
          restrict: 'private',
          tags: const [(name: 'x', checked: true), (name: 'y', checked: false)],
        ),
        defaultRestrict: 'public',
        autoTags: const ['y'],
      );
      expect(opened.restrict, 'private');
      expect(opened.checkedTags, ['x']);
    });

    test('typed tags go on top checked; listed ones are checked where they are', () {
      final added = draft.copyWith(query: 'n').withAdded(' new  a new2 ');
      expect(added.tags, [
        (name: 'new', checked: true),
        (name: 'new2', checked: true),
        (name: 'a', checked: true),
        (name: 'b', checked: true),
      ]);
      expect(added.query, isEmpty);
    });

    test('select all and clear all', () {
      expect(draft.withAllChecked(true).allChecked, isTrue);
      expect(draft.withAllChecked(false).checkedTags, isEmpty);
      expect(draft.withTag('a', checked: true).checkedTags, ['a', 'b']);
    });

    test('suggestions come from the reader\'s own tags', () {
      final withKnown = draft.copyWith(known: const [(name: 'Cat', count: 3), (name: 'dog', count: 1)], query: 'CA');
      expect(withKnown.suggestions, [(name: 'Cat', count: 3)]);
      expect(
        mergePixivBookmarkTags([
          [(name: 'Cat', count: 3)],
          [(name: 'Cat', count: 1), (name: 'dog', count: 2)],
        ]).map((tag) => tag.name),
        ['Cat', 'dog'],
      );
    });
  });

  group('editor', () {
    testWidgets('a long press on a tile\'s heart opens the editor, and Save sends the checked tags and visibility', (
      tester,
    ) async {
      final api = FakePixivBookmarkApi(detailResult: _detail());
      await _pumpTile(tester, api);
      await _openEditor(tester);
      expect(find.text('Add bookmark'), findsOneWidget);
      expect(api.calls, ['detail:120']);

      await tester.tap(find.byKey(const ValueKey('pixiv-bookmark-editor-private')));
      await tester.tap(find.byKey(const ValueKey('pixiv-bookmark-tag-オリジナル')));
      await tester.enterText(find.byKey(const ValueKey('pixiv-bookmark-editor-field')), 'mine');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('pixiv-bookmark-editor-save')));
      await settlePixiv(tester);

      expect(api.writes, ['add:120:private:mine オリジナル']);
      expect(find.text('Add bookmark'), findsNothing);
      expect(_bookmarks(tester).state[120], isTrue);
      await disposePixiv(tester);
    });

    testWidgets('an existing bookmark opens with its visibility and tags, and can be removed', (tester) async {
      final api = FakePixivBookmarkApi(
        detailResult: _detail(bookmarked: true, restrict: 'private', tags: const [(name: 'Favs', checked: true)]),
      );
      await _pumpTile(tester, api);
      await _openEditor(tester);

      expect(find.text('Edit bookmark'), findsOneWidget);
      expect(tester.widget<SwitchListTile>(find.byKey(const ValueKey('pixiv-bookmark-editor-private'))).value, isTrue);
      expect(tester.widget<CheckboxListTile>(find.byKey(const ValueKey('pixiv-bookmark-tag-Favs'))).value, isTrue);

      await tester.tap(find.byKey(const ValueKey('pixiv-bookmark-editor-remove')));
      await settlePixiv(tester);
      expect(api.writes, ['delete:120']);
      expect(_bookmarks(tester).state[120], isFalse);
      await disposePixiv(tester);
    });

    testWidgets('typing suggests the reader\'s own tags and picking one adds it checked', (tester) async {
      final api = FakePixivBookmarkApi(
        detailResult: _detail(),
        tagLists: {
          'public': const [(name: 'Cat', count: 4), (name: 'Dog', count: 2)],
          'private': const [(name: 'cathedral', count: 1)],
        },
      );
      await _pumpTile(tester, api);
      await _openEditor(tester);

      await tester.enterText(find.byKey(const ValueKey('pixiv-bookmark-editor-field')), 'cat');
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixiv-bookmark-suggestion-Cat')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-bookmark-suggestion-cathedral')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-bookmark-suggestion-Dog')), findsNothing);

      await tester.tap(find.byKey(const ValueKey('pixiv-bookmark-suggestion-Cat')));
      await tester.pump();
      expect(tester.widget<CheckboxListTile>(find.byKey(const ValueKey('pixiv-bookmark-tag-Cat'))).value, isTrue);
      expect(find.byKey(const ValueKey('pixiv-bookmark-suggestion-Cat')), findsNothing);
      await disposePixiv(tester);
    });

    testWidgets('a failed load offers a retry that loads again', (tester) async {
      final api = FakePixivBookmarkApi(detailResult: _detail())..failDetail = pixivNetworkFailure();
      await _pumpTile(tester, api);
      await _openEditor(tester);
      expect(find.text('Retry'), findsOneWidget);

      await tester.tap(find.text('Retry'));
      await settlePixiv(tester);
      expect(find.text('Add bookmark'), findsOneWidget);
      expect(api.calls, ['detail:120', 'detail:120']);
      await disposePixiv(tester);
    });

    testWidgets('a failed save keeps the editor open with the reason', (tester) async {
      final api = FakePixivBookmarkApi(detailResult: _detail())..failWrite = pixivNetworkFailure();
      await _pumpTile(tester, api);
      await _openEditor(tester);

      await tester.tap(find.byKey(const ValueKey('pixiv-bookmark-editor-save')));
      await settlePixiv(tester);
      expect(find.text('Add bookmark'), findsOneWidget);
      expect(find.text('Could not reach Pixiv'), findsOneWidget);
      expect(_bookmarks(tester).state[120], isNull);
      await disposePixiv(tester);
    });

    testWidgets('large text on a small screen keeps every control reachable', (tester) async {
      final api = FakePixivBookmarkApi(detailResult: _detail());
      await pumpPixiv(
        tester,
        Scaffold(
          body: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: 220,
              child: PixivIllustTile(illust: pixivWork(pages: 1, tags: _workTags)),
            ),
          ),
        ),
        size: const Size(320, 568),
        textScale: 2,
        extraProviders: [api.provider],
      );
      await _openEditor(tester);
      expect(tester.takeException(), isNull);
      await tester.tap(find.byKey(const ValueKey('pixiv-bookmark-editor-save')));
      await settlePixiv(tester);
      expect(api.writes, ['add:120:public:']);
      await disposePixiv(tester);
    });

    testWidgets('Edit bookmark in the detail menu opens the editor', (tester) async {
      final api = FakePixivBookmarkApi(detailResult: _detail());
      await pumpPixiv(tester, PixivIllustScreen(illust: pixivWork()), extraProviders: [api.provider]);
      await tester.tap(find.byKey(const ValueKey('pixiv-illust-menu')));
      await settlePixiv(tester);
      await tester.tap(find.byKey(const ValueKey('pixiv-illust-menu-bookmark')));
      await settlePixiv(tester);
      expect(find.text('Add bookmark'), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  group('the heart', () {
    testWidgets('is a 48 dp labelled button with tap and long-press actions', (tester) async {
      final semantics = tester.ensureSemantics();
      await _pumpTile(tester, FakePixivBookmarkApi());
      expect(tester.getSize(_heart), const Size.square(48));
      expect(
        tester.getSemantics(_heart),
        isSemantics(label: 'Bookmark', isButton: true, hasTapAction: true, hasLongPressAction: true),
      );
      semantics.dispose();
      await disposePixiv(tester);
    });

    testWidgets('a tap bookmarks with the defaults and buzzes lightly; a long press buzzes firmer', (tester) async {
      var now = DateTime(2026);
      final played = <PixivHaptic>[];
      final haptics = PixivHaptics(
        clock: () => now = now.add(const Duration(seconds: 1)),
        play: (kind) async => played.add(kind),
      );
      final api = FakePixivBookmarkApi(detailResult: _detail());
      await _pumpTile(
        tester,
        api,
        prefs: {optionPluginPixivDefaultPrivateBookmark: true, optionPluginPixivHaptics: true},
        extra: [Provider<PixivHaptics>.value(value: haptics)],
      );

      await tester.tap(_heart);
      await settlePixiv(tester);
      expect(api.writes, ['add:120:private:']);
      expect(find.byIcon(Icons.favorite), findsWidgets);

      await _openEditor(tester);
      expect(played, [PixivHaptic.light, PixivHaptic.medium]);
      await disposePixiv(tester);
    });

    testWidgets('following the author after a bookmark says so', (tester) async {
      final api = FakePixivBookmarkApi();
      final harness = await _pumpTile(tester, api, prefs: {optionPluginPixivFollowAfterBookmark: true});

      await tester.tap(_heart);
      await settlePixiv(tester);
      expect(harness.client.calls, contains('follow:42:public'));
      expect(find.text('Followed Mika'), findsOneWidget);
      await disposePixiv(tester);
    });
  });
}
