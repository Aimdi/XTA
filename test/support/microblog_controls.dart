import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_home_dock.dart';

Future<void> openMicroblogFilters(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('home-plugin-options')));
  await tester.pumpAndSettle();
  final filters = find.byType(PluginDockFilterButton);
  await tester.ensureVisible(filters);
  await tester.pumpAndSettle();
  await tester.tap(filters);
  await tester.pumpAndSettle();
}

Future<void> closeMicroblogFilters(WidgetTester tester) async {
  final close = find.byTooltip('Close').last;
  await tester.ensureVisible(close);
  await tester.pumpAndSettle();
  await tester.tap(close);
  await tester.pumpAndSettle();
  final optionsClose = find.byKey(const ValueKey('home-plugin-options-close'));
  await tester.ensureVisible(optionsClose);
  await tester.pumpAndSettle();
  await tester.tap(optionsClose);
  await tester.pumpAndSettle();
}
