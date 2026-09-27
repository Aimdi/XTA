import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/ui/reader_swipe_navigation.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/saved/saved_source_filter.dart';

Widget _localized(Widget child) => MaterialApp(
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: Scaffold(
    body: Center(child: SizedBox(width: 500, height: 80, child: child)),
  ),
);

void main() {
  testWidgets('Saved source filter swipes retain the source menu', (tester) async {
    final source = ValueNotifier(SavedSource.all);
    addTearDown(source.dispose);
    await tester.pumpWidget(
      _localized(
        ValueListenableBuilder<SavedSource>(
          valueListenable: source,
          builder: (_, value, _) => SavedSourceButton(selected: value, onSelected: (next) => source.value = next),
        ),
      ),
    );
    await tester.drag(find.byType(SavedSourceButton), const Offset(-120, 0));
    await tester.pumpAndSettle();
    expect(source.value, SavedSource.x);
    await tester.tap(find.byKey(const ValueKey('saved-source-filter')));
    await tester.pumpAndSettle();
    await tester.tap(
      find.ancestor(
        of: find.text(L10n.current.plugin_reddit_title),
        matching: find.byType(CheckedPopupMenuItem<SavedSource>),
      ),
    );
    await tester.pumpAndSettle();
    expect(source.value, SavedSource.reddit);
  });

  testWidgets('profile section swipes keep segmented buttons accessible', (tester) async {
    final section = ValueNotifier(PluginProfileFeedTab.posts);
    addTearDown(section.dispose);
    await tester.pumpWidget(
      _localized(
        ValueListenableBuilder<PluginProfileFeedTab>(
          valueListenable: section,
          builder: (_, value, _) => PluginProfileTabBar(selected: value, onSelected: (next) => section.value = next),
        ),
      ),
    );
    await tester.drag(find.byType(PluginProfileTabBar), const Offset(-120, 0));
    await tester.pumpAndSettle();
    expect(section.value, PluginProfileFeedTab.replies);
    await tester.tap(find.text(L10n.current.media));
    await tester.pumpAndSettle();
    expect(section.value, PluginProfileFeedTab.media);
  });

  testWidgets('standalone lazy readers swipe once without building inactive panes', (tester) async {
    final selected = ValueNotifier(0);
    addTearDown(selected.dispose);
    final built = <int>[];
    final changes = <int>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ValueListenableBuilder<int>(
            valueListenable: selected,
            builder: (context, value, _) => PluginLazyTabs(
              index: value,
              onSelected: (index) {
                changes.add(index);
                selected.value = index;
              },
              children: [
                for (var i = 0; i < 3; i++)
                  (_) {
                    built.add(i);
                    return SizedBox.expand(child: Center(child: Text('Pane $i')));
                  },
              ],
            ),
          ),
        ),
      ),
    );
    expect(built, [0]);
    await tester.drag(find.byType(PluginLazyTabs), const Offset(-120, 0));
    await tester.pumpAndSettle();
    expect(changes, [1]);
    expect(find.text('Pane 1'), findsOneWidget);
    expect(find.text('Pane 0', skipOffstage: false), findsNothing);
    expect(built.contains(2), isFalse);
  });

  testWidgets('embedded lazy readers leave body swipes to Home source navigation', (tester) async {
    final sources = <int>[];
    final sections = <int>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ReaderSwipeNavigation(
            index: 0,
            count: 3,
            identity: 'home',
            onChanged: (index) {
              sources.add(index);
              return true;
            },
            child: PluginEmbedded(
              child: PluginLazyTabs(
                index: 0,
                onSelected: sections.add,
                children: [for (var i = 0; i < 3; i++) (_) => SizedBox.expand(child: Text('Pane $i'))],
              ),
            ),
          ),
        ),
      ),
    );
    await tester.drag(find.byType(PluginLazyTabs), const Offset(-120, 0));
    await tester.pumpAndSettle();
    expect(sources, [1]);
    expect(sections, isEmpty);
  });

  testWidgets('repeated header swipes before selection rebuild are accepted once', (tester) async {
    final selected = ValueNotifier(0);
    addTearDown(selected.dispose);
    final changes = <int>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 300,
              height: 52,
              child: ValueListenableBuilder<int>(
                valueListenable: selected,
                builder: (context, value, _) => PluginCompactTabs(
                  tabs: [
                    for (var i = 0; i < 3; i++)
                      PluginHomeTab(
                        icon: Icons.circle,
                        label: 'Tab $i',
                        selected: i == value,
                        onTap: () {
                          changes.add(i);
                          selected.value = i;
                        },
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
    final center = tester.getCenter(find.byType(PluginCompactTabs));
    for (var i = 0; i < 2; i++) {
      final gesture = await tester.startGesture(center, pointer: 50 + i);
      await gesture.moveBy(const Offset(-60, 0));
      await gesture.moveBy(const Offset(-60, 0));
      await gesture.up();
    }
    await tester.pumpAndSettle();
    expect(changes, [1]);
  });

  for (final compact in [true, false]) {
    testWidgets('plugin ${compact ? "icons" : "picker"} switches on release and retains tap', (tester) async {
      final selected = ValueNotifier(0);
      addTearDown(selected.dispose);
      final changes = <int>[];
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 280,
                height: 52,
                child: ValueListenableBuilder<int>(
                  valueListenable: selected,
                  builder: (context, value, _) {
                    final tabs = [
                      for (var i = 0; i < 3; i++)
                        PluginHomeTab(
                          icon: Icons.circle,
                          label: ['Posts', 'Media', 'Saved'][i],
                          selected: value == i,
                          onTap: () {
                            changes.add(i);
                            selected.value = i;
                          },
                        ),
                    ];
                    return compact ? PluginCompactTabs(tabs: tabs) : PluginSectionPicker(tabs: tabs);
                  },
                ),
              ),
            ),
          ),
        ),
      );
      final region = find.byType(compact ? PluginCompactTabs : PluginSectionPicker);
      final gesture = await tester.startGesture(tester.getCenter(region));
      await gesture.moveBy(const Offset(-120, 0));
      await tester.pump();
      expect(changes, isEmpty);
      await gesture.up();
      await tester.pumpAndSettle();
      expect(changes, [1]);
      if (compact) {
        await tester.tap(find.byTooltip('Saved'));
      } else {
        await tester.tap(region);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Saved'));
      }
      await tester.pumpAndSettle();
      expect(changes, [1, 2]);
    });
  }
}
