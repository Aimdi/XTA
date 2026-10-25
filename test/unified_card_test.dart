import 'dart:convert';
import 'dart:io';

import 'package:dart_twitter_api/twitter_api.dart' hide Size;
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_ExpandableTweetText.dart';
import 'package:xta/tweet/_card.dart';
import 'package:xta/tweet/_media.dart';
import 'package:xta/tweet/grok_share_card.dart';
import 'package:xta/tweet/unified_card.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/_entities.dart';
import 'package:xta/utils/rich_text.dart';
import 'package:xta/utils/urls.dart';

/// A card as X sends it: binding values still a list of `{key, value}`.
Map<String, dynamic> _rawCard(String name) =>
    jsonDecode(File('test/fixtures/UnifiedCard/$name.json').readAsStringSync()) as Map<String, dynamic>;

/// The same card as the client stores it, binding values keyed.
Map<String, dynamic> _keyedCard(String name) {
  final raw = _rawCard(name);
  return {
    ...raw,
    'binding_values': {for (final e in raw['binding_values'] as List) e['key'] as String: e['value']},
  };
}

/// [name]'s card with its unified card passed through [edit].
Map<String, dynamic> _edited(String name, void Function(Map<String, dynamic> unified) edit) {
  final card = _keyedCard(name);
  final unified = unifiedCardOf(card)!;
  edit(unified);
  return {
    ...card,
    'binding_values': {
      'unified_card': {'string_value': jsonEncode(unified)},
    },
  };
}

Map<String, dynamic> _details(Map<String, dynamic> unified) =>
    unified['component_objects']['details_1']['data'] as Map<String, dynamic>;

TweetWithCard _tweet(Map<String, dynamic> card) => TweetWithCard()
  ..idStr = '1'
  ..user = (User()..screenName = 'reader')
  ..entities = Entities.fromJson({'urls': []})
  ..card = card;

Future<void> _pump(WidgetTester tester, Widget child) async {
  tester.view.physicalSize = const Size(1200, 2400);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    PrefService(
      service: PrefServiceCache(cache: const {}),
      child: MaterialApp(
        theme: xLookLightTheme(null),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(body: SingleChildScrollView(child: child)),
      ),
    ),
  );
  await tester.pump();
}

Future<void> _pumpCard(WidgetTester tester, Map<String, dynamic> card) =>
    _pump(tester, TweetCard(tweet: _tweet(card), card: card));

/// [text] as a post reads it, its links described by [entities].
Future<String> _shownText(
  WidgetTester tester,
  String text,
  Entities entities, {
  bool hideGrokShareLinks = false,
}) async {
  late String shown;
  await _pump(
    tester,
    Builder(
      builder: (context) {
        final parts = buildRichText(context, text, entities, hideGrokShareLinks: hideGrokShareLinks);
        shown = TextSpan(children: displayRichText(parts)).toPlainText();
        disposeRichTextParts(parts);
        return const SizedBox();
      },
    ),
  );
  return shown;
}

/// A post's only link, [url] at [indices], leading to [expandedUrl].
Entities _link(String url, String expandedUrl, List<int> indices) => Entities.fromJson({
  'urls': [
    {'url': url, 'expanded_url': expandedUrl, 'display_url': expandedUrl.substring(8), 'indices': indices},
  ],
});

/// The question of a recorded Grok share (x.com/quax_tests/status/2109405630372991224), too long for three lines.
const _longQuestion =
    "Here I'm having a discussion with grok but I speak a lot. How are you doing grok ? I would like to chat with "
    "you to test my Android app ! But for this I need to fill my message so that I it's long enough to make my test "
    'case relevant.';

Map<String, dynamic> _question(Map<String, dynamic> unified) =>
    (_details(unified)['conversation_preview'] as List).first as Map<String, dynamic>;

