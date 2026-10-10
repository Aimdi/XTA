import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_api.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_store.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_image_source.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_card.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_content.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_export.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_position.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_plugin.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/reading/article_reading_store.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/crash_reporter.dart';

import 'support/memory_json_store.dart';
import 'support/pixiv_comments_fake.dart';
import 'support/pixiv_novel_fakes.dart';
import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_social_fakes.dart';

const _id = 900;

PixivNovelContent _content({
  int id = _id,
  String text = 'First line',
  PixivNovelNeighbour? previous,
  PixivNovelNeighbour? next,
  Map<String, PixivNovelPicture> uploads = const {},
  Map<String, PixivNovelPicture> illusts = const {},
}) => PixivNovelContent(id: id, text: text, previous: previous, next: next, uploads: uploads, illusts: illusts);

FakePixivNovelApi _api({Map<int, PixivNovelContent>? contents, Map<int, PixivNovel> details = const {}}) =>
    FakePixivNovelApi(PixivClient(PrefServiceCache()), contents: contents ?? {_id: _content()}, details: details);

Future<PixivHarness> _open(
  WidgetTester tester,
  FakePixivNovelApi api, {
  PixivNovel? novel,
  int id = _id,
  List<SingleChildWidget> more = const [],
  Size size = const Size(390, 844),
  double textScale = 1,
}) => pumpPixiv(
  tester,
  PixivNovelReaderScreen(novelId: id, novel: novel),
  size: size,
  textScale: textScale,
  extraProviders: [...api.providers, ...more],
);

/// The lines of a long text, each one telling where it is.
String _lines(int count) => [for (var i = 0; i < count; i++) 'Line $i'].join('\n');

/// The style a line of the novel is drawn in: its span's, under the app's default.
TextStyle? _styleOf(WidgetTester tester, String text) =>
    (tester.widget<RichText>(find.text(text, findRichText: true)).text as TextSpan).children?.first.style;

/// The line of the text showing first at the top of the list, and how far above the top it starts.
(String, double) _topLine(WidgetTester tester) {
  final top = tester.getTopLeft(find.byType(CustomScrollView)).dy;
  final lines = [
    for (final element in find.byType(RichText).evaluate())
      if ((element.widget as RichText).text.toPlainText() case final text when RegExp(r'^Line \d+$').hasMatch(text))
        if ((element.renderObject! as RenderBox).localToGlobal(Offset.zero).dy - top case final at
            when at + (element.renderObject! as RenderBox).size.height > 0)
          (text, at),
  ]..sort((a, b) => a.$2.compareTo(b.$2));
  return lines.first;
}

/// The places the reader keeps, by novel.
Map<String, Object?> _places(PixivHarness harness) =>
    jsonDecode(harness.prefs.get<String>(optionPluginPixivNovelReading) ?? '{}') as Map<String, Object?>;

/// Scrolls well into the text and waits until the place is written.
Future<void> _read(WidgetTester tester) async {
  await tester.fling(find.byType(CustomScrollView), const Offset(0, -3000), 3000);
  await settlePixiv(tester);
  await tester.pump(const Duration(seconds: 1));
}

/// Leaves the reader for a page of its own, so nothing is left to write.
Future<BuildContext> _leave(WidgetTester tester) async {
  tester
      .state<NavigatorState>(find.byType(Navigator).first)
      .pushAndRemoveUntil(MaterialPageRoute<void>(builder: (_) => const SizedBox(key: ValueKey('left'))), (_) => false);
  await settlePixiv(tester);
  return tester.element(find.byKey(const ValueKey('left')));
}

/// Taps the words themselves, where a link's recognizer is.
Future<void> _tapText(WidgetTester tester, String text) async {
  await tester.tapOnText(find.textRange.ofSubstring(text));
  await settlePixiv(tester);
}

/// Records what the reader exports instead of opening the save dialog.
class _Exporter extends PixivNovelExporter {
  final saved = <(String, String)>[];

  @override
  Future<bool> save(String fileName, String text) async {
    saved.add((fileName, text));
    return true;
  }
}

/// Answers share_plus's channel and keeps what each share sent.
List<Map<Object?, Object?>> _captureShares() {
  const channel = MethodChannel('dev.fluttercommunity.plus/share');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final shares = <Map<Object?, Object?>>[];
  messenger.setMockMethodCallHandler(channel, (call) async {
    shares.add(call.arguments as Map<Object?, Object?>);
    return 'dev.fluttercommunity.plus/share/success';
  });
  addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
  return shares;
}

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pump();
  await tester.tap(finder);
  await settlePixiv(tester);
}

