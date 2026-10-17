import 'dart:async';
import 'dart:io';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/substack/substack_article_navigation.dart';
import 'package:xta/plugins/substack/substack_blocks.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_native_article.dart';
import 'package:xta/plugins/substack/substack_plugin.dart';
import 'package:xta/plugins/substack/substack_reader_style.dart';
import 'package:xta/plugins/substack/substack_settings.dart';
import 'package:xta/reading/article_block_reading.dart';
import 'package:xta/reading/article_reading_store.dart';

import 'fixtures/substack_synthetic.dart';

SubstackPost _post({bool paid = false}) => SubstackPost(
  id: '1',
  title: 'A native read',
  slug: 'native',
  subtitle: 'Why the web view goes',
  authorName: 'Ana',
  canonicalUrl: 'https://fieldnotes.substack.com/p/native',
  audience: paid ? 'only_paid' : 'everyone',
  publicationBaseUrl: 'https://fieldnotes.substack.com',
  publicationName: 'Field Notes',
);

const _body =
    '<h2>Section</h2><p>Read <a href="https://example.com/x">this link</a> and a note'
    '<a class="footnote-anchor" href="#footnote-1">1</a>.</p>'
    '<iframe src="https://www.youtube.com/embed/abc"></iframe>'
    '<div class="poll-embed" data-component-name="PollToDOM"></div>'
    '<div class="footnote"><a class="footnote-number" href="#a">1</a><div class="footnote-content"><p>The note.</p></div></div>';

Widget _app(Widget home, {double scale = 1, double width = 390}) => MaterialApp(
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale), size: Size(width, 800)),
    child: child!,
  ),
  home: Scaffold(body: home),
);

/// The article with a place keeper, as the reader screen hands it over.
Widget _article({
  required SubstackPost post,
  required List<SubstackBlock> blocks,
  required ValueChanged<String> onLink,
  ArticleBlockReading? place,
  Map<String, File> keptImages = const {},
  ({SubstackReaderFont font, SubstackColumnWidth column}) style = (
    font: SubstackReaderFont.serif,
    column: SubstackColumnWidth.medium,
  ),
}) => SubstackNativeArticle(
  style: style,
  post: post,
  blocks: blocks,
  onLink: onLink,
  keptImages: keptImages,
  place: place ?? ArticleBlockReading(_store(), blocks: () => blocks.length),
);

ArticleReadingStore _store([PrefServiceCache? prefs]) => ArticleReadingStore(
  prefs: prefs ?? PrefServiceCache(),
  articleId: 'substack:fieldnotes:native',
  onCompleted: () async {},
);

List<SubstackBlock> _blocks(String html) => substackBlocksOfBody(SubstackArticleDocument.parse(html).body);

