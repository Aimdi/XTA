import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/_tweets.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/user.dart';

Map<String, dynamic> _result(String fixture) =>
    (jsonDecode(File('test/fixtures/UserByScreenName/$fixture.json').readAsStringSync())
            as Map<String, dynamic>)['data']['user']['result']
        as Map<String, dynamic>;

/// @quax_tests as current queries return it, without `legacy`: public, and
/// not followed by the account that asked.
Map<String, dynamic> _modern() => _result('modern');

/// @X in the older shape, its profile under `legacy`.
Map<String, dynamic> _legacy() => _result('ok');

/// [user] as X sends a protected account, which no capture holds, with
/// [following] as `relationship_perspectives` gives it.
Map<String, dynamic> _protected(Map<String, dynamic> user, {required bool following}) => {
  ...user,
  'privacy': {'protected': true},
  'relationship_perspectives': {...user['relationship_perspectives'] as Map, 'following': following},
};

void main() {
  group('whether X keeps the posts from the reader', () {
    test('it does not for a public account', () {
      expect(UserWithExtra.fromNonLegacyJson(_modern()).postsHidden, isFalse);
    });

    test('it does for a protected account the asking account does not follow', () {
      expect(UserWithExtra.fromNonLegacyJson(_protected(_modern(), following: false)).postsHidden, isTrue);
    });

    test('it does not for a protected account the asking account follows', () {
      expect(UserWithExtra.fromNonLegacyJson(_protected(_modern(), following: true)).postsHidden, isFalse);
    });

    test('it does when X does not say who follows, as for a guest', () {
      final guest = _protected(_modern(), following: false)..remove('relationship_perspectives');

      expect(UserWithExtra.fromNonLegacyJson(guest).postsHidden, isTrue);
    });

    test('the older shape says it under legacy, which the newer fields override', () {
      final user = _legacy();
      final legacy = user['legacy'] as Map<String, dynamic>;
      legacy['protected'] = true;
      expect(UserWithExtra.fromNonLegacyJson(user).postsHidden, isTrue, reason: 'legacy.following is false');

      legacy['following'] = true;
      expect(UserWithExtra.fromNonLegacyJson(user).postsHidden, isFalse);

      user['relationship_perspectives'] = {'following': false};
      expect(UserWithExtra.fromNonLegacyJson(user).postsHidden, isTrue, reason: 'relationship_perspectives wins');
    });

    test('a cached profile keeps the answer', () {
      final followed = UserWithExtra.fromNonLegacyJson(_protected(_modern(), following: true));
      final cached = UserWithExtra.fromJson(followed.toJson());

      expect([cached.protected, cached.following, cached.postsHidden], [true, true, false]);
    });
  });

  group('a protected account\'s posts tab', () {
    setUp(() => VisibilityDetectorController.instance.updateInterval = Duration.zero);
    tearDown(() => VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500));

    /// The posts tab of [user] behind its gate, and every page it asked for.
    Future<List<String?>> pumpTab(WidgetTester tester, UserWithExtra user) async {
      final requests = <String?>[];
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [Locale('en')],
          home: Scaffold(
            body: Builder(
              builder: (context) => ProtectedPostsGate(
                hidden: user.postsHidden,
                message: L10n.of(context).protected_posts(user.screenName!),
                child: ProfileTweets(
                  user: user,
                  type: 'profile',
                  includeReplies: false,
                  pinnedTweets: const [],
                  pref: PrefServiceCache(),
                  fetchTweets: (cursor) async {
                    requests.add(cursor);
                    return TweetStatus(chains: const [], cursorBottom: null, cursorTop: null);
                  },
                ),
              ),
            ),
          ),
        ),
      );
      // A first page waits up to a second on the timeline cache before asking.
      for (var i = 0; i < 4; i++) {
        await tester.pump(const Duration(seconds: 1));
      }
      return requests;
    }

    testWidgets('says the posts are protected, without asking X for them', (tester) async {
      final requests = await pumpTab(tester, UserWithExtra.fromNonLegacyJson(_protected(_modern(), following: false)));

      expect(requests, isEmpty, reason: 'X sends none of them, so the tab does not ask');
      expect(find.text("Only approved followers on X can see @quax_tests's posts."), findsOneWidget);
      expect(
        find.descendant(of: find.byType(TweetStateTile), matching: find.byIcon(Icons.lock_outline)),
        findsOneWidget,
      );
      expect(find.text(L10n.current.could_not_find_any_tweets_by_this_user), findsNothing, reason: 'not an empty tab');
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('loads the posts for an account that follows it', (tester) async {
      final requests = await pumpTab(tester, UserWithExtra.fromNonLegacyJson(_protected(_modern(), following: true)));

      expect(requests, [null], reason: 'the first page is asked for');
      expect(find.byType(TweetStateTile), findsNothing);
      expect(find.text(L10n.current.could_not_find_any_tweets_by_this_user), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });
}
