import 'package:dart_twitter_api/twitter_api.dart' show Url, UserEntities, UserEntityUrl;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/main.dart' show xtaLocalizationsDelegates;
import 'package:xta/profile/profile.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/user.dart' show UserWithExtra;

/// Three short paragraphs a blank line apart, two of them with a link.
const profileFixtureBio =
    'Reads everything twice.\n\nNotes: https://t.co/notes\n\nImpressum & Datenschutz: https://t.co/legal';

Url _link(String text, String url, String shown) {
  final start = text.indexOf(url);
  return Url()
    ..url = url
    ..displayUrl = shown
    ..expandedUrl = 'https://$shown'
    ..indices = [start, start + url.length];
}

UserWithExtra profileFixtureUser() => UserWithExtra.fromArguments(
  idStr: '42',
  name: 'Mara Lindqvist',
  screenName: 'maralind',
  verified: true,
  description: profileFixtureBio,
  url: 'https://t.co/site',
  entities: UserEntities()
    ..url = (UserEntityUrl()..urls = [_link('https://t.co/site', 'https://t.co/site', 'maralind.substack.com')])
    ..description = (UserEntityUrl()
      ..urls = [
        _link(profileFixtureBio, 'https://t.co/notes', 'example.com'),
        _link(profileFixtureBio, 'https://t.co/legal', 'example.com/legal'),
      ]),
  followersCount: 62646,
  friendsCount: 1379,
  createdAt: DateTime.utc(2024, 8, 3),
);

/// Inter and Material's icons, so text measures and draws as on a phone; the
/// test font draws every glyph 1 em wide.
Future<void> loadProfileFonts() async {
  for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
  }
  await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
}

class _People extends SubscriptionsModel {
  _People(super.prefs, super.groupModel, {required bool subscribed, required bool inFeed}) {
    if (subscribed) {
      update([
        UserSubscription(
          id: '42',
          screenName: 'maralind',
          name: 'Mara Lindqvist',
          profileImageUrlHttps: null,
          verified: true,
          inFeed: inFeed,
          createdAt: DateTime.utc(2025),
        ),
      ]);
    }
  }
}

/// The fixture user's X profile, as true black with a violet accent, [width]
/// wide with text at [scale]; [subscribed] and [inFeed] are the local follow.
Future<void> pumpXProfile(
  WidgetTester tester, {
  Locale locale = const Locale('en'),
  double width = 390,
  double scale = 1,
  bool subscribed = false,
  bool inFeed = true,
}) async {
  tester.view.physicalSize = Size(width, 844);
  tester.view.devicePixelRatio = 1;
  tester.view.padding = const FakeViewPadding(top: 24);
  addTearDown(tester.view.reset);
  final prefs = PrefServiceCache(
    cache: {
      optionLocale: locale.languageCode,
      optionShareBaseUrl: '',
      optionNonConfirmationBiasMode: false,
      optionTweetsShowSubscribeBadge: false,
      optionUseAbsoluteTimestamp: true,
      alwaysShowFullTweetContents: true,
      optionGestureDoubleTapLike: false,
      optionThemeTrueBlack: true,
      optionThemeTrueBlackTweetCards: false,
      optionZenMode: false,
      optionCalmMode: false,
      optionDisableAnimations: true,
      optionDefaultProfileTab: 'posts',
      optionSubscriptionOrderCustom: '',
    },
  );
  final groups = GroupsModel(prefs);
  final people = _People(prefs, groups, subscribed: subscribed, inFeed: inFeed);
  final likes = LikedTweetModel();
  final saves = SavedTweetModel();
  addTearDown(() => Future.wait([likes.destroy(), saves.destroy(), people.destroy(), groups.destroy()]));
  await tester.pumpWidget(
    MultiProvider(
      providers: [
        Provider<LikedTweetModel>.value(value: likes),
        Provider<SavedTweetModel>.value(value: saves),
        Provider<GroupsModel>.value(value: groups),
        Provider<SubscriptionsModel>.value(value: people),
      ],
      child: PrefService(
        service: prefs,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: xLookThemeData(XLookTokens.lightsOut.copyWith(accent: const Color(0xFF8B7CF6)), null),
          locale: locale,
          localizationsDelegates: xtaLocalizationsDelegates,
          supportedLocales: L10n.delegate.supportedLocales,
          builder: (context, app) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
            child: app!,
          ),
          home: RepaintBoundary(
            key: const ValueKey('profile-shot'),
            child: Scaffold(
              body: ProfileScreenBody(profile: Profile(profileFixtureUser(), const []), defaultTabIndex: 0),
            ),
          ),
        ),
      ),
    ),
  );
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

/// Lets the header's stores start their debounced reads before the test ends.
Future<void> settleProfile(WidgetTester tester) => tester.pump(const Duration(seconds: 1));
