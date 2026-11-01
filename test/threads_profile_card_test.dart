import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_parse.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/feed_ink_harness.dart';

/// What a logged-out browser is shown on a profile's page: a name, a picture and a description with two counts.
const _guestPage = '''
<html><head>
<meta property="og:title" content="Maya Chen (&#064;maya) &#x2022; Threads, Say more" />
<meta property="og:description" content="1.2K Followers &#x2022; 48 Threads &#x2022; Ceramics, slowly. With @dana at https://studio.example/kiln" />
<meta property="og:image" content="https://cdn.example/maya.jpg" />
</head></html>
''';

/// docs/specs/threads-x-parity.md, Profiles.
void main() {
  group('a guest profile', () {
    test('knows the counts the page gave, not whom it follows, and does not link to itself', () {
      final profile = threadsProfileFromGuestHtml(_guestPage, 'maya')!;

      expect(profile.followerCount, 1200);
      expect(profile.mediaCount, 48);
      expect(profile.followingCount, isNull);
      expect(profile.externalUrl, isNull);
    });

    test('pieced together from posts knows no count', () {
      final profile = threadsProfileFromPosts('maya', [
        const ThreadsPost(id: '1', handle: 'maya', authorName: 'Maya Chen', text: 'kiln'),
      ])!;

      expect([profile.followerCount, profile.followingCount, profile.mediaCount], everyElement(isNull));
      expect(profile.externalUrl, isNull);
    });
  });

  group('on screen', () {
    Future<void> pump(WidgetTester tester, ThreadsProfile profile) => pumpInk(
      tester,
      Scaffold(
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [ThreadsProfileCard(profile: profile)],
        ),
      ),
      theme: xLookLightsOutTheme(null),
      locale: const Locale('en'),
    );

    testWidgets('a guest profile shows the counts it knows, no following count and no link to itself', (tester) async {
      await pump(tester, threadsProfileFromGuestHtml(_guestPage, 'maya')!);

      expect(find.textContaining(L10n.current.followers, findRichText: true), findsOneWidget);
      expect(find.textContaining(L10n.current.tweets, findRichText: true), findsOneWidget);
      expect(find.textContaining(L10n.current.following, findRichText: true), findsNothing);
      expect(find.textContaining('threads.com', findRichText: true), findsNothing);
      expect(find.byIcon(Icons.link), findsNothing);
    });

    testWidgets('a profile that knows no count shows none', (tester) async {
      await pump(
        tester,
        threadsProfileFromPosts('maya', [
          const ThreadsPost(id: '1', handle: 'maya', authorName: 'Maya Chen', text: 'kiln'),
        ])!,
      );

      expect(find.textContaining(L10n.current.followers, findRichText: true), findsNothing);
      expect(find.textContaining(L10n.current.tweets, findRichText: true), findsNothing);
    });

    testWidgets("the bio's @mentions and links can be tapped", (tester) async {
      await pump(tester, threadsProfileFromGuestHtml(_guestPage, 'maya')!);

      final spans = <TextSpan>[];
      for (final text in tester.widgetList<RichText>(find.byType(RichText))) {
        text.text.visitChildren((span) {
          if (span is TextSpan) spans.add(span);
          return true;
        });
      }
      TextSpan span(String text) => spans.firstWhere((span) => span.text == text);

      expect(span('@dana').recognizer, isA<TapGestureRecognizer>());
      expect(span('https://studio.example/kiln').recognizer, isA<TapGestureRecognizer>());
    });
  });
}
