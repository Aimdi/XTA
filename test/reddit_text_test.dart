import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_text.dart';

void main() {
  group('redditTextParts', () {
    test('keeps ordinary words as one run', () {
      final parts = redditTextParts('hello there');
      expect(parts, hasLength(1));
      expect(parts.single.text, 'hello there');
      expect(parts.single.url, isNull);
    });

    test('a markdown link uses the label, not the URL', () {
      const raw =
          'Download>> [OTs-14](https://drive.google.com/drive/folders/abc?usp=sharing) [Source](https://x.com/GFL2EXILIUM_EN/status/2093171725546770459)';
      final parts = redditTextParts(raw);

      expect(parts.map((p) => p.text).toList(), [
        'Download>> ',
        'OTs-14',
        ' ',
        'Source',
      ]);
      expect(
        parts[1].url,
        'https://drive.google.com/drive/folders/abc?usp=sharing',
      );
      expect(
        parts[3].url,
        'https://x.com/GFL2EXILIUM_EN/status/2093171725546770459',
      );
    });

    test('the GirlsFrontline2 sticker comment keeps short labels', () {
      const raw =
          'Sticker Download>> [OTs-14](https://drive.google.com/drive/mobile/folders/1PoYmalZzYdWTWfP2xdD3xGsG0blUU-r?usp=sharing) [Source ](https://x.com/GFL2EXILIUM_EN/status/2093171725546770459)';
      final parts = redditTextParts(raw);

      expect(parts.map((p) => p.text).toList(), [
        'Sticker Download>> ',
        'OTs-14',
        ' ',
        'Source',
      ]);
      expect(parts[1].url, contains('drive.google.com/drive/mobile/folders/'));
      expect(parts[3].url, contains('x.com/GFL2EXILIUM_EN/status/'));
    });

    test('a bare Drive URL shrinks to the host', () {
      final parts = redditTextParts(
        'see https://drive.google.com/drive/folders/abc?usp=sharing',
      );
      expect(parts.last.text, 'drive.google.com');
      expect(parts.last.url, startsWith('https://drive.google.com/'));
    });

    test('a bare X status shrinks to x.com/handle', () {
      final parts = redditTextParts(
        'https://x.com/GFL2EXILIUM_EN/status/2093171725546770459',
      );
      expect(parts.single.text, 'x.com/GFL2EXILIUM_EN');
      expect(parts.single.isLink, isTrue);
    });
  });

  group('compactRedditLinkLabel', () {
    test('prefers a non-URL markdown label', () {
      expect(
        compactRedditLinkLabel('https://example.com/a', label: 'Source'),
        'Source',
      );
    });

    test('does not print a label that is itself the URL', () {
      expect(
        compactRedditLinkLabel(
          'https://example.com/story',
          label: 'https://example.com/story',
        ),
        'example.com/story',
      );
    });
  });

  testWidgets('paints Drive and X markdown as short blue labels', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: RedditRichText(
            text:
                'Sticker Download>> [OTs-14](https://drive.google.com/drive/mobile/folders/abc?usp=sharing) [Source ](https://x.com/GFL2EXILIUM_EN/status/2093171725546770459)',
          ),
        ),
      ),
    );

    expect(find.text('OTs-14'), findsOneWidget);
    expect(find.text('Source'), findsOneWidget);
    expect(find.textContaining('usp=sharing'), findsNothing);
    expect(find.textContaining('2093171725546770459'), findsNothing);
  });
}