Future<void> _openMenu(WidgetTester tester) => _tap(tester, find.byKey(const ValueKey('pixiv-novel-menu')));

Future<void> _push(WidgetTester tester, Route<void> route) async {
  tester.state<NavigatorState>(find.byType(Navigator).first).push(route);
  await settlePixiv(tester);
}

/// Leaves the reader while the app is still up, as closing it does, then ends the test: the
/// reader saves its place on the way out, which a torn-down app could no longer take.
Future<void> _close(WidgetTester tester) async {
  tester
      .state<NavigatorState>(find.byType(Navigator).first)
      .pushAndRemoveUntil(MaterialPageRoute<void>(builder: (_) => const SizedBox()), (_) => false);
  await settlePixiv(tester);
  await disposePixiv(tester);
}

void main() {
  testWidgets('a novel from a list shows its header, then its text laid out from the markup', (tester) async {
    final api = _api(
      contents: {_id: _content(text: '[chapter:Opening]\n彼は[[rb:漢字 > かんじ]]を書いた\n\n[newpage]\nSecond page')},
    );
    await _open(
      tester,
      api,
      novel: pixivNovel(
        caption: 'A quiet story',
        series: const PixivSeriesRef(id: 77, title: 'Seasons'),
      ),
    );

    expect(find.text('Autumn Letters'), findsWidgets);
    expect(find.text('Series: Seasons'), findsOneWidget);
    expect(find.text('12,345 characters'), findsOneWidget, reason: 'the bar counts the characters exactly');
    expect(find.textContaining('A quiet story', findRichText: true), findsOneWidget);
    expect(find.text('Opening', findRichText: true), findsOneWidget);
    expect(find.text('漢字'), findsOneWidget);
    expect(find.text('かんじ'), findsOneWidget);
    expect(find.text('Page 2'), findsOneWidget);
    expect(find.text('Second page', findRichText: true), findsOneWidget);
    expect(api.calls, ['content:$_id'], reason: 'a novel from a list needs no detail');
    await _close(tester);
  });

  testWidgets('a novel opened by its id fetches its detail and joins the novel history', (tester) async {
    final history = PixivNovelHistoryStore(storage: MemoryJsonStore());
    addTearDown(history.destroy);
    final api = _api(details: {_id: pixivNovel(title: 'By id')});
    await _open(tester, api, more: [Provider<PixivNovelHistoryStore>.value(value: history)]);

    expect(api.calls, unorderedEquals(['detail:$_id', 'content:$_id']));
    expect(find.text('By id'), findsWidgets);
    expect([for (final entry in history.state) entry.id], [_id]);
    await _close(tester);
  });

  testWidgets('a card opens its novel in the reader, from what the list already has', (tester) async {
    final api = _api();
    await pumpPixiv(
      tester,
      Scaffold(
        body: ListView(children: [PixivNovelCard(novel: pixivNovel())]),
      ),
      extraProviders: api.providers,
    );

    await _tap(tester, find.byKey(const ValueKey('pixiv-novel-$_id')));
    expect(tester.widget<PixivNovelReaderScreen>(find.byType(PixivNovelReaderScreen)).novel?.id, _id);
    expect(find.text('First line', findRichText: true), findsOneWidget);
    expect(api.calls, ['content:$_id']);
    await _close(tester);
  });

  testWidgets('a paused history leaves the opened novel out', (tester) async {
    final history = PixivNovelHistoryStore(storage: MemoryJsonStore());
    addTearDown(history.destroy);
    final api = _api(details: {_id: pixivNovel()});
    final harness = await pumpPixiv(
      tester,
      const SizedBox(),
      extraProviders: [
        ...api.providers,
        Provider<PixivNovelHistoryStore>.value(value: history),
      ],
    );
    await harness.prefs.set(optionPluginPixivHistoryPaused, true);
    await _push(tester, pixivNovelReaderRoute(_id));

    expect(find.text('First line', findRichText: true), findsOneWidget);
    expect(history.state, isEmpty);
    await _close(tester);
  });

  testWidgets('a novel that will not load says why and loads again on retry', (tester) async {
    final api = _api()..contentError = PixivException(PixivErrorKind.network, 'offline');
    await _open(tester, api, novel: pixivNovel());

    expect(find.byType(FullPageErrorWidget), findsOneWidget);
    api.contentError = null;
    await _tap(tester, find.text('Retry'));
    expect(find.byType(FullPageErrorWidget), findsNothing);
    expect(find.text('First line', findRichText: true), findsOneWidget);
    await _close(tester);
  });

  testWidgets('the text is selectable, so Copy and translators reach it', (tester) async {
    await _open(tester, _api(), novel: pixivNovel());

    expect(
      find.ancestor(of: find.text('First line', findRichText: true), matching: find.byType(SelectionArea)),
      findsOneWidget,
    );
    await _close(tester);
  });

  testWidgets('the shared appearance sheet changes the text size', (tester) async {
    final harness = await _open(tester, _api(), novel: pixivNovel());
    expect(_styleOf(tester, 'First line')?.fontSize, 18);

    await _tap(tester, find.byTooltip('Reading appearance'));
    final slider = find.byType(Slider).first;
    await tester.drag(slider, const Offset(400, 0));
    await settlePixiv(tester);
    await tester.tapAt(const Offset(20, 20));
    await settlePixiv(tester);

    expect(_styleOf(tester, 'First line')?.fontSize, 28);
    expect(harness.prefs.get<String>(articleAppearancePreference), contains('"fontSize":28'));
    await _close(tester);
  });

  testWidgets('chapters beside the novel: off when not viewable, replacing the reader when opened', (tester) async {
    final api = _api(
      contents: {
        _id: _content(
          previous: const PixivNovelNeighbour(id: 899, title: 'Before'),
          next: const PixivNovelNeighbour(id: 901, viewable: true, order: 3),
        ),
        901: _content(id: 901, text: 'Third chapter'),
      },
      details: {901: pixivNovel(id: 901, title: 'Part three')},
    );
    await _open(tester, api, novel: pixivNovel());

    final previous = find.byKey(const ValueKey('pixiv-novel-chapter-899'));
    await tester.ensureVisible(previous);
    expect(tester.widget<OutlinedButton>(previous).onPressed, isNull);
    expect(find.text('Before'), findsOneWidget);
    expect(find.text('#3'), findsOneWidget, reason: 'a chapter without a title goes by its number');

    await _tap(tester, find.byKey(const ValueKey('pixiv-novel-chapter-901')));
    expect(find.text('Third chapter', findRichText: true), findsOneWidget);
    expect(find.byType(PixivNovelReaderScreen), findsOneWidget, reason: 'the next chapter replaces this one');
    expect(api.calls, containsAll(['detail:901', 'content:901']));
    await _close(tester);
  });

  testWidgets('the menu offers the chapters too, with the unviewable one off', (tester) async {
    final api = _api(
      contents: {
        _id: _content(
          previous: const PixivNovelNeighbour(id: 899, title: 'Before'),
          next: const PixivNovelNeighbour(id: 901, viewable: true, title: 'After'),
        ),
      },
    );
    await _open(tester, api, novel: pixivNovel());
    await _openMenu(tester);

    expect(tester.widget<ListTile>(find.byKey(const ValueKey('pixiv-novel-menu-previous'))).enabled, isFalse);
    expect(tester.widget<ListTile>(find.byKey(const ValueKey('pixiv-novel-menu-next'))).enabled, isTrue);
    expect(find.text('Export as text'), findsOneWidget);
    expect(find.text('Open on Pixiv'), findsOneWidget);
    await _close(tester);
  });

  testWidgets('View comments opens the novel\'s comments, above and below the text', (tester) async {
    final comments = FakePixivCommentsApi({
      FakePixivCommentsApi.commentsKey(const PixivCommentTarget.novel(_id)): [
        PixivCommentPage([pixivTestComment(1, text: 'Lovely ending')]),
      ],
    });
    await _open(
      tester,
      _api(),
      novel: pixivNovel(comments: 4),
      more: [Provider<PixivCommentsApi>.value(value: comments)],
    );

    final links = find.byKey(const ValueKey('pixiv-comments-link'));
    expect(links, findsNWidgets(2));
    expect(find.text('View comments (4)'), findsNWidgets(2));
    await _tap(tester, links.last);

    final screen = tester.widget<PixivCommentsScreen>(find.byType(PixivCommentsScreen));
    expect(screen.target, const PixivCommentTarget.novel(_id));
    expect(find.textContaining('Lovely ending'), findsOneWidget);
    await _close(tester);
  });

  testWidgets('a page jump scrolls to the start of its page', (tester) async {
    final api = _api(contents: {_id: _content(text: '[jump:2]\n${_lines(80)}\n[newpage]\n${_lines(80)}')});
    await _open(tester, api, novel: pixivNovel());

    await _tapText(tester, 'Go to page 2');
    final divider = tester.getTopLeft(find.byKey(const ValueKey('pixiv-novel-page-2')));
    final list = tester.getTopLeft(find.byType(CustomScrollView));
    expect(divider.dy - list.dy, closeTo(0, 1));
    await _close(tester);
  });

  testWidgets('an outside link asks first; a Pixiv link opens in XTA', (tester) async {
    final text = '[[jumpuri:a site > https://example.com/a]]\n[[jumpuri:a work > https://www.pixiv.net/artworks/120]]';
    await _open(
      tester,
      _api(contents: {_id: _content(text: text)}),
      novel: pixivNovel(),
    );

    await _tapText(tester, 'a site');
    expect(find.text('Leave Pixiv to open https://example.com/a?'), findsOneWidget);
    await _tap(tester, find.text('Cancel'));
    expect(find.byType(AlertDialog), findsNothing);

    await _tapText(tester, 'a work');
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.byType(PixivIllustScreen), findsOneWidget);
    await _close(tester);
  });

  testWidgets('a link to another novel opens it in the reader', (tester) async {
    final api = _api(
      contents: {
        _id: _content(text: '[[jumpuri:the sequel > https://www.pixiv.net/novel/show.php?id=901]]'),
        901: _content(id: 901, text: 'The sequel begins'),
      },
      details: {901: pixivNovel(id: 901, title: 'Sequel')},
    );
    await _open(tester, api, novel: pixivNovel());

    await _tapText(tester, 'the sequel');
    expect(find.byType(AlertDialog), findsNothing);
    expect(tester.widget<PixivNovelReaderScreen>(find.byType(PixivNovelReaderScreen)).novelId, 901);
    expect(find.text('The sequel begins', findRichText: true), findsOneWidget);
    await _close(tester);
  });

  testWidgets('pictures: a work missing from the page is fetched, a tap opens it, a long press saves', (tester) async {
    final api = _api(
      contents: {
        _id: _content(
          text: '[uploadedimage:9]\n[pixivimage:120-2]',
          uploads: {'9': const PixivNovelPicture(url: 'https://i.pximg.net/novel/9.jpg')},
        ),
      },
    );
    await _open(tester, api, novel: pixivNovel());

    final images = tester.widgetList<PixivNetworkImage>(find.byType(PixivNetworkImage)).map((image) => image.url);
    expect(images, contains('https://i.pximg.net/novel/9.jpg'));
    expect(images, contains(pixivWork(id: 120).viewerUrls[1]), reason: 'page 2 of the fetched work');

    // Fixture pictures never load; the retry in their middle has a tooltip of its own, so press beside it.
    final pictures = find.bySemanticsLabel('Picture in the novel');
    await tester.longPressAt(tester.getTopLeft(pictures.first) + const Offset(12, 12));
    await settlePixiv(tester);
    expect(find.text('Download failed'), findsOneWidget, reason: 'a test has no download folder');

    ScaffoldMessenger.of(tester.element(find.byType(PixivNovelReaderScreen))).removeCurrentSnackBar();
    await tester.ensureVisible(pictures.last);
    await tester.pumpAndSettle();
    await tester.tapAt(tester.getTopLeft(pictures.last) + const Offset(12, 12));
    await settlePixiv(tester);
    expect(find.byType(PixivIllustScreen), findsOneWidget);
    await _close(tester);
  });

  testWidgets('pictures open and save from the picked image server; a fetched work saves as its page', (tester) async {
    final api = _api(
      contents: {
        _id: _content(
          text: '[uploadedimage:9]\n[pixivimage:120-2]',
          uploads: {
            '9': const PixivNovelPicture(
              url: 'https://i.pximg.net/novel/9_1200.jpg',
              originalUrl: 'https://i.pximg.net/novel/9.jpg',
            ),
          },
        ),
      },
    );
    final harness = await pumpPixiv(tester, const SizedBox(), extraProviders: api.providers);
    await harness.prefs.set(optionPluginPixivImageHost, pixivMirrorHost);
    await _push(tester, pixivNovelReaderRoute(_id, novel: pixivNovel()));

    final pictures = find.bySemanticsLabel('Picture in the novel');
    await tester.ensureVisible(pictures.last);
    await tester.pumpAndSettle();
    await tester.longPressAt(tester.getTopLeft(pictures.last) + const Offset(12, 12));
    await settlePixiv(tester);
    expect(harness.downloader.pages, [1], reason: 'page 2 of the work, saved the way its own screen saves it');

    await tester.ensureVisible(pictures.first);
    await tester.pumpAndSettle();
    await tester.tapAt(tester.getTopLeft(pictures.first) + const Offset(12, 12));
    await settlePixiv(tester);
    final item = tester.widget<PluginImageViewer>(find.byType(PluginImageViewer)).items.single;
    expect(
      [item.url, item.resolvedDownloadUrl],
      ['https://i.pixiv.re/novel/9_1200.jpg', 'https://i.pixiv.re/novel/9.jpg'],
    );
    await _close(tester);
  });

  testWidgets('export saves <title>.txt, as plain text or with the markup', (tester) async {
    final exporter = _Exporter();
    final api = _api(contents: {_id: _content(text: '[[rb:漢字>かんじ]]\n[newpage]\nEnd')});
    await _open(
      tester,
      api,
      novel: pixivNovel(title: 'Letters/Two: "Final"?'),
      more: [Provider<PixivNovelExporter>.value(value: exporter)],
    );

    await _openMenu(tester);
    await _tap(tester, find.byKey(const ValueKey('pixiv-novel-menu-export')));
    await _tap(tester, find.byKey(const ValueKey('pixiv-novel-export-plain')));
    await _openMenu(tester);
    await _tap(tester, find.byKey(const ValueKey('pixiv-novel-menu-export')));
    await _tap(tester, find.byKey(const ValueKey('pixiv-novel-export-markup')));

    expect(exporter.saved, [
      ('Letters_Two_ _Final__.txt', '漢字(かんじ)\n\nEnd'),
      ('Letters_Two_ _Final__.txt', '[[rb:漢字>かんじ]]\n[newpage]\nEnd'),
    ]);
    expect(find.text('Saved Letters_Two_ _Final__.txt'), findsOneWidget);
    await _close(tester);
  });

  test('an export name loses what no file system accepts and falls back to the id', () {
    expect(pixivNovelExportFileName('秋の手紙', 1), '秋の手紙.txt');
    expect(pixivNovelExportFileName('a\\b|c*d', 1), 'a_b_c_d.txt');
    expect(pixivNovelExportFileName('  ..  ', 7), 'pixiv-novel-7.txt');
    expect(pixivNovelExportFileName('', 7), 'pixiv-novel-7.txt');
  });

  testWidgets('sharing from the menu sends the link with the menu button as the sheet\'s origin', (tester) async {
    final shares = _captureShares();
    await _open(
      tester,
      _api(),
      novel: pixivNovel(series: const PixivSeriesRef(id: 77, title: 'Seasons')),
    );

    await _openMenu(tester);
    await _tap(tester, find.byKey(const ValueKey('pixiv-novel-menu-share')));
    await _openMenu(tester);
    await _tap(tester, find.byKey(const ValueKey('pixiv-novel-menu-shareSeries')));
    await _openMenu(tester);
    await _tap(tester, find.byKey(const ValueKey('pixiv-novel-menu-shareAuthor')));

    expect(
      [for (final share in shares) share['text']],
      [
        'https://www.pixiv.net/novel/show.php?id=$_id',
        'https://www.pixiv.net/novel/series/77',
        'https://www.pixiv.net/users/42',
      ],
    );
    final button = tester.getRect(find.byKey(const ValueKey('pixiv-novel-menu')));
    expect((shares.first['originX'], shares.first['originWidth']), (button.left, button.width));
    await _close(tester);
  });

  testWidgets('the series page shares its link from its button', (tester) async {
    final shares = _captureShares();
    final api = FakePixivNovelApi(
      PixivClient(PrefServiceCache()),
      seriesPages: {
        null: PixivNovelSeriesPage(
          series: PixivNovelSeries(id: 77, title: 'Seasons', user: pixivNovel().user),
        ),
      },
    );
    await pumpPixiv(tester, const PixivNovelSeriesScreen(seriesId: 77), extraProviders: api.providers);

    await _tap(tester, find.byTooltip('Share link'));
    expect(shares.single['text'], 'https://www.pixiv.net/novel/series/77');
    expect(shares.single['originWidth'], greaterThan(0));
    await _close(tester);
  });

  testWidgets('the author row opens the profile on its Novels tab', (tester) async {
    final social = FakePixivSocialApi(profile: pixivProfileOf(id: 42));
    await _open(tester, _api(), novel: pixivNovel(), more: [social.provider]);

    await _openMenu(tester);
    await _tap(tester, find.byKey(const ValueKey('pixiv-novel-menu-author')));

    final profile = tester.widget<PixivUserScreen>(find.byType(PixivUserScreen));
    expect((profile.userId, profile.initialTab), (42, 'novels'));
    await _close(tester);
  });

  testWidgets('the place reached is kept by passage and comes back on reopening', (tester) async {
    final api = _api(contents: {_id: _content(text: _lines(300))});
    final harness = await _open(tester, api, novel: pixivNovel());

    await _read(tester);
    final saved = ArticleReadPoint.parse(_places(harness)[pixivNovelReadingId(_id)]);
    expect(saved.paragraph, greaterThan(20));
    expect(
      harness.prefs.get<String>(articleReadingPreference),
      isNull,
      reason: 'the RSS and Substack journal is not used',
    );

    final navigator = tester.state<NavigatorState>(find.byType(Navigator));
    navigator.pushReplacement(pixivNovelReaderRoute(_id, novel: pixivNovel()));
    await settlePixiv(tester);

    final line = find.text('Line ${saved.paragraph}', findRichText: true);
    expect(line, findsOneWidget);
    final list = tester.getTopLeft(find.byType(CustomScrollView)).dy;
    expect(tester.getTopLeft(line).dy - list, closeTo(saved.leading, 2));
    expect(find.textContaining('Resumed'), findsOneWidget);
    await _close(tester);
  });

  testWidgets('with reading positions off, the place is not kept', (tester) async {
    final harness = await pumpPixiv(
      tester,
      const SizedBox(),
      extraProviders: _api(contents: {_id: _content(text: _lines(300))}).providers,
    );
    await harness.prefs.set(optionFeedReadingPosition, false);
    await _push(tester, pixivNovelReaderRoute(_id, novel: pixivNovel()));

    await _read(tester);

    expect(_places(harness), isEmpty);
    await _close(tester);
  });

  testWidgets('a novel only opened, or one that would not load, leaves no place behind', (tester) async {
    final api = _api(contents: {_id: _content(text: _lines(300))});
    final harness = await pumpPixiv(tester, const SizedBox(), extraProviders: api.providers);
    await _push(tester, pixivNovelReaderRoute(_id, novel: pixivNovel()));
    await tester.pump(const Duration(seconds: 1));
    await _leave(tester);

    api.contentError = PixivException(PixivErrorKind.network, 'offline');
    await _push(tester, pixivNovelReaderRoute(_id, novel: pixivNovel()));
    expect(find.byType(FullPageErrorWidget), findsOneWidget);
    await _leave(tester);

    expect(harness.prefs.toMap().keys, isNot(contains(optionPluginPixivNovelReading)));
    expect(harness.prefs.toMap().keys, isNot(contains(articleReadingPreference)));
    await disposePixiv(tester);
  });

  testWidgets('with the history paused, a novel keeps no place and finds none', (tester) async {
    final api = _api(contents: {_id: _content(text: _lines(300))});
    final harness = await pumpPixiv(tester, const SizedBox(), extraProviders: api.providers);
    final saved = jsonEncode({
      pixivNovelReadingId(_id): const ArticleReadPoint(fraction: 0.5, paragraph: 150).toJson(),
    });
    await harness.prefs.set(optionPluginPixivNovelReading, saved);
    await harness.prefs.set(optionPluginPixivHistoryPaused, true);
    await _push(tester, pixivNovelReaderRoute(_id, novel: pixivNovel()));

    expect(find.text('Line 0', findRichText: true), findsOneWidget, reason: 'the old place is not put back');
    await _read(tester);
    await _leave(tester);

    expect(harness.prefs.get<String>(optionPluginPixivNovelReading), saved);
    await disposePixiv(tester);
  });

  testWidgets('places stay out of backups and go with the novel history when the plugin forgets its data', (
    tester,
  ) async {
    final history = PixivNovelHistoryStore(storage: MemoryJsonStore());
    final favorites = PixivFavoriteTagsStore(PrefServiceCache());
    final searches = PixivNovelSearchHistory(PrefServiceCache());
    addTearDown(history.destroy);
    addTearDown(favorites.destroy);
    addTearDown(searches.destroy);
    final api = _api(contents: {_id: _content(text: _lines(300))});
    final harness = await pumpPixiv(
      tester,
      const SizedBox(),
      extraProviders: [
        ...api.providers,
        Provider<PixivNovelHistoryStore>.value(value: history),
        Provider<PixivFavoriteTagsStore>.value(value: favorites),
        Provider<PixivNovelSearchHistory>.value(value: searches),
      ],
    );
    await _push(tester, pixivNovelReaderRoute(_id, novel: pixivNovel()));
    await _read(tester);

    expect(_places(harness).keys, [pixivNovelReadingId(_id)]);
    expect(history.state, hasLength(1));
    expect(prefsMapWithoutSecrets(harness.prefs.toMap()).keys, isNot(contains(optionPluginPixivNovelReading)));

    await PixivPlugin().forgetLoadedData(await _leave(tester));
    expect(_places(harness), isEmpty);
    expect(history.state, isEmpty);
    await disposePixiv(tester);
  });

  testWidgets('a text size dragged over several steps keeps the passage being read at the top', (tester) async {
    await _open(
      tester,
      _api(contents: {_id: _content(text: _lines(400))}),
      novel: pixivNovel(),
    );
    final position = tester.state<ScrollableState>(find.byType(Scrollable).last).position;
    position.jumpTo(2000);
    await settlePixiv(tester);
    position.jumpTo(position.pixels + _topLine(tester).$2 + 15);
    await settlePixiv(tester);
    final (line, at) = _topLine(tester);
    expect(at, closeTo(-15, 1), reason: 'the line read is cut through its middle');

    await _tap(tester, find.byTooltip('Reading appearance'));
    final slider = find.byType(Slider).first;
    final gesture = await tester.startGesture(tester.getRect(slider).centerLeft + const Offset(24, 0));
    for (var i = 0; i < 30; i++) {
      await gesture.moveBy(const Offset(8, 0));
      await tester.pump(const Duration(milliseconds: 16));
    }
    await gesture.up();
    await settlePixiv(tester);
    await tester.tapAt(const Offset(20, 20));
    await settlePixiv(tester);

    final (lineAfter, atAfter) = _topLine(tester);
    expect(lineAfter, line);
    expect(atAfter, closeTo(at, 2));
    expect(_styleOf(tester, line)?.fontSize, greaterThan(18));
    await _close(tester);
  });

  testWidgets('large text at 320 dp fits without overflow', (tester) async {
    final api = _api(
      contents: {
        _id: _content(
          text: '[chapter:A long chapter title that wraps]\n彼は[[rb:漢字 > かんじ]]を書いた\n${_lines(5)}',
          previous: const PixivNovelNeighbour(id: 899, viewable: true, title: 'A previous chapter with a long title'),
          next: const PixivNovelNeighbour(id: 901, viewable: true, title: 'A next chapter with a long title'),
        ),
      },
    );
    await _open(tester, api, novel: pixivNovel(), size: const Size(320, 640), textScale: 2);

    expect(tester.takeException(), isNull);
    await tester.scrollUntilVisible(find.text('漢字'), 300);
    expect(
      tester.getRect(find.text('漢字')).height,
      closeTo(18 * 2, 1),
      reason: 'ruby\'s base is as large as the text around it, scaled once',
    );
    expect(tester.getRect(find.text('かんじ')).height, closeTo(9 * 1.1 * 2, 1));
    await tester.scrollUntilVisible(find.byKey(const ValueKey('pixiv-novel-chapter-901')), 300);
    expect(tester.takeException(), isNull);
    await _close(tester);
  });

  test('the first block showing is the first whose bottom is below the top', () {
    expect(
      pixivFirstVisibleBlock([
        (index: 5, top: 40, height: 20),
        (index: 4, top: -10, height: 30),
        (index: 3, top: -50, height: 30),
      ]),
      (index: 4, leading: -10),
    );
    expect(pixivFirstVisibleBlock([(index: 1, top: -40, height: 30)]), isNull);
  });
}
