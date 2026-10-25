import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/main.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/tweet/focal_post.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/formatting_locale.dart';

TweetWithCard _post() => TweetWithCard()
  ..idStr = '1'
  ..fullText = 'Saluton, mondo'
  ..lang = 'eo'
  ..createdAt = DateTime.utc(2026, 10, 9, 14, 12)
  ..user = (User()
    ..idStr = '9'
    ..name = 'Aŭtoro'
    ..screenName = 'autoro')
  ..replyCount = 38
  ..retweetCount = 500
  ..favoriteCount = 9123
  ..viewCount = 77123;

final Map<String, Object> _prefs = {
  optionLocale: 'eo',
  optionShareBaseUrl: '',
  optionNonConfirmationBiasMode: false,
  optionTweetsShowSubscribeBadge: false,
  optionUseAbsoluteTimestamp: true,
  alwaysShowFullTweetContents: true,
  optionGestureDoubleTapLike: false,
  optionThemeTrueBlack: false,
  optionThemeTrueBlackTweetCards: false,
  optionZenMode: false,
  optionCalmMode: false,
  optionDefaultProfileTab: 'posts',
  optionSubscriptionOrderCustom: '',
};

/// [body] in an app set to Esperanto, which XTA translates but intl has no
/// number or date data for.
Future<void> _pumpInEsperanto(WidgetTester tester, Widget body) async {
  final prefs = PrefServiceCache(cache: _prefs);
  final groups = GroupsModel(prefs);
  final subscriptions = SubscriptionsModel(prefs, groups);
  final likes = LikedTweetModel();
  final saves = SavedTweetModel();
  addTearDown(() async {
    Intl.defaultLocale = null;
    await Future.wait([likes.destroy(), saves.destroy(), subscriptions.destroy(), groups.destroy()]);
  });

  await tester.pumpWidget(
    MultiProvider(
      providers: [
        Provider<LikedTweetModel>.value(value: likes),
        Provider<SavedTweetModel>.value(value: saves),
        Provider<GroupsModel>.value(value: groups),
        Provider<SubscriptionsModel>.value(value: subscriptions),
      ],
      child: PrefService(
        service: prefs,
        child: MaterialApp(
          locale: const Locale('eo'),
          localizationsDelegates: xtaLocalizationsDelegates,
          supportedLocales: L10n.delegate.supportedLocales,
          home: Scaffold(body: body),
        ),
      ),
    ),
  );
  await tester.pump();
}

void main() {
  group('verifiedFormattingLocale', () {
    // First in the file: no Localizations has loaded intl's date symbols yet.
    test('lets numbers decide alone until the date symbols are loaded', () {
      expect(() => DateFormat.localeExists('de'), throwsException);
      expect(verifiedFormattingLocale('de'), 'de');
      expect(verifiedFormattingLocale('eo'), 'en');
    });

    test('writes a language intl has no data for in English', () {
      expect(verifiedFormattingLocale('eo'), 'en');
      expect(() => NumberFormat.compact(locale: verifiedFormattingLocale('eo')).format(12345), returnsNormally);
    });

    test('takes a script or region variant to the language intl knows', () {
      expect(verifiedFormattingLocale('zh_Hans'), 'zh');
      expect(verifiedFormattingLocale('nb_NO'), 'nb');
    });

    testWidgets('every other app language keeps its own numbers and can write dates', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: xtaLocalizationsDelegates,
          supportedLocales: L10n.delegate.supportedLocales,
          home: const SizedBox(),
        ),
      );

      String numbers(String locale) => [
        NumberFormat.decimalPattern(locale).format(1234567.891),
        NumberFormat.compact(locale: locale).format(1234567),
        NumberFormat.percentPattern(locale).format(0.625),
      ].join(' | ');

      for (final locale in L10n.delegate.supportedLocales.where((locale) => locale.languageCode != 'eo')) {
        final name = locale.toString();
        final numbersOnly = Intl.verifiedLocale(name, NumberFormat.localeExists)!;
        final verified = verifiedFormattingLocale(name);
        expect(numbers(verified), numbers(numbersOnly), reason: '$name should keep the numbers it had');
        expect(() => DateFormat.yMMMd(verified).add_Hm().format(DateTime(2026, 10, 9)), returnsNormally);
      }
    });
  });

  group('in Esperanto', () {
    testWidgets('a feed post shows its counts and absolute date', (tester) async {
      await _pumpInEsperanto(tester, SingleChildScrollView(child: TweetTile(clickable: true, tweet: _post())));

      expect(tester.takeException(), isNull);
      expect(find.text('9.1K'), findsOneWidget);
      expect(find.textContaining('Oct 9, 2026'), findsOneWidget);
    });

    testWidgets("an opened post states its time, date and views", (tester) async {
      await _pumpInEsperanto(
        tester,
        SingleChildScrollView(
          child: FocalPostScope(
            id: '1',
            child: TweetTile(clickable: true, tweet: _post(), tweetOpened: true),
          ),
        ),
      );

      expect(tester.takeException(), isNull);
      final line = find.byType(TweetFocalMetaLine);
      final text = tester.widget<RichText>(find.descendant(of: line, matching: find.byType(RichText)));
      expect(text.text.toPlainText(), contains('Oct 9, 2026'));
      expect(text.text.toPlainText(), contains(L10n.current.post_views_count(1000, '77K')));
    });

    testWidgets('a profile header shows its counts and when the account joined', (tester) async {
      final user = UserWithExtra.fromArguments(
        idStr: '9',
        name: 'Aŭtoro',
        screenName: 'autoro',
        verified: false,
        followersCount: 12345,
        friendsCount: 678,
        createdAt: DateTime.utc(2012, 3, 4),
      );
      await _pumpInEsperanto(tester, ProfileScreenBody(profile: Profile(user, const []), defaultTabIndex: 0));

      expect(tester.takeException(), isNull);
      expect(find.text('12.3K ${L10n.current.followers}', findRichText: true), findsOneWidget);
      expect(find.text(L10n.current.joined('March 2012')), findsOneWidget);
      // The header's stores start their reads on a short debounce.
      await tester.pump(const Duration(seconds: 1));
    });
  });
}
