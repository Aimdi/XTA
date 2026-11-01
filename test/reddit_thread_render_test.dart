import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/main.dart' show setTimeagoLocales;
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_thread_navigation.dart';
import 'package:xta/plugins/reddit/reddit_thread_screen.dart';
import 'package:xta/plugins/reddit/reddit_thread_visits.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/reddit_thread_harness.dart';
import 'support/review_fonts.dart';

/// Renders the Reddit thread beside the owner's boards:
///   fvm flutter test test/reddit_thread_render_test.dart --dart-define=RENDER_REDDIT_THREAD=true
const _render = bool.fromEnvironment('RENDER_REDDIT_THREAD');

const _shot = ValueKey('reddit-thread-shot');

DateTime _ago(Duration age) => DateTime.now().subtract(age);

final _post = RedditPost(
  id: 'eu1',
  title: 'EU Commission backs Poland government over constitutional court crisis',
  subreddit: 'europe',
  permalink: '/r/europe/comments/eu1/eu_commission/',
  author: 'Ceyyhun',
  score: 30,
  commentCount: 6,
  createdAt: _ago(const Duration(hours: 1)),
  url: 'https://www.yahoo.com/news/eu-commission',
  domain: 'yahoo.com',
);

List<RedditComment> _comments() => [
  RedditComment(
    id: 'c1',
    author: 'obserwator_z_kanapy',
    authorFlair: 'Poland',
    score: 1,
    createdAt: _ago(const Duration(minutes: 60)),
    body:
        "Funny how 'defending the Constitution' always means defending the judges appointed in one night, in 2015, "
        'by the party that needed them!!!!',
    replies: [
      RedditComment(
        id: 'c1a',
        author: 'Ceyyhun',
        isSubmitter: true,
        score: 3,
        createdAt: _ago(const Duration(minutes: 50)),
        body: 'The ruling is the Commission saying that out loud.',
      ),
    ],
  ),
  RedditComment(
    id: 'c2',
    author: 'paneuropeanism_',
    score: 1,
    createdAt: _ago(const Duration(minutes: 58)),
    body: "Tusk leads the way! It's time to detrumpify and deputinise Europe.",
    replies: [
      RedditComment(
        id: 'c3',
        author: 'jeanmacoun',
        score: 1,
        createdAt: _ago(const Duration(minutes: 39)),
        body:
            'If only he was as much focused on dramatic situation of polish healthcare. People are getting told the '
            'earliest date for appointment is in a year.',
        replies: [
          RedditComment(
            id: 'c3r',
            author: '[deleted]',
            body: '[removed]',
            createdAt: _ago(const Duration(minutes: 30)),
          ),
          RedditComment(id: 'more-c3', body: '', permalink: '/r/europe/comments/eu1/x/c3/', moreCount: 4),
        ],
      ),
    ],
  ),
  RedditComment(
    id: 'c4',
    author: 'utter_master',
    score: 1,
    createdAt: _ago(const Duration(minutes: 42)),
    body: 'EU is starting to look a lot more like China.',
    replies: [
      RedditComment(
        id: 'c5',
        author: 'User929261',
        score: 1,
        createdAt: _ago(const Duration(minutes: 21)),
        body: 'I wish. Instead we do not even have a budget or taxes or a common police or military.',
      ),
    ],
  ),
];

PrefServiceCache _visitedHalfAnHourAgo() => PrefServiceCache(
  cache: {
    optionPluginRedditThreadVisits:
        '{"${redditThreadKey(_post.permalink)}":${_ago(const Duration(minutes: 30)).millisecondsSinceEpoch}}',
  },
);

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    setTimeagoLocales();
    await loadReviewFonts();
  });

  for (final (name, theme, locale, width, scale, scroll) in [
    ('reddit-thread-de-390-lightsout', xLookLightsOutTheme(null), const Locale('de'), 390.0, 1.0, 0.0),
    ('reddit-thread-en-390-light', xLookLightTheme(null), const Locale('en'), 390.0, 1.0, 0.0),
    ('reddit-thread-comments-en-390-lightsout', xLookLightsOutTheme(null), const Locale('en'), 390.0, 1.0, 330.0),
    ('reddit-thread-de-320-x1.3', xLookLightsOutTheme(null), const Locale('de'), 320.0, 1.3, 0.0),
  ]) {
    testWidgets(name, skip: !_render, (tester) async {
      final app = redditThreadApp(
        RedditThreadScreen(post: _post),
        comments: _comments(),
        theme: theme,
        locale: locale,
        scale: scale,
        prefs: _visitedHalfAnHourAgo(),
      );
      await pumpRedditThread(
        tester,
        RepaintBoundary(key: _shot, child: app),
        size: Size(width, 844),
      );
      if (scroll > 0) {
        await tester.drag(find.byType(RedditCommentsHeader), Offset(0, -scroll));
        await tester.pumpAndSettle();
      }
      expect(tester.takeException(), isNull);
      await expectLater(find.byKey(_shot), matchesGoldenFile('../review-artifacts/renders/$name.png'));
    });
  }
}
