import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';

Widget _german(Widget child) {
  return MaterialApp(
    locale: const Locale('de'),
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: SingleChildScrollView(child: child)),
  );
}

Future<void> _pump(WidgetTester tester, MastodonPost post) async {
  tester.view.physicalSize = const Size(320, 900);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    PrefService(
      service: PrefServiceCache(),
      child: _german(MastodonPostCard(post: post)),
    ),
  );
  await tester.pump();
}

void main() {
  timeago.setLocaleMessages('de', timeago.DeMessages());

  final post = MastodonPost(
    id: '1',
    acct: 'jemand@eine.instanz.example',
    authorName: 'Jemand',
    text: 'Der verborgene Text',
    url: 'https://eine.instanz.example/@jemand/1',
    publishedAt: DateTime.now().subtract(const Duration(hours: 3)),
    spoilerText: 'Politik',
  );

  testWidgets('a content warning is labelled and hides the post body', (
    tester,
  ) async {
    await _pump(tester, post);

    expect(find.text('Inhaltswarnung'), findsOneWidget);
    expect(find.text('Politik'), findsOneWidget);
    expect(find.text('Der verborgene Text'), findsNothing);
    expect(find.text('Anzeigen'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the warning stays visible once the body is shown', (
    tester,
  ) async {
    await _pump(tester, post);

    await tester.tap(find.text('Anzeigen'));
    await tester.pump();

    expect(find.text('Der verborgene Text'), findsOneWidget);
    expect(find.text('Inhaltswarnung'), findsOneWidget);
    expect(find.text('Politik'), findsOneWidget);
    expect(find.text('Ausblenden'), findsOneWidget);

    await tester.tap(find.text('Ausblenden'));
    await tester.pump();

    expect(find.text('Der verborgene Text'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a long warning in German does not overflow a 320dp phone', (
    tester,
  ) async {
    await _pump(
      tester,
      MastodonPost(
        id: '2',
        acct: 'jemand@eine.instanz.example',
        authorName: 'Jemand',
        text: 'Text',
        url: 'https://eine.instanz.example/@jemand/2',
        publishedAt: DateTime.now(),
        spoilerText:
            'Eine außergewöhnlich ausführliche Inhaltswarnung über ein '
            'schwieriges Thema, die über mehrere Zeilen laufen muss',
      ),
    );

    expect(tester.takeException(), isNull);
  });
}
