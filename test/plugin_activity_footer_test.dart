import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_activity.dart';
import 'package:xta/ui/reader_failure.dart';

Future<void> _open(WidgetTester tester, Future<PluginActivityPage<String>> Function(String?) loader) async {
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      locale: const Locale('en'),
      home: Builder(
        builder: (context) => TextButton(
          onPressed: () => openPluginActivity<String>(
            context,
            title: 'Reposts',
            postUrl: 'https://example.social/post/1',
            loader: loader,
            idOf: (item) => item,
            itemBuilder: (context, item) => SizedBox(height: 400, child: Text(item)),
            errorLabel: (l10n, error) => 'Could not load',
          ),
          child: const Text('open'),
        ),
      ),
    ),
  );
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('a fully loaded list ends at its last item', (tester) async {
    await _open(tester, (cursor) async => const PluginActivityPage(['first', 'last']));
    await tester.drag(find.byType(ListView), const Offset(0, -2000));
    await tester.pumpAndSettle();
    // Before, an idle footer kept 48 px after the last item.
    expect(tester.getRect(find.text('last')).bottom, tester.getRect(find.byType(ListView)).bottom);
  });

  testWidgets('a page that failed with items shown is one compact row', (tester) async {
    await _open(tester, (cursor) async {
      if (cursor == null) return const PluginActivityPage(['first', 'last'], cursor: 'next');
      throw StateError('offline');
    });
    await tester.drag(find.byType(ListView), const Offset(0, -2000));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Load more'));
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView), const Offset(0, -2000));
    await tester.pumpAndSettle();
    expect(find.byType(ReaderFailureNotice), findsOneWidget);
    expect(tester.getSize(find.byType(ReaderFailureNotice)).height, 48);
  });
}
