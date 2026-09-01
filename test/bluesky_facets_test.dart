import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_facets.dart';
import 'package:xta/utils/json.dart';

void main() {
  group('blueskyFacetsOf', () {
    test('reads link, mention and tag features with byte ranges', () {
      final facets = blueskyFacetsOf(
        Json({
          'facets': [
            {
              'index': {'byteStart': 0, 'byteEnd': 11},
              'features': [
                {
                  '\$type': 'app.bsky.richtext.facet#mention',
                  'did': 'did:plc:alice',
                },
              ],
            },
            {
              'index': {'byteStart': 20, 'byteEnd': 28},
              'features': [
                {'\$type': 'app.bsky.richtext.facet#tag', 'tag': 'bluesky'},
              ],
            },
            {
              'index': {'byteStart': 30, 'byteEnd': 42},
              'features': [
                {
                  '\$type': 'app.bsky.richtext.facet#link',
                  'uri': 'https://bsky.app',
                },
              ],
            },
          ],
        }),
      );

      expect(facets, hasLength(3));
      expect(facets[0].kind, BlueskyFacetKind.mention);
      expect(facets[0].value, 'did:plc:alice');
      expect(facets[1].kind, BlueskyFacetKind.tag);
      expect(facets[1].value, 'bluesky');
      expect(facets[2].kind, BlueskyFacetKind.link);
      expect(facets[2].value, 'https://bsky.app');
    });
  });

  group('blueskyRichTextSpans', () {
    test('splits ASCII text around a link facet', () {
      const text = 'see https://bsky.app now';
      // 'see ' = 4 bytes, URL = 16 bytes starting at 4
      final facets = [
        const BlueskyFacet(
          byteStart: 4,
          byteEnd: 20,
          kind: BlueskyFacetKind.link,
          value: 'https://bsky.app',
        ),
      ];
      final recognizers = <GestureRecognizer>[];
      final spans = blueskyRichTextSpans(
        text: text,
        facets: facets,
        style: const TextStyle(),
        linkStyle: const TextStyle(color: Color(0xFF0000FF)),
        onFacetTap: (_) {},
        recognizers: recognizers,
      );

      expect(spans, hasLength(3));
      expect((spans[0] as TextSpan).text, 'see ');
      expect((spans[1] as TextSpan).text, 'https://bsky.app');
      expect((spans[2] as TextSpan).text, ' now');
      expect(recognizers, hasLength(1));
      for (final r in recognizers) {
        r.dispose();
      }
    });

    test('utf-8 emoji before a facet still maps byte indices', () {
      const text = '👋 hi';
      // waving hand is 4 UTF-8 bytes, space, then "hi"
      final facets = [
        const BlueskyFacet(
          byteStart: 5,
          byteEnd: 7,
          kind: BlueskyFacetKind.tag,
          value: 'hi',
        ),
      ];
      final recognizers = <GestureRecognizer>[];
      final spans = blueskyRichTextSpans(
        text: text,
        facets: facets,
        style: const TextStyle(),
        linkStyle: const TextStyle(color: Color(0xFF0000FF)),
        onFacetTap: (_) {},
        recognizers: recognizers,
      );

      expect((spans[0] as TextSpan).text, '👋 ');
      expect((spans[1] as TextSpan).text, 'hi');
      for (final r in recognizers) {
        r.dispose();
      }
    });
  });
}
