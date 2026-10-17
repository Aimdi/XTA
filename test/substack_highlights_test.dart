import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_blocks.dart';
import 'package:xta/plugins/substack/substack_highlights.dart';
import 'package:xta/plugins/substack/substack_highlights_sheet.dart';
import 'package:xta/plugins/substack/substack_models.dart';

SubstackPost _post([String slug = 'native']) => SubstackPost(
  id: slug,
  title: 'A native read',
  slug: slug,
  authorName: 'Ana',
  canonicalUrl: 'https://fieldnotes.substack.com/p/$slug',
  publicationBaseUrl: 'https://fieldnotes.substack.com',
  publicationName: 'Field Notes',
);

void main() {
  test('a selection over two blocks is two quotes; a stray letter is none', () {
    expect(substackSelectionQuotes('  the first   part\nthe second\nx\n'), ['the first part', 'the second']);
  });

  test('highlighted text is split out of its runs, keeping their styles', () {
    const runs = [SubstackRun('Read '), SubstackRun('this link', href: 'https://e.x'), SubstackRun(' now.')];
    final out = substackHighlightRuns(runs, ['d this', 'now']);

    expect(
      [for (final run in out) (run.text, run.highlighted, run.href)],
      [
        ('Rea', false, null),
        ('d ', true, null),
        ('this', true, 'https://e.x'),
        (' link', false, 'https://e.x'),
        (' ', false, null),
        ('now', true, null),
        ('.', false, null),
      ],
    );
    expect(substackHighlightRuns(runs, ['absent']), same(runs));
  });

  test('highlights are kept by post, survive a restart, drop the oldest beyond the limit', () async {
    final prefs = PrefServiceCache();
    final store = SubstackHighlightsStore(prefs);
    addTearDown(store.destroy);
    await store.add(_post(), ['one', 'two']);
    await store.add(_post(), ['two', 'three']);
    expect(store.of(_post()), ['one', 'two', 'three']);
    await store.remove(_post(), 'two');

    final again = SubstackHighlightsStore(prefs);
    addTearDown(again.destroy);
    expect(again.of(_post()), ['one', 'three']);

    await again.add(_post('old'), List.generate(substackHighlightsLimit - 2, (i) => 'old $i'));
    await again.add(_post('new'), ['fresh', 'more', 'again']);
    expect(again.of(_post('new')), hasLength(3));
    expect(again.of(_post('old')), isEmpty, reason: 'over the limit, the oldest post goes');
  });

  test('a damaged preference reads as no highlights', () {
    final store = SubstackHighlightsStore(PrefServiceCache(defaults: {substackHighlightsPreference: '{not json'}));
    addTearDown(store.destroy);
    expect(store.state, isEmpty);
  });

  test('the Markdown names the post and quotes each highlight', () {
    expect(
      substackHighlightsMarkdown(_post(), ['First thought', '# not a heading']),
      '# A native read\n\nField Notes · Ana\n\nhttps://fieldnotes.substack.com/p/native\n\n> First thought\n\n> not a heading',
    );
  });

  testWidgets('the sheet lists, removes and shares highlights', (tester) async {
    final store = SubstackHighlightsStore(PrefServiceCache());
    addTearDown(store.destroy);
    await store.add(_post(), ['First thought', 'Second thought']);
    final shared = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () => showSubstackHighlights(context, store, _post(), share: (text) async => shared.add(text)),
              child: const Text('open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();

    expect(find.text('Second thought'), findsOneWidget);
    await tester.tap(find.byTooltip(L10n.current.plugin_substack_highlight_remove).first);
    await tester.pumpAndSettle();
    expect(find.text('First thought'), findsNothing);
    await tester.tap(find.text(L10n.current.plugin_substack_highlights_share));
    expect(shared.single, endsWith('> Second thought'));
  });

  test('two readers open on two posts keep each other\'s highlights', () async {
    final prefs = PrefServiceCache();
    final first = SubstackHighlightsStore(prefs);
    final second = SubstackHighlightsStore(prefs);
    addTearDown(first.destroy);
    addTearDown(second.destroy);

    await first.add(_post('a'), ['from a']);
    await second.add(_post('b'), ['from b']);
    await first.add(_post('a'), ['again a']);

    final read = SubstackHighlightsStore(prefs);
    addTearDown(read.destroy);
    expect(read.of(_post('b')), ['from b'], reason: 'the first reader wrote over these before the fix');
    expect(read.of(_post('a')), ['from a', 'again a']);
  });
}
