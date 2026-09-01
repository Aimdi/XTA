import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/ui/provenance_accent.dart';

void main() {
  group('provenanceAccentColor', () {
    testWidgets('maps known plugin ids to their brand colours', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) {
              expect(provenanceAccentColor(context, pluginIdReddit), const Color(0xFFFF4500));
              expect(provenanceAccentColor(context, pluginIdSubstack), const Color(0xFFFF6719));
              expect(provenanceAccentColor(context, pluginIdBluesky), const Color(0xFF0085FF));
              expect(provenanceAccentColor(context, pluginIdMastodon), const Color(0xFF6364FF));
              return const SizedBox.shrink();
            },
          ),
        ),
      );
    });

    testWidgets('provenanceAccent lays out in unbounded height without crashing', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => ListView(
              children: [
                provenanceAccent(
                  context: context,
                  color: Colors.red,
                  child: const SizedBox(height: 80, child: Text('interleaved card')),
                ),
              ],
            ),
          ),
        ),
      );

      expect(find.text('interleaved card'), findsOneWidget);
    });

    testWidgets('lightens near-black Threads brand on dark theme', (tester) async {
      late Color threadsColor;
      await tester.pumpWidget(
        MaterialApp(
          theme: ThemeData.dark(),
          home: Builder(
            builder: (context) {
              threadsColor = provenanceAccentColor(context, pluginIdThreads);
              return const SizedBox.shrink();
            },
          ),
        ),
      );

      expect(threadsColor, isNot(const Color(0xFF101010)));
      expect(threadsColor.computeLuminance(), greaterThan(0.12));
    });
  });
}
