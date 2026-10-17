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
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_native_article.dart';
import 'package:xta/plugins/substack/substack_plugin.dart';
import 'package:xta/plugins/substack/substack_settings.dart';

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

List<SubstackBlock> _blocks(String html) => substackBlocksOfBody(SubstackArticleDocument.parse(html).body);

void main() {
  testWidgets('a post reads natively: header, text, embeds as links, web-only gaps and footnotes', (tester) async {
    final links = <String>[];
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_app(SubstackNativeArticle(post: _post(), blocks: _blocks(_body), onLink: links.add)));
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
  });

  testWidgets('a paid preview ends with the note and the way to the site', (tester) async {
    final links = <String>[];
    await tester.pumpWidget(
      _app(SubstackNativeArticle(post: _post(paid: true), blocks: _blocks('<p>The free part.</p>'), onLink: links.add)),
    );
    await tester.pumpAndSettle();

    expect(find.text(L10n.current.plugin_substack_preview_ends), findsOneWidget);
    await tester.tap(find.text(L10n.current.plugin_substack_continue_on_site));
    expect(links, ['https://fieldnotes.substack.com/p/native']);
  });

  testWidgets('the long post scrolls on a narrow screen at 200 % text without overflow', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _app(
        SubstackNativeArticle(post: _post(), blocks: _blocks(syntheticSubstackBody(sections: 6)), onLink: (_) {}),
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
    final prefs = PrefServiceCache(defaults: {optionPluginSubstackNativeReader: false});
    await tester.pumpWidget(PrefService(service: prefs, child: _app(const SubstackSettingsScreen())));
    await tester.pumpAndSettle();

    expect(prefs.get<bool>(optionPluginSubstackNativeReader), isFalse);
    await tester.tap(find.text(L10n.current.plugin_substack_native_reader));
    await tester.pumpAndSettle();
    expect(prefs.get<bool>(optionPluginSubstackNativeReader), isTrue);

    await tester.runAsync(() => SubstackPlugin().resetPreferences(prefs));
    expect(prefs.get<bool>(optionPluginSubstackNativeReader), isFalse);
  });

  testWidgets('a footnote marker opens its note in a sheet; an image opens the viewer', (tester) async {
    await tester.pumpWidget(
      _app(
        SubstackNativeArticle(
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
}
