import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

void main() {
  testWidgets('standalone lists keep a short gutter', (tester) async {
    late EdgeInsets padding;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            padding = pluginFeedPadding(context);
            return const SizedBox.shrink();
          },
        ),
      ),
    );

    expect(padding.bottom, kPluginStandaloneGutter);
    expect(padding.top, 0);
  });

  testWidgets('embedded lists clear the floating home pill', (tester) async {
    late EdgeInsets padding;
    await tester.pumpWidget(
      MaterialApp(
        home: PluginEmbedded(
          child: Builder(
            builder: (context) {
              padding = pluginFeedPadding(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      ),
    );

    expect(padding.bottom, kPluginHomeNavClearance);
  });

  testWidgets('embedded lists do not reuse the requested outer controller', (
    tester,
  ) async {
    final outer = ScrollController();
    addTearDown(outer.dispose);
    late ScrollController? resolved;
    await tester.pumpWidget(
      MaterialApp(
        home: PluginEmbedded(
          child: Builder(
            builder: (context) {
              resolved = pluginInnerScrollController(context, outer);
              return const SizedBox.shrink();
            },
          ),
        ),
      ),
    );

    expect(identical(resolved, outer), isFalse);
  });
}
