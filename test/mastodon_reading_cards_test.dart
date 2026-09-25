import 'dart:ui' show SemanticsAction;

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_media_grid.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_poll.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_text.dart';
import 'package:xta/plugins/plugin_post_media.dart';

const _private = MastodonPost(
  id: 'sensitive',
  acct: 'reader@social.example',
  authorName: 'Reader',
  text: 'The ordinary text stays readable',
  url: 'https://social.example/@reader/sensitive',
  sensitive: true,
  images: ['https://social.example/private.jpg'],
  imageAlts: ['A private attachment description'],
);

Widget _app(Widget child, {bool calm = false, double scale = 1}) => PrefService(
  service: PrefServiceCache(defaults: {optionCalmMode: calm}),
  child: MaterialApp(
    locale: const Locale('en'),
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
      child: child!,
    ),
    home: Scaffold(body: SingleChildScrollView(child: child)),
  ),
);

Future<void> _pump(WidgetTester tester, Widget child, {bool calm = false, double scale = 1}) async {
  await tester.pumpWidget(_app(child, calm: calm, scale: scale));
  await tester.pump();
}

void main() {
  group('Mastodon body link boundaries', () {
    test('URLs retain fragments and balanced parentheses; entities drop punctuation', () {
      const text = 'Try (https://example.org/a_(b)?q=1#part), then #Café, #東京! @Reader@social.example.';
      final parts = mastodonTextParts(text);
      expect(parts.map((part) => part.text).join(), text);
      expect(
        parts.where((part) => part.kind == MastodonTextKind.link).single.value,
        'https://example.org/a_(b)?q=1#part',
      );
      expect(parts.where((part) => part.kind == MastodonTextKind.tag).map((part) => part.value), ['Café', '東京']);
      expect(parts.where((part) => part.kind == MastodonTextKind.mention).single.value, 'reader@social.example');
    });

    test('email addresses and embedded fragments do not turn into mentions or tags', () {
      const text = 'mail@example.org embedded#fragment @Reader #tag';
      final parts = mastodonTextParts(text, mentionAccts: ['reader@social.example']);
      expect(parts.map((part) => part.text).join(), text);
      expect(parts.where((part) => part.kind == MastodonTextKind.mention).single.value, 'reader@social.example');
      expect(parts.where((part) => part.kind == MastodonTextKind.tag).single.value, 'tag');
    });

    test('empty hosts, embedded schemes and URL credentials are not links', () {
      const text = 'https:///broken wordhttps://example.org https://reader:secret@example.org';
      final parts = mastodonTextParts(text);
      expect(parts.map((part) => part.text).join(), text);
      expect(parts.where((part) => part.kind == MastodonTextKind.link), isEmpty);
    });
  });

  testWidgets('links, mentions and hashtags dispatch to their own destinations', (tester) async {
    final opened = <String>[];
    await _pump(
      tester,
      MastodonRichText(
        text: 'https://example.org/path#fragment @Reader #Café,',
        mentionAccts: const ['reader@social.example'],
        onLinkTap: (value) => opened.add('link:$value'),
        onMentionTap: (value) => opened.add('mention:$value'),
        onTagTap: (value) => opened.add('tag:$value'),
      ),
    );
    await tester.tapOnText(find.textRange.ofSubstring('https://example.org/path#fragment'));
    await tester.tapOnText(find.textRange.ofSubstring('@Reader'));
    await tester.tapOnText(find.textRange.ofSubstring('#Café'));
    expect(opened, ['link:https://example.org/path#fragment', 'mention:reader@social.example', 'tag:Café']);
  });

  testWidgets('sensitive attachments are not built until revealed and can be hidden again', (tester) async {
    await _pump(tester, const MastodonPostCard(post: _private));
    expect(find.text(_private.text), findsOneWidget);
    expect(find.byType(PluginPostMedia), findsNothing);
    expect(find.byType(ExtendedImage), findsNothing);
    await tester.tap(find.text('Show'));
    await tester.pump();
    expect(find.byType(PluginPostMedia), findsOneWidget);
    expect(find.text('Content warning'), findsOneWidget);
    await tester.tap(find.text('Hide'));
    await tester.pump();
    expect(find.byType(PluginPostMedia), findsNothing);
    expect(find.byType(ExtendedImage), findsNothing);
  });

  testWidgets('a recycled card does not inherit another post reveal choice', (tester) async {
    const first = MastodonPost(
      id: '1',
      acct: 'a@one.example',
      authorName: 'A',
      text: 'First secret',
      url: 'https://one.example/1',
      spoilerText: 'First warning',
    );
    const second = MastodonPost(
      id: '2',
      acct: 'a@one.example',
      authorName: 'A',
      text: 'Second secret',
      url: 'https://one.example/2',
      spoilerText: 'Second warning',
    );
    await _pump(tester, const MastodonPostCard(post: first));
    await tester.tap(find.text('Show'));
    await tester.pump();
    expect(find.text('First secret'), findsOneWidget);
    await _pump(tester, const MastodonPostCard(post: second));
    expect(find.text('Second secret'), findsNothing);
    expect(find.text('Second warning'), findsOneWidget);
    expect(find.text('Show'), findsOneWidget);
  });

  testWidgets('quoted warning text remains visible without exposing the quoted body', (tester) async {
    const post = MastodonPost(
      id: 'outer',
      acct: 'a@one.example',
      authorName: 'A',
      text: 'My comment',
      url: 'https://one.example/outer',
      quote: MastodonQuotedPost(
        id: 'inner',
        acct: 'b@two.example',
        authorName: 'B',
        text: 'Quoted secret',
        url: 'https://two.example/inner',
        spoilerText: 'Spoiler',
        images: ['https://two.example/private.jpg'],
      ),
    );
    await _pump(tester, const MastodonPostCard(post: post));
    expect(find.text('Spoiler'), findsOneWidget);
    expect(find.text('Quoted secret'), findsNothing);
    expect(find.byType(ExtendedImage), findsNothing);
  });

  testWidgets('hidden counts retain accessible action names', (tester) async {
    final semantics = tester.ensureSemantics();
    try {
      await _pump(tester, const MastodonPostCard(post: _private), calm: true);
      expect(find.bySemanticsLabel('Replies'), findsOneWidget);
      expect(find.bySemanticsLabel('Reposted by'), findsOneWidget);
      expect(find.bySemanticsLabel('Likes'), findsOneWidget);
      final replies = tester.getSemantics(find.bySemanticsLabel('Replies'));
      expect(replies.getSemanticsData().hasAction(SemanticsAction.tap), isTrue);
    } finally {
      semantics.dispose();
    }
  });

  group('Mastodon read-only poll results', () {
    test('multi-choice percentages use voters and malformed counts remain bounded', () {
      const choice = MastodonPollOption(title: 'Choice', votes: 6);
      expect(mastodonPollFraction(const MastodonPoll(options: [choice], votesCount: 10), choice), .6);
      expect(
        mastodonPollFraction(
          const MastodonPoll(options: [choice], votesCount: 10, votersCount: 8, multiple: true),
          choice,
        ),
        .75,
      );
      expect(
        mastodonPollFraction(const MastodonPoll(options: [choice], votesCount: 10, multiple: true), choice),
        isNull,
      );
      expect(mastodonPollFraction(const MastodonPoll(options: [choice], votesCount: 2), choice), 1);
      expect(mastodonPollFraction(const MastodonPoll(options: [choice], resultsAvailable: false), choice), isNull);
    });

    testWidgets('hidden poll results do not falsely claim zero votes', (tester) async {
      await _pump(
        tester,
        const MastodonPollResults(
          poll: MastodonPoll(options: [MastodonPollOption(title: 'Choice')], resultsAvailable: false),
        ),
      );
      expect(find.text('Choice'), findsOneWidget);
      expect(find.textContaining('Results are not available'), findsOneWidget);
      expect(find.textContaining('No votes'), findsNothing);
      expect(find.byType(LinearProgressIndicator), findsNothing);
    });

    testWidgets('poll status and totals remain readable with enlarged text on a narrow phone', (tester) async {
      tester.view.physicalSize = const Size(320, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await _pump(
        tester,
        const Padding(
          padding: EdgeInsets.all(16),
          child: MastodonPollResults(
            poll: MastodonPoll(
              options: [MastodonPollOption(title: 'An unusually long choice that wraps', votes: 6)],
              votesCount: 10,
              votersCount: 8,
              multiple: true,
              expired: true,
            ),
          ),
        ),
        scale: 2,
      );
      expect(find.text('75%'), findsOneWidget);
      expect(find.textContaining('10 votes'), findsOneWidget);
      expect(find.textContaining('Poll closed'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  testWidgets('the media grid does not expose alt text before a sensitive reveal', (tester) async {
    await _pump(tester, const SizedBox(width: 150, height: 150, child: MastodonMediaTile(post: _private)));
    expect(find.byKey(const ValueKey('mastodon-media-alt-sensitive')), findsNothing);
    expect(find.byType(ExtendedImage), findsNothing);
  });

  testWidgets('the media grid exposes descriptions separately from opening an image', (tester) async {
    const post = MastodonPost(
      id: 'public',
      acct: 'a@one.example',
      authorName: 'A',
      text: '',
      url: 'https://one.example/public',
      images: ['https://one.example/public.jpg'],
      imageAlts: ['A green hillside'],
    );
    await _pump(tester, const SizedBox(width: 150, height: 150, child: MastodonMediaTile(post: post)));
    await tester.tap(find.byKey(const ValueKey('mastodon-media-alt-public')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('A green hillside'), findsOneWidget);
    expect(find.byType(PluginImageViewer), findsNothing);
  });
}
