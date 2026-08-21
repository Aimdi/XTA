import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_home_source.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';

Widget _app(Widget child) {
  return MaterialApp(
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: child),
  );
}

class _FakeSubredditsStore extends RedditSubredditsStore {
  _FakeSubredditsStore(List<String> names) : super(PrefServiceCache()) {
    update(names);
  }
}

void main() {
  group('redditHomeFeedKey', () {
    test('a followed community is not the Following rail', () {
      const home = RedditHomeSource(mode: RedditFeedMode.following);
      const community = RedditHomeSource(
        mode: RedditFeedMode.following,
        subreddit: 'foo',
      );
      const fromPopular = RedditHomeSource(
        mode: RedditFeedMode.popular,
        subreddit: 'Bar',
      );

      expect(redditHomeFeedKey(home), 'following');
      expect(redditHomeFeedKey(community), 'r/foo');
      expect(redditHomeFeedKey(community), isNot('following'));
      expect(redditHomeFeedKey(fromPopular), 'r/bar');
      expect(redditHomeFeedKey(fromPopular), isNot('popular'));
      expect(redditHomeFeedKey(fromPopular), isNot('following'));
      expect(
        redditHomeRailSelected(community, RedditFeedMode.following),
        isFalse,
      );
      expect(redditHomeRailSelected(home, RedditFeedMode.following), isTrue);
    });
  });

  group('RedditHomeStore', () {
    test('selecting a followed subreddit changes the feed source', () async {
      final prefs = PrefServiceCache();
      final store = RedditHomeStore(prefs);

      expect(redditHomeFeedKey(store.state), 'following');

      await store.selectMode(RedditFeedMode.popular);
      expect(redditHomeFeedKey(store.state), 'popular');

      await store.selectSubreddit('foo');
      expect(store.state.subreddit, 'foo');
      expect(redditHomeFeedKey(store.state), 'r/foo');
      expect(redditHomeFeedKey(store.state), isNot('popular'));
      expect(redditHomeFeedKey(store.state), isNot('following'));
      expect(prefs.get<String>(optionPluginRedditSelectedSubreddit), 'foo');

      await store.selectSubreddit('bar');
      expect(redditHomeFeedKey(store.state), 'r/bar');
      expect(redditHomeFeedKey(store.state), isNot('r/foo'));

      await store.selectMode(RedditFeedMode.following);
      expect(store.state.subreddit, isNull);
      expect(redditHomeFeedKey(store.state), 'following');
      expect(prefs.get<String>(optionPluginRedditSelectedSubreddit), '');

      store.destroy();
    });

    test('an unfollowed persisted community falls back to the rail', () async {
      final prefs = PrefServiceCache(
        cache: {optionPluginRedditSelectedSubreddit: 'gone'},
      );
      final store = RedditHomeStore(prefs);

      expect(redditHomeFeedKey(store.state), 'r/gone');
      await store.reconcileFollowed(['foo', 'bar']);
      expect(store.state.subreddit, isNull);
      expect(redditHomeFeedKey(store.state), 'following');

      store.destroy();
    });
  });

  testWidgets('tapping a followed chip leaves Home and opens that community', (
    tester,
  ) async {
    final home = RedditHomeStore(PrefServiceCache());
    final subs = _FakeSubredditsStore(['foo', 'bar']);

    await tester.pumpWidget(
      PrefService(
        service: home.prefs,
        child: Provider<RedditSubredditsStore>.value(
          value: subs,
          child: _app(
            Column(
              children: [
                RedditHomeChrome(source: home.state, onMode: home.selectMode),
                RedditSubredditChips(home: home),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(find.text('r/foo'), findsOneWidget);
    expect(find.text('r/bar'), findsOneWidget);
    expect(redditHomeFeedKey(home.state), 'following');

    await tester.tap(find.text('r/foo'));
    await tester.pump();
    await tester.pump();

    expect(redditHomeFeedKey(home.state), 'r/foo');
    expect(redditHomeFeedKey(home.state), isNot('following'));
    expect(home.state.viewingSubreddit, isTrue);

    await tester.tap(find.text('r/bar'));
    await tester.pump();
    await tester.pump();

    expect(redditHomeFeedKey(home.state), 'r/bar');
    expect(redditHomeFeedKey(home.state), isNot('r/foo'));
    expect(redditHomeFeedKey(home.state), isNot('following'));

    home.destroy();
    subs.destroy();
  });
}
