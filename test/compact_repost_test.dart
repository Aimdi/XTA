import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_header.dart';
import 'package:xta/ui/dates.dart';

const reposterName = 'A very long reposter name that should not fill the feed';

TweetWithCard _post({bool repost = false}) {
  final original = TweetWithCard()
    ..idStr = 'original'
    ..fullText = 'A short post for reading.'
    ..lang = 'en'
    ..user = (User()
      ..idStr = 'author-id'
      ..screenName = 'author'
      ..name = 'Original author');
  return repost
      ? (TweetWithCard()
          ..idStr = 'repost'
          ..createdAt = DateTime.now().subtract(const Duration(minutes: 36))
          ..lang = 'en'
          ..user = (User()
            ..idStr = 'reposter-id'
            ..screenName = 'reposter'
            ..name = reposterName)
          ..retweetedStatusWithCard = original)
      : original;
}

Future<void> _pump(
  WidgetTester tester,
  TweetWithCard post, {
  bool thread = false,
  double scale = 1,
  Locale locale = const Locale('en'),
  RouteFactory? onGenerateRoute,
}) async {
  final likes = LikedTweetModel();
  final saves = SavedTweetModel();
  addTearDown(likes.destroy);
  addTearDown(saves.destroy);
  await tester.pumpWidget(
    MultiProvider(
      providers: [
        Provider<LikedTweetModel>.value(value: likes),
        Provider<SavedTweetModel>.value(value: saves),
      ],
      child: PrefService(
        service: PrefServiceCache(
          cache: {
            optionLocale: locale.languageCode,
            optionNonConfirmationBiasMode: false,
            optionUseAbsoluteTimestamp: false,
            optionThemeTrueBlack: true,
            optionThemeTrueBlackTweetCards: true,
            optionTweetsShowSubscribeBadge: false,
          },
        ),
        child: MaterialApp(
          theme: ThemeData.dark(),
          locale: locale,
          onGenerateRoute: onGenerateRoute,
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
          home: Scaffold(
            body: SingleChildScrollView(
              child: TweetTile(key: ValueKey(post.idStr), clickable: true, tweet: post, threadConnectBottom: thread),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUp(() {
    timeago.setLocaleMessages('de', timeago.DeMessages());
    timeago.setLocaleMessages('de_short', timeago.DeShortMessages());
    compactDateLocales.addAll(['en', 'de']);
  });
  tearDown(compactDateLocales.clear);
  for (final thread in [false, true]) {
    testWidgets('long repost attribution adds only one line, thread=$thread', (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await _pump(tester, _post(), thread: thread);
      final plainHeight = tester.getSize(find.byType(TweetTile)).height;
      await _pump(tester, _post(repost: true), thread: thread);
      final repostHeight = tester.getSize(find.byType(TweetTile)).height;
      expect(repostHeight - plainHeight, lessThanOrEqualTo(32));
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('the compact credit exposes the full attribution on long press', (tester) async {
    await _pump(tester, _post(repost: true));
    await tester.longPress(find.byIcon(Icons.repeat).first);
    await tester.pumpAndSettle();
    expect(find.textContaining('$reposterName reposted'), findsOneWidget);
  });

  testWidgets('German attribution stays one line with large text on a narrow screen', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final semantics = tester.ensureSemantics();
    await _pump(tester, _post(repost: true), scale: 2, locale: const Locale('de'));
    expect(tester.getSize(find.byType(TweetRepostCredit)).height, lessThan(48));
    expect(find.bySemanticsLabel(RegExp('Retweet von $reposterName')), findsOneWidget);
    expect(tester.takeException(), isNull);
    semantics.dispose();
  });

  testWidgets('the author avatar still opens the original author of a repost', (tester) async {
    RouteSettings? destination;
    await _pump(
      tester,
      _post(repost: true),
      onGenerateRoute: (settings) {
        destination = settings;
        return MaterialPageRoute<void>(builder: (_) => const Scaffold(body: Text('Profile')));
      },
    );
    await tester.tap(find.descendant(of: find.byType(TweetHeader), matching: find.byType(InkResponse)));
    await tester.pumpAndSettle();
    expect(destination?.name, routeProfile);
    expect((destination!.arguments! as ProfileScreenArguments).screenName, 'author');
  });

  testWidgets('the post menu still opens the reposter, not the original author', (tester) async {
    RouteSettings? destination;
    await _pump(
      tester,
      _post(repost: true),
      onGenerateRoute: (settings) {
        destination = settings;
        return MaterialPageRoute<void>(builder: (_) => const Scaffold(body: Text('Profile')));
      },
    );
    await tester.tap(find.byIcon(Icons.more_horiz));
    await tester.pumpAndSettle();
    final credit = find.descendant(of: find.byType(BottomSheet), matching: find.textContaining(reposterName));
    expect(credit, findsOneWidget);
    await tester.ensureVisible(credit);
    await tester.tap(credit);
    await tester.pumpAndSettle();
    expect(destination?.name, routeProfile);
    expect((destination!.arguments! as ProfileScreenArguments).screenName, 'reposter');
    expect(find.byType(BottomSheet), findsNothing);
  });
}
