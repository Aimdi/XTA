import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/tweet/tweet_header.dart';
import 'package:xta/ui/verified_badges.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/user_verification.dart';

const _spaceX = Affiliation(
  badgeUrl: 'https://pbs.twimg.com/profile_images/1/spacex_bigger.jpg',
  name: 'SpaceX',
  profileUrl: 'https://twitter.com/SpaceX',
  labelType: 'BusinessLabel',
);

Future<List<ProfileScreenArguments>> _pumpHeader(
  WidgetTester tester,
  UserVerification verification, {
  String displayName = 'Display Name',
  double textScale = 1,
}) async {
  final opened = <ProfileScreenArguments>[];
  await tester.pumpWidget(
    MaterialApp(
      theme: xLookLightTheme(null),
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      onGenerateRoute: (settings) {
        if (settings.name == routeProfile) {
          opened.add(settings.arguments as ProfileScreenArguments);
        }
        return MaterialPageRoute(builder: (_) => const SizedBox());
      },
      home: MediaQuery(
        data: MediaQueryData(textScaler: TextScaler.linear(textScale)),
        child: Scaffold(
          body: TweetHeader(
            avatar: const ColoredBox(color: Colors.red),
            onOpenProfile: () {},
            displayName: displayName,
            handle: 'handle',
            verified: verification.type != VerifiedType.none,
            verification: verification,
          ),
        ),
      ),
    ),
  );
  return opened;
}

Color? _checkColor(WidgetTester tester) => tester.widget<Icon>(find.byIcon(Icons.verified)).color;

void main() {
  testWidgets('blue, gold and grey checks', (tester) async {
    final expected = {
      VerifiedType.blue: xLookLightTheme(null).colorScheme.primary,
      VerifiedType.business: const Color(0xFFE2B719),
      VerifiedType.government: const Color(0xFF829AAB),
    };
    for (final MapEntry(key: type, value: color) in expected.entries) {
      await _pumpHeader(tester, UserVerification(type));
      expect(_checkColor(tester), color, reason: '$type');
    }
  });

  testWidgets('checks carry their own semantics labels', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pumpHeader(tester, const UserVerification(VerifiedType.business));
    expect(find.bySemanticsLabel('Verified organization'), findsOneWidget);
    await _pumpHeader(tester, const UserVerification(VerifiedType.government));
    expect(find.bySemanticsLabel('Verified government account'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('no check and no affiliation draws nothing after the name', (tester) async {
    await _pumpHeader(tester, UserVerification.none);
    expect(find.byIcon(Icons.verified), findsNothing);
    expect(find.byType(AffiliationBadge), findsNothing);
  });

  testWidgets('affiliation badge follows the check, labelled and openable', (tester) async {
    final semantics = tester.ensureSemantics();
    final opened = await _pumpHeader(tester, const UserVerification(VerifiedType.business, _spaceX));

    final check = tester.getRect(find.byIcon(Icons.verified));
    final badge = tester.getRect(find.byType(AffiliationBadge));
    expect(badge.left, greaterThan(check.right));
    expect(badge.size, const Size.square(16));
    expect(find.bySemanticsLabel('Affiliated with SpaceX'), findsOneWidget);

    await tester.tap(find.byType(AffiliationBadge));
    await tester.pump();
    expect(opened.single.screenName, 'SpaceX');
    semantics.dispose();
  });

  testWidgets('an affiliation outside X is shown but opens nothing', (tester) async {
    const elsewhere = Affiliation(
      badgeUrl: 'https://pbs.twimg.com/b.jpg',
      name: 'Org',
      profileUrl: 'https://org.example',
    );
    final opened = await _pumpHeader(tester, const UserVerification(VerifiedType.none, elsewhere));
    await tester.tap(find.byType(AffiliationBadge));
    await tester.pump();
    expect(opened, isEmpty);
  });

  testWidgets('a long name ellipsizes while the badges keep their size', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await _pumpHeader(
      tester,
      const UserVerification(VerifiedType.government, _spaceX),
      displayName: 'An extraordinarily long display name that cannot possibly fit on one line',
      textScale: 1.2,
    );
    expect(tester.takeException(), isNull);
    expect(tester.getSize(find.byIcon(Icons.verified)), Size.square(16 * 1.2));
    expect(tester.getSize(find.byType(AffiliationBadge)), Size.square(16 * 1.2));
  });
}