void main() {
  testWidgets('a post reads natively: header, text, embeds as links, web-only gaps and footnotes', (tester) async {
    final links = <String>[];
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_app(_article(post: _post(), blocks: _blocks(_body), onLink: links.add)));
    await tester.pumpAndSettle();

    expect(find.byType(SelectionArea), findsOneWidget);
    expect(find.text('A native read'), findsOneWidget);
    expect(find.text('Field Notes · Ana'), findsOneWidget);
    expect(find.bySemanticsLabel('Section'), findsOneWidget);
    expect(tester.getSemantics(find.bySemanticsLabel('Section')).flagsCollection.isHeader, isTrue);
    expect(find.textContaining('The note.', findRichText: true), findsOneWidget);

    await tester.tap(find.textContaining('youtube.com'));
    await tester.tap(find.text(L10n.current.open_in_browser));
    final paragraph = find.textContaining('this link', findRichText: true);
    final text = tester.renderObject<RenderParagraph>(paragraph);
    final offset = text.getOffsetForCaret(const TextPosition(offset: 7), Rect.zero);
    await tester.tapAt(tester.getTopLeft(paragraph) + offset + const Offset(4, 8));

    expect(links, [
      'https://www.youtube.com/embed/abc',
      'https://fieldnotes.substack.com/p/native',
      'https://example.com/x',
    ]);
    semantics.dispose();
    await _finish(tester);
  });

  testWidgets('a paid preview ends with the note and the way to the site', (tester) async {
    final links = <String>[];
    await tester.pumpWidget(
      _app(_article(post: _post(paid: true), blocks: _blocks('<p>The free part.</p>'), onLink: links.add)),
    );
    await tester.pumpAndSettle();

    expect(find.text(L10n.current.plugin_substack_preview_ends), findsOneWidget);
    await tester.tap(find.text(L10n.current.plugin_substack_continue_on_site));
    expect(links, ['https://fieldnotes.substack.com/p/native']);
    await _finish(tester);
  });

  testWidgets('the long post scrolls on a narrow screen at 200 % text without overflow', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _app(
        _article(post: _post(), blocks: _blocks(syntheticSubstackBody(sections: 6)), onLink: (_) {}),
        scale: 2,
        width: 320,
      ),
    );
    // Network images keep their loading animation running, so pump in steps.
    await tester.pump(const Duration(milliseconds: 100));
    for (var i = 0; i < 6; i++) {
      await tester.drag(find.byType(CustomScrollView), const Offset(0, -600));
      await tester.pump(const Duration(milliseconds: 100));
    }

    expect(tester.takeException(), isNull);
    expect(find.byType(SubstackNativeArticle), findsOneWidget);
  });

  testWidgets('the setting is off by default, switches on, and a reset turns it off', (tester) async {
    final prefs = PrefServiceCache(
      defaults: {
        optionPluginSubstackNativeReader: false,
        optionPluginSubstackReaderFont: 'serif',
        optionPluginSubstackColumnWidth: 'medium',
      },
    );
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          Provider<SubstackClient>.value(value: SubstackClient()),
          Provider<SubstackPublicationsStore>.value(value: SubstackPublicationsStore(prefs)),
        ],
        child: PrefService(service: prefs, child: _app(const SubstackSettingsScreen())),
      ),
    );
    await tester.pumpAndSettle();

    expect(prefs.get<bool>(optionPluginSubstackNativeReader), isFalse);
    await tester.tap(find.text(L10n.current.plugin_substack_native_reader));
    await tester.pumpAndSettle();
    expect(prefs.get<bool>(optionPluginSubstackNativeReader), isTrue);

    expect(find.text(L10n.current.plugin_substack_reader_font), findsOneWidget);
    expect(find.text(L10n.current.plugin_substack_column_width), findsOneWidget);
    await prefs.set(optionPluginSubstackColumnWidth, 'wide');

    await tester.runAsync(() => SubstackPlugin().resetPreferences(prefs));
    expect(prefs.get<bool>(optionPluginSubstackNativeReader), isFalse);
    expect(prefs.get<String>(optionPluginSubstackColumnWidth), 'medium');
  });

  testWidgets('a footnote marker opens its note in a sheet; an image opens the viewer', (tester) async {
    await tester.pumpWidget(
      _app(
        _article(
          post: _post(),
          blocks: _blocks('$_body${syntheticImage(0, 'https://cdn.example/a.jpg')}'),
          onLink: (_) {},
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 100));

    final paragraph = find.textContaining('this link', findRichText: true);
    final text = tester.renderObject<RenderParagraph>(paragraph);
    final marker = text.text.toPlainText().indexOf('note1') + 4;
    final offset = text.getOffsetForCaret(TextPosition(offset: marker), Rect.zero);
    await tester.tapAt(tester.getTopLeft(paragraph) + offset + const Offset(2, 8));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byType(BottomSheet), findsOneWidget);
    expect(
      find.descendant(of: find.byType(BottomSheet), matching: find.textContaining('The note.', findRichText: true)),
      findsOneWidget,
    );
    tester.state<NavigatorState>(find.byType(Navigator)).pop();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byType(BottomSheet), findsNothing);

    await tester.ensureVisible(find.byType(ExtendedImage));
    await tester.pump(const Duration(milliseconds: 100));
    await tester.tap(find.byType(ExtendedImage));
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.byType(PluginImageViewer), findsOneWidget);
  });

  testWidgets('the place reached is kept by block and comes back on reopening', (tester) async {
    final prefs = PrefServiceCache();
    final blocks = _blocks([for (var i = 0; i < 300; i++) '<p>Line $i</p>'].join());
    final first = _store(prefs);
    final place = ArticleBlockReading(first, blocks: () => blocks.length);
    await tester.pumpWidget(_app(_article(post: _post(), blocks: blocks, onLink: (_) {}, place: place)));
    await _settle(tester, place.open(first.state.point));

    for (var i = 0; i < 4; i++) {
      await tester.drag(find.byType(CustomScrollView), const Offset(0, -500));
      await tester.pump(const Duration(milliseconds: 100));
    }
    await tester.pump(const Duration(milliseconds: 300));
    final saved = first.state.point;
    expect(saved.paragraph, greaterThan(20));
    place.close(then: first.destroy);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 1));

    final again = _store(prefs);
    addTearDown(again.destroy);
    expect(again.state.point.paragraph, saved.paragraph, reason: 'the place is in the journal');
    final reopened = ArticleBlockReading(again, blocks: () => blocks.length);
    addTearDown(reopened.close);
    await tester.pumpWidget(_app(_article(post: _post(), blocks: blocks, onLink: (_) {}, place: reopened)));
    await _settle(tester, reopened.open(again.state.point));

    final line = find.text('Line ${saved.paragraph}', findRichText: true);
    expect(line, findsOneWidget);
    final list = tester.getTopLeft(find.byType(CustomScrollView)).dy;
    expect(tester.getTopLeft(line).dy - list, closeTo(saved.leading, 2));
    await _finish(tester);
  });

  testWidgets('a jump from the contents puts its block at the top', (tester) async {
    final document = SubstackArticleDocument.parse(
      [for (var i = 0; i < 60; i++) '<h2>Part $i</h2><p>${'Words ' * 80}</p>'].join(),
    );
    final blocks = substackBlocksOfBody(document.body);
    final place = ArticleBlockReading(_store(), blocks: () => blocks.length);
    await tester.pumpWidget(_app(_article(post: _post(), blocks: blocks, onLink: (_) {}, place: place)));
    await _settle(tester, place.open(const ArticleReadPoint()));

    final heading = document.headings[40];
    final passages = [for (final passage in document.passages) passage.anchor];
    final index = substackBlockOfPassage(blocks, passages, heading.anchor)!;
    await _settle(tester, place.showBlock(index, duration: const Duration(milliseconds: 250)));

    final part = find.text('Part 40', findRichText: true);
    expect(part, findsOneWidget);
    expect(tester.getTopLeft(part).dy, closeTo(tester.getTopLeft(find.byType(CustomScrollView)).dy, 2));
    await _finish(tester);
  });

  testWidgets('a kept post shows its images from their files, the rest from the network', (tester) async {
    tester.view.physicalSize = const Size(390, 4000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final kept = File('${Directory.systemTemp.path}/xta-native-kept.png');
    final blocks = _blocks(
      '${syntheticImage(0, 'https://cdn.example/kept.jpg')}${syntheticImage(1, 'https://cdn.example/new.jpg')}',
    );
    await tester.pumpWidget(
      _app(_article(post: _post(), blocks: blocks, onLink: (_) {}, keptImages: {'https://cdn.example/kept.jpg': kept})),
    );
    await tester.pump(const Duration(milliseconds: 100));

    final images = tester.widgetList<ExtendedImage>(find.byType(ExtendedImage)).toList();
    expect(images, hasLength(2));
    ImageProvider source(ExtendedImage image) => (image.image as ExtendedResizeImage).imageProvider;
    expect((source(images[0]) as ExtendedFileImageProvider).file.path, kept.path);
    expect((source(images[1]) as ExtendedNetworkImageProvider).url, 'https://cdn.example/new.jpg');
    await _finish(tester);
  });

  testWidgets('on a wide screen the text keeps its column width and font', (tester) async {
    tester.view.physicalSize = const Size(1200, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    Future<double> widthWith(SubstackColumnWidth column) async {
      await tester.pumpWidget(
        _app(
          _article(
            post: _post(),
            blocks: _blocks('<p>${'Words ' * 200}</p>'),
            onLink: (_) {},
            style: (font: SubstackReaderFont.sans, column: column),
          ),
          width: 1200,
        ),
      );
      await tester.pump();
      return tester.getSize(find.textContaining('Words', findRichText: true)).width;
    }

    expect(await widthWith(SubstackColumnWidth.narrow), closeTo(36 * 16, 1));
    expect(await widthWith(SubstackColumnWidth.wide), closeTo(52 * 16, 1));
    final text = tester.widget<RichText>(find.textContaining('Words', findRichText: true));
    expect(text.text.style?.fontFamily, isNot('serif'));
    await _finish(tester);
  });
}

/// Takes the article down and lets the reading store's last write run.
Future<void> _finish(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox());
  await tester.pump(const Duration(seconds: 1));
}

/// Pumps frames until [done] completes: a restore waits on laid-out frames.
Future<void> _settle(WidgetTester tester, Future<void> done) async {
  var finished = false;
  unawaited(done.whenComplete(() => finished = true));
  for (var i = 0; i < 200 && !finished; i++) {
    await tester.pump(const Duration(milliseconds: 16));
  }
  expect(finished, isTrue);
}