void main() {
  group('unifiedCardOf', () {
    test('reads binding values both keyed and as X sends them', () {
      expect(unifiedCardOf(_rawCard('image_carousel_website'))?['type'], 'image_carousel_website');
      expect(unifiedCardOf(_keyedCard('image_carousel_website'))?['type'], 'image_carousel_website');
    });

    test('gives nothing for a missing or broken payload', () {
      expect(unifiedCardOf(null), isNull);
      expect(unifiedCardOf({'binding_values': {}}), isNull);
      expect(
        unifiedCardOf({
          'binding_values': {
            'unified_card': {'string_value': '{not json'},
          },
        }),
        isNull,
      );
    });
  });

  group('CarouselCardData', () {
    final unified = unifiedCardOf(_keyedCard('image_carousel_website'))!;

    test('reads the page, its title and every picture in order', () {
      final carousel = CarouselCardData.fromUnified(unified)!;
      expect(carousel.url, startsWith('https://www.uefa.com/news-media/news/'));
      expect(carousel.title, contains('Tap to read'));
      expect(carousel.subtitle, 'uefa.com');
      expect(carousel.media.map((m) => m.idStr), ['2082854064501489664', '2082853967843762176']);
    });

    test('gives nothing without a page, a title or a known picture', () {
      Map<String, dynamic> copy() => jsonDecode(jsonEncode(unified)) as Map<String, dynamic>;
      expect(CarouselCardData.fromUnified(copy()..remove('destination_objects')), isNull);
      expect(CarouselCardData.fromUnified(copy()..['media_entities'] = {}), isNull);
      final untitled = copy();
      _details(untitled).remove('title');
      expect(CarouselCardData.fromUnified(untitled), isNull);
    });
  });

  group('GrokShareCardData', () {
    final unified = unifiedCardOf(_keyedCard('grok_share'))!;

    test('reads the question, the answer without markup, and Grok', () {
      final share = GrokShareCardData.fromUnified(unified)!;
      expect(share.url, 'https://x.com/i/grok/share/96b0a07447744eb79d03cbcaaf76e19f');
      expect(share.question, 'Is this real?');
      expect(share.answer, startsWith('Yes, the clip is real footage'));
      expect(share.answer, isNot(contains('grok:render')));
      expect(share.grokScreenName, 'grok');
      expect(share.grokImageUrl, startsWith('https://pbs.twimg.com/profile_images/'));
    });

    test('falls back to @grok and no picture when the card names nobody', () {
      final copy = jsonDecode(jsonEncode(unified)) as Map<String, dynamic>;
      _details(copy).remove('grok_user');
      final share = GrokShareCardData.fromUnified(copy)!;
      expect([share.grokScreenName, share.grokImageUrl], ['grok', null]);
    });

    test('keeps the question when there is no answer yet', () {
      final copy = jsonDecode(jsonEncode(unified)) as Map<String, dynamic>;
      final details = _details(copy);
      details['conversation_preview'] = (details['conversation_preview'] as List).take(1).toList();
      final share = GrokShareCardData.fromUnified(copy)!;
      expect([share.question, share.answer], ['Is this real?', '']);
    });

    test('gives nothing without a conversation or a destination', () {
      Map<String, dynamic> copy() => jsonDecode(jsonEncode(unified)) as Map<String, dynamic>;
      final silent = copy();
      _details(silent).remove('conversation_preview');
      expect(GrokShareCardData.fromUnified(silent), isNull);
      expect(GrokShareCardData.fromUnified(copy()..remove('destination_objects')), isNull);
    });

    test('isGrokShareCard tells a Grok share from other cards', () {
      expect(isGrokShareCard(_keyedCard('grok_share')), isTrue);
      expect(isGrokShareCard(_rawCard('grok_share')), isTrue);
      expect(isGrokShareCard(_keyedCard('image_carousel_website')), isFalse);
      expect(isGrokShareCard(null), isFalse);
    });
  });

  group('Grok share links', () {
    test('are recognised on every X host', () {
      for (final host in ['x.com', 'www.x.com', 'twitter.com', 'mobile.twitter.com']) {
        expect(grokShareIdIn('https://$host/i/grok/share/abc123?s=20'), 'abc123', reason: host);
      }
    });

    test('are not confused with other links', () {
      for (final url in [
        null,
        '',
        'https://x.com/i/grok',
        'https://x.com/i/grok/share/',
        'https://x.com/grok/status/1',
        'https://grok.com/share/abc123',
        'https://x.com.evil.example/i/grok/share/abc123',
      ]) {
        expect(grokShareIdIn(url), isNull, reason: url);
      }
    });

    test('parse as a Grok share rather than an unknown link', () async {
      final parsed = await parseUri(Uri.parse('https://x.com/i/grok/share/abc123'));
      expect(parsed, isA<GrokShareUriInfo>());
      expect((parsed as GrokShareUriInfo).url, 'https://x.com/i/grok/share/abc123');
    });

    testWidgets('are dropped from the text only when the card shows them', (tester) async {
      final entities = Entities.fromJson({
        'urls': [
          {
            'url': 'https://t.co/g',
            'expanded_url': 'https://x.com/i/grok/share/abc123',
            'display_url': 'x.com/i/grok/share/abc…',
            'indices': [5, 19],
          },
        ],
      });
      late String hidden;
      late String shown;
      await _pump(
        tester,
        Builder(
          builder: (context) {
            String text(bool hide) => TextSpan(
              children: displayRichText(
                buildRichText(context, 'Look https://t.co/g', entities, hideGrokShareLinks: hide),
              ),
            ).toPlainText();
            hidden = text(true);
            shown = text(false);
            return const SizedBox();
          },
        ),
      );
      expect(hidden.trim(), 'Look');
      expect(shown, contains('x.com/i/grok/share'));
    });

    testWidgets('leave no line break behind when they end the text', (tester) async {
      // The text of x.com/quax_tests/status/2109403816038666716, recorded by QuaX.
      const text = 'I discussed with Grok today !\nhttps://t.co/v55JVdUSt7';
      final link = _link('https://t.co/v55JVdUSt7', 'https://x.com/i/grok/share/32db3dd9ab8941ecbad132eeadd54f37', [
        30,
        53,
      ]);

      expect(
        await _shownText(tester, text, link, hideGrokShareLinks: true),
        'I discussed with Grok today !',
        reason: 'the line break would end the text on an empty line above the card',
      );
      expect(
        await _shownText(tester, text, link),
        'I discussed with Grok today !\nx.com/i/grok/share/32db3dd9ab8941ecbad132eeadd54f37',
        reason: 'a link the text keeps keeps its line',
      );
    });

    testWidgets('keep the text around them when more follows', (tester) async {
      final link = _link('https://t.co/g', 'https://x.com/i/grok/share/abc123', [5, 19]);

      expect(await _shownText(tester, 'Look https://t.co/g at this', link, hideGrokShareLinks: true), 'Look  at this');
    });

    testWidgets('leave no line break before a broadcast shown under the text either', (tester) async {
      final link = _link('https://t.co/b', 'https://x.com/i/broadcasts/1ZkJzbdvLgRJv', [10, 24]);

      expect(await _shownText(tester, 'Live now\n\nhttps://t.co/b', link), 'Live now');
    });

    test('a hidden link entity paints nothing', () {
      final url = Url.fromJson({
        'expanded_url': 'https://x.com/i/grok/share/abc123',
        'display_url': 'x.com/i/grok/share/abc…',
        'indices': [0, 1],
      });
      final context = EntitySpanContext(linkColor: Colors.blue, recognizer: (_) => throw StateError('no tap'));
      expect(UrlEntity(url, () {}, hidden: true).getContent(context).toPlainText(), '');
    });
  });

  group('TweetCard', () {
    testWidgets('shows every picture of a carousel above its page', (tester) async {
      await _pumpCard(tester, _keyedCard('image_carousel_website'));
      final media = tester.widget<TweetMedia>(find.byType(TweetMedia));
      expect(media.media.map((m) => m.idStr), ['2082854064501489664', '2082853967843762176']);
      expect(find.textContaining('Tap to read'), findsOneWidget);
      expect(find.text('uefa.com'), findsOneWidget);
    });

    testWidgets('reads a carousel kept as X sent it', (tester) async {
      await _pumpCard(tester, _rawCard('image_carousel_website'));
      expect(find.byType(TweetMedia), findsOneWidget);
    });

    testWidgets('shows a Grok share as its question and answer', (tester) async {
      await _pumpCard(tester, _keyedCard('grok_share'));
      expect(find.byType(GrokShareCard), findsOneWidget);
      expect(find.text('Is this real?'), findsOneWidget);
      expect(find.textContaining('Yes, the clip is real footage'), findsOneWidget);
      expect(find.textContaining('grok:render'), findsNothing);
    });

    testWidgets('lets a Grok question longer than three lines be unfolded', (tester) async {
      await _pumpCard(tester, _edited('grok_share', (u) => _question(u)['message'] = _longQuestion));
      final question = find.ancestor(of: find.text(_longQuestion), matching: find.byType(ExpandableTweetText));
      Finder showMore() => find.descendant(of: question, matching: find.text('Show more'));

      expect(showMore(), findsOneWidget, reason: 'cut after three lines, the rest a tap away');
      await tester.tap(showMore());
      await tester.pump();
      expect(showMore(), findsNothing);

      await _pumpCard(tester, _edited('grok_share', (u) => _question(u)['message'] = _longQuestion));
      expect(showMore(), findsNothing, reason: 'drawing the card again keeps the question unfolded');
    });

    testWidgets('shows a short Grok question whole', (tester) async {
      await _pumpCard(tester, _keyedCard('grok_share'));

      expect(find.text('Is this real?'), findsOneWidget);
      expect(find.text('Show more'), findsNothing);
    });

    testWidgets('opens the Grok conversation from its question', (tester) async {
      final card = _edited('grok_share', (u) => _question(u)['message'] = _longQuestion);
      var opened = 0;
      await _pump(
        tester,
        GrokShareCard(share: GrokShareCardData.fromUnified(unifiedCardOf(card)!)!, onTap: () => opened++),
      );

      await tester.tap(find.text(_longQuestion));
      expect(opened, 1, reason: 'the question is part of the card, which opens the conversation');
      await tester.tap(find.text('Show more'));
      await tester.pump();
      expect(opened, 1, reason: 'Show more unfolds the question and opens nothing');
    });

    testWidgets('keeps an icon where Grok\'s picture goes when there is none', (tester) async {
      await _pumpCard(tester, _edited('grok_share', (u) => _details(u).remove('grok_user')));
      expect(find.byIcon(Icons.auto_awesome), findsOneWidget);
    });

    testWidgets('draws nothing for a carousel or Grok share it cannot read', (tester) async {
      for (final card in [
        _edited('image_carousel_website', (u) => u.remove('destination_objects')),
        _edited('grok_share', (u) => u.remove('destination_objects')),
        _edited('grok_share', (u) => _details(u).remove('conversation_preview')),
      ]) {
        await _pumpCard(tester, card);
        expect(tester.takeException(), isNull);
        expect(find.byType(TweetMedia), findsNothing);
        expect(find.byType(GrokShareCard), findsNothing);
      }
    });
  });
}
