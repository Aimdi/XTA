import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/alt_microblogging_mark.dart';
import 'package:xta/plugins/plugin_marks.dart';

Widget _app(Widget child) => MaterialApp(
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: Scaffold(body: Center(child: child)),
);

void main() {
  testWidgets('Bluesky sits top left and Mastodon bottom right, over it', (tester) async {
    await tester.pumpWidget(_app(const AltMicrobloggingMark(size: 48)));

    final marks = tester.widgetList<PluginBrandMark>(find.byType(PluginBrandMark)).toList();
    expect(marks.map((mark) => mark.plugin.id), [pluginIdBluesky, pluginIdMastodon]);
    final box = tester.getRect(find.byType(AltMicrobloggingMark));
    final bluesky = tester.getRect(find.byWidget(marks.first));
    final mastodon = tester.getRect(find.byWidget(marks.last));
    expect(bluesky.topLeft, box.topLeft);
    expect(mastodon.bottomRight, box.bottomRight);
    expect(bluesky.overlaps(mastodon), isTrue);
  });

  testWidgets('the section is named after Mastodon and Bluesky only', (tester) async {
    await tester.pumpWidget(_app(Builder(builder: (context) => Text(L10n.of(context).alt_microblogging))));

    expect(find.text('Mastodon & Bluesky'), findsOneWidget);
  });
}
