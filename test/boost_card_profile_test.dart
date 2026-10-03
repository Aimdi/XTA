import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/tweet/boost_run_carousel.dart';

TweetChain _repost(String id) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..user = (User()
      ..screenName = 'alice'
      ..name = 'Alice')
    ..retweetedStatusWithCard = (TweetWithCard()
      ..idStr = 'original-$id'
      ..text = 'the reposted post $id'
      ..user = (User()
        ..idStr = '42'
        ..screenName = 'carol'
        ..name = 'Carol'));
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

void main() {
  Future<List<RouteSettings>> pump(WidgetTester tester) async {
    final pushed = <RouteSettings>[];
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        onGenerateRoute: (settings) {
          pushed.add(settings);
          return MaterialPageRoute(builder: (_) => const SizedBox.shrink(), settings: settings);
        },
        home: Scaffold(
          body: BoostRunCarousel(chains: [_repost('1'), _repost('2')], username: 'alice'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    pushed.clear();
    return pushed;
  }

  testWidgets('a repost card names whose post it is and opens that profile', (tester) async {
    final pushed = await pump(tester);

    expect(find.text('Carol'), findsNWidgets(2));
    expect(find.text('Alice'), findsNothing);

    await tester.tap(find.text('Carol').first);
    await tester.pumpAndSettle();

    expect(pushed.single.name, routeProfile);
    final arguments = pushed.single.arguments as ProfileScreenArguments;
    expect(arguments.screenName, 'carol');
    expect(arguments.id, '42');
  });

  testWidgets('a tap on the post text still opens the post', (tester) async {
    final pushed = await pump(tester);

    await tester.tap(find.text('the reposted post 1'));
    await tester.pumpAndSettle();

    expect(pushed.single.name, routeStatus);
  });
}
