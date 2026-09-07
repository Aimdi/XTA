import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_timeline_picker.dart';
import 'package:xta/ui/x_look_theme.dart';

void main() {
  for (final large in [false, true]) {
    testWidgets('Source picker reaches many long names and keeps Add visible (large RTL $large)', (tester) async {
      tester.view.physicalSize = const Size(320, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final semantics = tester.ensureSemantics();
      try {
        HomeTimelineSelection? result;
        await tester.pumpWidget(
          MaterialApp(
            theme: xLookLightsOutTheme(null),
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(large ? 2 : 1)),
              child: Directionality(textDirection: large ? TextDirection.rtl : TextDirection.ltr, child: child!),
            ),
            home: Scaffold(
              body: Builder(
                builder: (context) => TextButton(
                  onPressed: () async {
                    result = await showModalBottomSheet<HomeTimelineSelection>(
                      context: context,
                      isScrollControlled: true,
                      useSafeArea: true,
                      showDragHandle: true,
                      builder: (_) => HomeTimelinePicker(
                        selected: 'feed-0',
                        options: [
                          for (var i = 0; i < 18; i++)
                            HomeTimelineOption(
                              id: 'feed-$i',
                              label: 'Pinned network $i with a long translated title',
                              mark: const Icon(Icons.rss_feed),
                              plugin: true,
                              unread: i == 0,
                            ),
                        ],
                      ),
                    );
                  },
                  child: const Text('Open fixture'),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Open fixture'));
        await tester.pumpAndSettle();
        expect(find.bySemanticsLabel(RegExp(RegExp.escape(L10n.current.group_has_unread))), findsWidgets);
        expect(find.byKey(const ValueKey('home-add-timeline')).hitTestable(), findsOneWidget);
        final last = find.byKey(const ValueKey('home-source-feed-17'));
        await tester.scrollUntilVisible(
          last,
          300,
          scrollable: find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)),
        );
        await tester.pumpAndSettle();
        expect(last.hitTestable(), findsOneWidget);
        expect(find.byKey(const ValueKey('home-add-timeline')).hitTestable(), findsOneWidget);
        expect(tester.takeException(), isNull);
        await tester.tap(last);
        await tester.pumpAndSettle();
        expect(result?.id, 'feed-17');
        expect(find.byKey(const ValueKey('home-source-sheet')), findsNothing);
      } finally {
        semantics.dispose();
      }
    });
  }
}
