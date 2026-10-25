import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/main.dart' show xtaLocalizationsDelegates;
import 'package:xta/tweet/_card.dart';
import 'package:xta/tweet/focal_post.dart';
import 'package:xta/tweet/poll_fill.dart';
import 'package:xta/tweet/poll_results.dart';

/// A post with a poll: Yes 75 %, No 25 %.
TweetWithCard _post() => TweetWithCard()
  ..idStr = '1'
  ..card = {
    'name': 'poll2choice_text_only',
    'binding_values': {
      'choice1_label': {'string_value': 'Yes'},
      'choice1_count': {'string_value': '6'},
      'choice2_label': {'string_value': 'No'},
      'choice2_count': {'string_value': '2'},
    },
  };

Widget _poll(TweetWithCard post, {Key? key}) => TweetCard(key: key, tweet: post, card: post.card);

/// [post] as the opened post of a conversation screen.
Widget _opened(TweetWithCard post, {Widget? child}) => FocalPostScope(
  id: post.idStr!,
  child: FocalPollFill(child: child ?? _poll(post)),
);

Widget _app(Widget child, {bool reduceMotion = false}) => PrefService(
  service: PrefServiceCache(cache: const {}),
  child: MaterialApp(
    localizationsDelegates: xtaLocalizationsDelegates,
    supportedLocales: const [Locale('en')],
    home: MediaQuery(
      data: MediaQueryData(disableAnimations: reduceMotion),
      child: Scaffold(body: SingleChildScrollView(child: child)),
    ),
  ),
);

/// How far each bar has grown towards its share.
List<double> _grown(WidgetTester tester) => [
  for (final bar in tester.widgetList<PollOptionBar>(find.byType(PollOptionBar))) bar.grown,
];

void main() {
  group('the fill', () {
    final stagger = kPollBarStagger.inMilliseconds / kPollFill.inMilliseconds;
    final growth = kPollBarGrowth.inMilliseconds / kPollFill.inMilliseconds;

    test('takes every bar from nothing to its share', () {
      for (var index = 0; index < 4; index++) {
        expect(pollBarGrowth(0, index), 0);
        expect(pollBarGrowth(1, index), 1);
      }
    });

    test('starts each bar a stagger after the one above, and grows it for the same time', () {
      for (var index = 1; index < 4; index++) {
        final start = index * stagger;
        expect(pollBarGrowth(start, index), 0, reason: 'bar ${index + 1} waits');
        expect(pollBarGrowth(start, index - 1), greaterThan(0), reason: 'while the one above grows');
        expect(pollBarGrowth(start + growth / 2, index), inExclusiveRange(0, 1));
        expect(pollBarGrowth(start + growth, index), 1);
      }
    });
  });

  testWidgets('an opened post\'s poll grows its bars in order to their shares', (tester) async {
    await tester.pumpWidget(_app(_opened(_post())));
    expect(_grown(tester), [0, 0]);
    expect(find.text('75%'), findsOneWidget, reason: 'the shares are there from the start');

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    final [yes, no] = _grown(tester);
    expect(yes, inExclusiveRange(0, 1));
    expect(no, inExclusiveRange(0, yes));

    await tester.pumpAndSettle();
    expect(_grown(tester), [1, 1]);
    expect(tester.widget<FractionallySizedBox>(find.byType(FractionallySizedBox).first).widthFactor, 0.75);
  });

  testWidgets('a poll rebuilt mid-fill carries on, and one scrolled back into view stays full', (tester) async {
    final post = _post();
    await tester.pumpWidget(_app(_opened(post, child: _poll(post, key: const ValueKey('copy')))));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    final before = _grown(tester);

    // X's answer takes the copy's place.
    await tester.pumpWidget(_app(_opened(post, child: _poll(post, key: const ValueKey('answer')))));
    expect(_grown(tester), before);
    await tester.pumpAndSettle();

    await tester.pumpWidget(_app(_opened(post, child: const SizedBox())));
    await tester.pumpWidget(_app(_opened(post)));
    expect(_grown(tester), [1, 1]);
  });

  testWidgets('it waits for the conversation screen to slide in', (tester) async {
    await tester.pumpWidget(_app(Builder(builder: (context) => const SizedBox())));
    final navigator = tester.state<NavigatorState>(find.byType(Navigator));
    navigator.push(MaterialPageRoute<void>(builder: (_) => Scaffold(body: _opened(_post()))));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    final entrance = ModalRoute.of(tester.element(find.byType(TweetCard)))!.animation!;
    expect(entrance.isCompleted, isFalse);
    expect(_grown(tester), [0, 0]);

    while (!entrance.isCompleted) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    expect(_grown(tester), [0, 0]);
    await tester.pump(const Duration(milliseconds: 200));
    expect(_grown(tester).first, greaterThan(0));
    await tester.pumpAndSettle();
  });

  testWidgets('a feed\'s poll is full at once', (tester) async {
    await tester.pumpWidget(_app(_poll(_post())));
    expect(_grown(tester), [1, 1]);
  });

  testWidgets('so is another post\'s on a conversation screen', (tester) async {
    await tester.pumpWidget(
      _app(
        FocalPostScope(
          id: '2',
          child: FocalPollFill(child: _poll(_post())),
        ),
      ),
    );
    expect(_grown(tester), [1, 1]);
  });

  testWidgets('with reduced motion an opened post\'s poll is full at once', (tester) async {
    await tester.pumpWidget(_app(_opened(_post()), reduceMotion: true));
    expect(_grown(tester), [1, 1]);
    await tester.pump();
    expect(_grown(tester), [1, 1]);
  });
}
