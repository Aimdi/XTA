import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/home_timeline_controls.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_interleaved.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/ui/dates.dart';

/// The canvas's two posts (docs/specs/feed-ink.md): an X post from a
/// verified account and a Reddit post with a picture.
TweetWithCard inkXPost({String text = _clashText, bool verified = true}) => TweetWithCard()
  ..idStr = '42'
  ..fullText = text
  ..lang = 'en'
  ..createdAt = DateTime.now().subtract(const Duration(minutes: 19))
  ..user = (User()
    ..idStr = '7'
    ..name = 'Clash Report'
    ..screenName = 'clashreport'
    ..verified = verified
    ..profileImageUrlHttps = 'https://pbs.twimg.com/profile_images/7/clash_normal.jpg')
  ..replyCount = 12
  ..retweetCount = 40
  ..quoteCount = 3
  ..favoriteCount = 512
  ..viewCount = 20400;

const _clashText =
    'Russia has extended its ban on most diesel exports through January 2027 to stabilize domestic supplies, '
    'with exemptions for some exporters.';

RedditPost inkRedditPost({bool picture = true}) => RedditPost(
  id: 'r1',
  subreddit: 'EconomyCharts',
  title:
      'This is a crazy graph, it shows that in Spain since 2009, public spending on pensions has more than '
      'doubled while public spending on every other thing has declined.',
  author: 'Illustrious_Gap1681',
  permalink: '/r/EconomyCharts/comments/r1/crazy_graph/',
  url: picture ? 'https://i.redd.it/chart.png' : null,
  createdAt: DateTime.now().subtract(const Duration(minutes: 15)),
  score: 45,
  commentCount: 13,
  previewImage: picture ? 'https://i.redd.it/chart.png' : null,
);

/// No network, no cache: the subreddit gets its generated picture.
class InkNoIcons implements RedditIcons {
  @override
  RedditClient get client => throw UnimplementedError();

  @override
  Future<String?> iconFor(
    String subreddit, {
    String clientId = '',
    String? userToken,
    bool preferPublic = false,
  }) async => null;
}

/// Following as the canvas draws it: the bar, a Reddit post as a mixed
/// timeline builds it, an X post, and the bottom bar.
Widget inkFeedScreen({int filters = 1, int turnedOff = 7, bool showLabels = false}) => Builder(
  builder: (context) => Scaffold(
    extendBody: true,
    body: SafeArea(
      bottom: false,
      child: Column(
        children: [
          HomeFollowingBar(
            group: _group(filters),
            mediaOnly: false,
            onOrderSelected: (_) {},
            onMediaToggle: () {},
            onFilters: () {},
            layout: homeFollowingBarLayout(context, width: MediaQuery.sizeOf(context).width, actions: 3),
            actions: [
              IconButton(tooltip: 'Feeds', icon: const Icon(Icons.dynamic_feed), onPressed: () {}),
              IconButton(tooltip: 'Search', icon: const Icon(Icons.manage_search), onPressed: () {}),
              homeFeedAccountsButton(context, turnedOff: turnedOff, onPressed: () {}),
            ],
          ),
          Divider(height: 0, thickness: 0.5, color: Theme.of(context).dividerColor),
          Expanded(
            child: ListView(
              children: [
                redditInterleavedItems([inkRedditPost()]).single.build(context),
                TweetTile(clickable: true, tweet: inkXPost()),
              ],
            ),
          ),
        ],
      ),
    ),
    bottomNavigationBar: HomeNavigationBar(
      selectedIndex: 0,
      showLabels: showLabels,
      disableAnimations: true,
      onSelected: (_) {},
      items: const [
        HomeNavigationItem(label: 'Start', icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home)),
        HomeNavigationItem(label: 'Abos', icon: Icon(Icons.people_outlined), selectedIcon: Icon(Icons.people)),
        HomeNavigationItem(label: 'Entdecken', icon: Icon(Icons.search_outlined), selectedIcon: Icon(Icons.search)),
        HomeNavigationItem(
          label: 'Gespeichert',
          icon: Icon(Icons.bookmark_border_outlined),
          selectedIcon: Icon(Icons.bookmark),
        ),
      ],
    ),
  ),
);

SubscriptionGroupGet _group(int filters) => SubscriptionGroupGet(
  id: '-1',
  name: 'Following',
  icon: 'rss_feed',
  subscriptions: const [],
  includeReplies: true,
  includeRetweets: true,
  popular: false,
  custom: filters > 0,
  minLikes: filters > 0 ? 10 : 0,
);

/// [child] with everything a timeline post and a Reddit post read, in
/// [locale] on a phone [size] at text [scale].
Future<void> pumpInk(
  WidgetTester tester,
  Widget child, {
  required ThemeData theme,
  Locale locale = const Locale('de'),
  Size size = const Size(390, 844),
  double scale = 1,
  TextDirection? direction,
}) async {
  // As the app registers them at start: "19 Min." rather than "vor 19 Minuten".
  timeago.setLocaleMessages('de', timeago.DeMessages());
  timeago.setLocaleMessages('de_short', timeago.DeShortMessages());
  compactDateLocales.add('de');
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final likes = LikedTweetModel();
  final saves = SavedTweetModel();
  final votes = RedditVotesStore();
  addTearDown(likes.destroy);
  addTearDown(saves.destroy);
  addTearDown(votes.destroy);

  await tester.pumpWidget(
    MultiProvider(
      providers: [
        Provider<LikedTweetModel>.value(value: likes),
        Provider<SavedTweetModel>.value(value: saves),
        Provider<RedditVotesStore>.value(value: votes),
        Provider<RedditIcons>.value(value: InkNoIcons()),
      ],
      child: PrefService(
        service: PrefServiceCache(
          cache: {
            optionLocale: locale.languageCode,
            optionShareBaseUrl: '',
            optionNonConfirmationBiasMode: false,
            optionTweetsShowSubscribeBadge: false,
            optionUseAbsoluteTimestamp: false,
            alwaysShowFullTweetContents: false,
            optionGestureDoubleTapLike: false,
            optionThemeTrueBlack: false,
            optionThemeTrueBlackTweetCards: false,
            optionZenMode: false,
            optionCalmMode: false,
            optionDisableAnimations: true,
          },
        ),
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: theme,
          locale: locale,
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          builder: (context, app) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
            child: direction == null ? app! : Directionality(textDirection: direction, child: app!),
          ),
          home: RepaintBoundary(key: const ValueKey('ink-render'), child: child),
        ),
      ),
    ),
  );
}
