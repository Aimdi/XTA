import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_card.dart';
import 'package:xta/tweet/_media.dart';
import 'package:xta/tweet/poll.dart';
import 'package:xta/tweet/poll_results.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/capped_network_image.dart';
import 'package:xta/ui/x_look_theme.dart';

Map<String, dynamic> _card(Map<String, String> values) => {
      'binding_values': {for (final entry in values.entries) entry.key: {'string_value': entry.value}}
    };

/// x.com/quax_tests/status/2109301223363064091 as the client reads it: a poll
/// of three options, each with its image, one vote on the third.
TweetWithCard _imagePoll() => TweetWithCard.fromGraphqlJson(
    jsonDecode(File('test/fixtures/TweetDetail/image_poll.json').readAsStringSync()) as Map<String, dynamic>);

/// The card's bindings, keyed, to break on purpose.
Map<String, dynamic> _bindings(TweetWithCard post) => post.card!['binding_values'] as Map<String, dynamic>;

String _imageUrl(TweetWithCard post, int choice, String size) =>
    _bindings(post)['choice${choice}_image$size']['image_value']['url'] as String;

List<String> _imageUrls(TweetWithCard post, String size) => [for (var i = 1; i <= 3; i++) _imageUrl(post, i, size)];

void main() {
  group('reading a poll', () {
    test('shares add up and the leader is the highest count', () {
      final poll = TweetPoll.fromCard(
          _card({
            'choice1_label': 'Yes',
            'choice1_count': '30',
            'choice2_label': 'No',
            'choice2_count': '10',
            'end_datetime_utc': '2026-01-01T12:00:00Z',
          }),
          2)!;

      expect(poll.total, 40);
      expect(poll.choices.map((c) => c.label), ['Yes', 'No']);
      expect(poll.choices.first.share, closeTo(0.75, 0.001));
      expect(poll.choices.last.share, closeTo(0.25, 0.001));
      expect(poll.leadingCount, 30);
      expect(poll.endsAt, DateTime.parse('2026-01-01T12:00:00Z'));
    });

    test('a poll nobody has voted in divides by nothing', () {
      final poll = TweetPoll.fromCard(
          _card({
            'choice1_label': 'Yes',
            'choice1_count': '0',
            'choice2_label': 'No',
            'choice2_count': '0',
          }),
          2)!;

      expect(poll.total, 0);
      expect(poll.choices.every((c) => c.share == 0), isTrue);
      expect(poll.leadingCount, 0, reason: 'with no votes no bar is the leader');
    });

    test('four choices are all read', () {
      final poll = TweetPoll.fromCard(
          _card({
            for (var i = 1; i <= 4; i++) ...{'choice${i}_label': 'Option $i', 'choice${i}_count': '$i'}
          }),
          4)!;

      expect(poll.choices, hasLength(4));
      expect(poll.total, 10);
      expect(poll.leadingCount, 4);
    });
  });

  group('a payload that does not fit', () {
    test('a missing choice gives up rather than throwing mid-timeline', () {
      final card = _card({'choice1_label': 'Yes', 'choice1_count': '3'});

      expect(TweetPoll.fromCard(card, 2), isNull);
    });

    test('binding values of the wrong shape give up', () {
      expect(TweetPoll.fromCard({'binding_values': 'nope'}, 2), isNull);
      expect(TweetPoll.fromCard(const {}, 2), isNull);
    });

    test('an unparseable count is no votes, not a crash', () {
      final poll = TweetPoll.fromCard(
          _card({
            'choice1_label': 'Yes',
            'choice1_count': 'lots',
            'choice2_label': 'No',
            'choice2_count': '4',
          }),
          2)!;

      expect(poll.choices.first.count, 0);
      expect(poll.total, 4);
    });

    test('an unparseable end date leaves the poll without one', () {
      final poll = TweetPoll.fromCard(
          _card({
            'choice1_label': 'Yes',
            'choice1_count': '1',
            'choice2_label': 'No',
            'choice2_count': '1',
            'end_datetime_utc': 'soon',
          }),
          2)!;

      expect(poll.endsAt, isNull);
    });
  });

  group('reading a poll with images', () {
    test('reads how many options it has, and each option with its image at both sizes', () {
      final post = _imagePoll();
      expect(TweetPoll.choiceCountOf(post.card!), 3);

      final poll = TweetPoll.fromCard(post.card!, 3)!;
      expect(poll.choices.map((c) => c.label), ['Option A', 'Option B', 'Option C']);
      expect(poll.choices.map((c) => c.thumbnailUrl), _imageUrls(post, ''));
      expect(poll.choices.map((c) => c.imageUrl), _imageUrls(post, '_original'));
      expect(poll.choices.map(poll.leads), [false, false, true]);
    });

    test('an option without an image keeps its place among the others', () {
      final post = _imagePoll();
      _bindings(post).removeWhere((key, _) => key.startsWith('choice2_image'));
      final poll = TweetPoll.fromCard(post.card!, 3)!;

      expect([poll.choices[1].thumbnailUrl, poll.choices[1].imageUrl], [null, null]);
      expect(poll.choicesWithImages.map((c) => c.label), ['Option A', 'Option C']);
    });

    test('either size of an image stands in for the other', () {
      final post = _imagePoll();
      final thumbnail = _imageUrl(post, 3, '');
      final original = _imageUrl(post, 1, '_original');
      _bindings(post)
        ..remove('choice1_image')
        ..remove('choice3_image_original');
      final poll = TweetPoll.fromCard(post.card!, 3)!;

      expect(poll.choices.first.thumbnailUrl, original);
      expect(poll.choices.last.imageUrl, thumbnail);
    });

    test('a text poll has no images', () {
      final poll = TweetPoll.fromCard(
          _card({'choice1_label': 'Yes', 'choice1_count': '1', 'choice2_label': 'No', 'choice2_count': '2'}), 2)!;

      expect(poll.choicesWithImages, isEmpty);
    });

    test('a card that gives no usable number of options has none', () {
      final post = _imagePoll();
      for (final count in [null, '0', 'three']) {
        if (count == null) {
          _bindings(post).remove('choice_count');
        } else {
          _bindings(post)['choice_count'] = {'string_value': count};
        }
        expect(TweetPoll.choiceCountOf(post.card!), isNull, reason: '$count');
      }
    });
  });

  group('drawing a poll with images', () {
    Future<void> pumpCard(WidgetTester tester, TweetWithCard post,
        {Map<String, dynamic> prefs = const {}, double width = 411, double textScale = 1}) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = Size(width, 800);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      await tester.pumpWidget(PrefService(
        service: PrefServiceCache(cache: prefs),
        child: MaterialApp(
          theme: xLookLightTheme(null),
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [Locale('en')],
          home: MediaQuery(
            data: MediaQueryData(size: Size(width, 800), textScaler: TextScaler.linear(textScale)),
            child: Scaffold(body: SingleChildScrollView(child: TweetCard(tweet: post, card: post.card))),
          ),
        ),
      ));
      await tester.pump();
    }

    List<String> loadedImages(WidgetTester tester) =>
        [for (final image in tester.widgetList<CappedNetworkImage>(find.byType(CappedNetworkImage))) image.url];

    /// Taps the image of option [index] and waits for the viewer it opens.
    Future<TweetMediaView> openImage(WidgetTester tester, int index) async {
      await tester.tap(find.byType(PollChoiceImage).at(index));
      await tester.pump(const Duration(milliseconds: 250));
      // The viewer's photo never loads here, and its spinner never settles.
      await tester.pump(const Duration(milliseconds: 400));
      return tester.widget<TweetMediaView>(find.byType(TweetMediaView));
    }

    testWidgets('shows each option beside its image', (tester) async {
      final post = _imagePoll();
      await pumpCard(tester, post);

      for (final label in ['Option A', 'Option B', 'Option C']) {
        expect(find.text(label), findsOneWidget);
      }
      expect(loadedImages(tester), _imageUrls(post, ''), reason: 'the small size, which is all a bar shows');
      for (final (index, bar) in tester.widgetList<PollOptionBar>(find.byType(PollOptionBar)).indexed) {
        final image = find.descendant(of: find.byWidget(bar), matching: find.byType(PollChoiceImage));
        expect(tester.getSize(image), const Size.square(kTweetTouchTarget), reason: 'option ${index + 1}');
        expect(tester.getRect(image).left - tester.getRect(find.byWidget(bar)).left, 6,
            reason: 'as far in from the start of its bar as from the top');
      }
    });

    testWidgets('opens the full images in the viewer, on the one tapped', (tester) async {
      final post = _imagePoll();
      await pumpCard(tester, post);
      final viewer = await openImage(tester, 1);

      expect(viewer.entries.map((e) => e.media.mediaUrlHttps), _imageUrls(post, '_original'));
      expect(viewer.initialIndex, 1);
      expect(viewer.tweetMedia, isFalse, reason: 'a size suffix would break these whole URLs');
    });

    testWidgets('an option without an image keeps its bar, and the viewer skips it', (tester) async {
      final post = _imagePoll();
      _bindings(post).removeWhere((key, _) => key.startsWith('choice2_image'));
      await pumpCard(tester, post);

      expect(find.text('Option B'), findsOneWidget);
      expect(loadedImages(tester), [_imageUrl(post, 1, ''), _imageUrl(post, 3, '')]);
      final viewer = await openImage(tester, 1);
      expect(viewer.entries.map((e) => e.media.mediaUrlHttps), [
        _imageUrl(post, 1, '_original'),
        _imageUrl(post, 3, '_original'),
      ]);
      expect(viewer.initialIndex, 1, reason: 'the second image is the third option\'s');
    });

    testWidgets('shows no image when images are disabled', (tester) async {
      await pumpCard(tester, _imagePoll(), prefs: {optionImageQuality: 'disabled'});

      expect(find.text('Option A'), findsOneWidget);
      expect(find.byType(PollChoiceImage), findsNothing);
    });

    testWidgets('loads no image before a tap when media load manually', (tester) async {
      final post = _imagePoll();
      await pumpCard(tester, post, prefs: {optionMediaDisableAutoload: true});

      expect(find.byType(PollChoiceImage), findsNWidgets(3), reason: 'each image waits in its place');
      expect(loadedImages(tester), isEmpty);
      final viewer = await openImage(tester, 2);
      expect(viewer.entries.map((e) => e.media.mediaUrlHttps), _imageUrls(post, '_original'));
      expect(viewer.initialIndex, 2);
    });

    testWidgets('reads each image as part of its option, and opens it', (tester) async {
      final handle = tester.ensureSemantics();
      await pumpCard(tester, _imagePoll());

      final option = find.descendant(of: find.byType(PollOptionBar).first, matching: find.byType(MergeSemantics));
      expect(tester.getSemantics(option), isSemantics(label: 'Media\nOption A\n0%', isButton: true, hasTapAction: true));
      handle.dispose();
    });

    testWidgets('draws nothing for a poll with images that does not say how many options it has', (tester) async {
      final post = _imagePoll();
      _bindings(post).remove('choice_count');
      await pumpCard(tester, post);

      expect(tester.takeException(), isNull);
      expect(find.byType(PollOptionBar), findsNothing);
    });

    testWidgets('fits 320dp at twice the text size with its images', (tester) async {
      await pumpCard(tester, _imagePoll(), width: 320, textScale: 2);

      expect(tester.takeException(), isNull, reason: 'no overflow');
      for (final bar in tester.widgetList<PollOptionBar>(find.byType(PollOptionBar))) {
        final barRect = tester.getRect(find.byWidget(bar));
        final percent = tester.getRect(find.descendant(of: find.byWidget(bar), matching: find.text(bar.percent)));
        expect(barRect.right - percent.right, closeTo(12, 0.5), reason: '${bar.label}: the share stays at the end');
        expect(barRect.right, lessThanOrEqualTo(320));
      }
    });
  });

  group('who leads', () {
    test('the most voted option leads, ties included', () {
      final poll = TweetPoll.fromCard(
          _card({
            'choice1_label': 'Yes',
            'choice1_count': '3',
            'choice2_label': 'No',
            'choice2_count': '3',
            'choice3_label': 'Maybe',
            'choice3_count': '1',
          }),
          3)!;

      expect(poll.choices.map(poll.leads), [true, true, false]);
    });

    test('nothing leads a poll nobody voted in', () {
      final poll = TweetPoll.fromCard(
          _card({'choice1_label': 'Yes', 'choice1_count': '0', 'choice2_label': 'No', 'choice2_count': '0'}), 2)!;

      expect(poll.choices.any(poll.leads), isFalse,
          reason: 'every count equals the zero leading count, which used to make every option bold');
    });
  });

  group('drawing the results', () {
    TweetPoll poll(String yes, String no, {String? ends}) => TweetPoll.fromCard(
        _card({
          'choice1_label': 'Yes',
          'choice1_count': yes,
          'choice2_label': 'Absolutely not, under no circumstances whatsoever',
          'choice2_count': no,
          'end_datetime_utc': ?ends,
        }),
        2)!;

    Future<void> pump(WidgetTester tester, TweetPoll poll,
        {double width = 411, double textScale = 1, bool dark = false}) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = Size(width, 800);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      await tester.pumpWidget(MaterialApp(
        theme: dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: const [Locale('en')],
        home: MediaQuery(
          data: MediaQueryData(size: Size(width, 800), textScaler: TextScaler.linear(textScale)),
          child: Scaffold(body: SingleChildScrollView(child: TweetPollResults(poll: poll))),
        ),
      ));
      await tester.pumpAndSettle();
    }

    FontWeight? weightOf(WidgetTester tester, String text) => tester.widget<Text>(find.text(text)).style?.fontWeight;

    double fillOf(WidgetTester tester, int option) =>
        tester.widgetList<FractionallySizedBox>(find.byType(FractionallySizedBox)).elementAt(option).widthFactor!;

    testWidgets('fills each bar to its share and picks out the leader', (tester) async {
      await pump(tester, poll('5', '3', ends: '2020-01-01T00:00:00Z'));

      expect(find.text('62.5%'), findsOneWidget);
      expect(find.text('37.5%'), findsOneWidget);
      expect(fillOf(tester, 0), closeTo(0.625, 0.001));
      expect(fillOf(tester, 1), closeTo(0.375, 0.001));
      expect(weightOf(tester, 'Yes'), FontWeight.w700);
      expect(weightOf(tester, '62.5%'), FontWeight.w700);
      expect(weightOf(tester, '37.5%'), FontWeight.w400);
      expect(find.textContaining(RegExp(r'^8 votes · Ended ')), findsOneWidget);
    });

    testWidgets('shares sit at the right end of their bars', (tester) async {
      await pump(tester, poll('1', '0'));

      final bars = find.byType(PollOptionBar);
      for (final (index, share) in [(0, '100%'), (1, '0%')]) {
        final bar = tester.getRect(bars.at(index));
        final text = tester.getRect(find.descendant(of: bars.at(index), matching: find.text(share)));
        expect(bar.right - text.right, closeTo(12, 0.5), reason: '$share is right-aligned in its bar');
      }
    });

    testWidgets('a poll with no votes has no leader', (tester) async {
      await pump(tester, poll('0', '0'));

      expect(weightOf(tester, 'Yes'), FontWeight.w400);
      expect(find.text('0%'), findsNWidgets(2));
      expect(find.textContaining('No votes'), findsOneWidget);
    });

    for (final dark in [false, true]) {
      testWidgets('fits 320dp at twice the text size (${dark ? 'dark' : 'light'})', (tester) async {
        await pump(tester, poll('120', '7'), width: 320, textScale: 2, dark: dark);

        expect(tester.takeException(), isNull, reason: 'no overflow');
        for (final bar in tester.widgetList<PollOptionBar>(find.byType(PollOptionBar))) {
          final barRect = tester.getRect(find.byWidget(bar));
          final label = tester.getRect(find.text(bar.label));
          expect(barRect.top <= label.top && label.bottom <= barRect.bottom, isTrue,
              reason: 'the bar grows with the text instead of clipping "${bar.label}"');
          expect(barRect.right, lessThanOrEqualTo(320));
        }
      });
    }
  });
}
