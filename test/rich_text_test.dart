import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/utils/rich_text.dart';

/// The parts a fixture builds, using a real context so theming and navigation
/// wiring are the ones the app uses.
Future<List<RichTextPart>> _build(WidgetTester tester, String text, Object? entities) async {
  late List<RichTextPart> parts;
  await tester.pumpWidget(MaterialApp(
    home: Builder(builder: (context) {
      parts = buildRichText(context, text, entities);
      return const SizedBox.shrink();
    }),
  ));
  return parts;
}

String _textOf(RichTextPart part) {
  if (part.plainText != null) {
    return part.plainText!;
  }
  final span = part.entity;
  if (span is TextSpan) {
    return span.text ?? '';
  }
  if (span is WidgetSpan) {
    return _widgetSpanText(span);
  }
  return '';
}

String _widgetSpanText(WidgetSpan span) {
  final child = span.child;
  if (child is Text) {
    return child.data ?? '';
  }
  if (child is GestureDetector && child.child is Text) {
    return (child.child as Text).data ?? '';
  }
  return '';
}

void _expectTappableAccentSpan(RichTextPart part) {
  final span = part.entity;
  if (span is TextSpan) {
    expect(span.recognizer, isA<TapGestureRecognizer>());
    expect(span.style?.color, isNot(Colors.blue));
    return;
  }
  if (span is WidgetSpan) {
    final detector = span.child;
    expect(detector, isA<GestureDetector>());
    final text = (detector as GestureDetector).child as Text;
    expect((detector).onTap, isNotNull);
    expect(text.style?.color, isNot(Colors.blue));
    return;
  }
  fail('expected a tappable span');
}

void main() {
  group('a post with entities', () {
    const text = r'Hi @bob check https://t.co/abc &amp; more #tag';
    final entities = Entities.fromJson({
      'user_mentions': [
        {'screen_name': 'bob', 'id_str': '1', 'indices': [3, 7]},
      ],
      'urls': [
        {
          'url': 'https://t.co/abc',
          'display_url': 'example.com',
          'expanded_url': 'https://example.com/x',
          'indices': [14, 30],
        },
      ],
      'hashtags': [
        {'text': 'tag', 'indices': [42, 46]},
      ],
    });

    testWidgets('lands every run where X placed it', (tester) async {
      final parts = await _build(tester, text, entities);

      expect(parts.map(_textOf), ['Hi ', '@bob', ' check ', 'example.com', ' & more ', '#tag']);
    });

    testWidgets('the text between entities is unescaped', (tester) async {
      final parts = await _build(tester, text, entities);

      expect(parts[4].plainText, ' & more ', reason: '&amp; is markup, not words anyone wrote');
    });

    testWidgets('every entity span is tappable and wears the theme accent, not blue', (tester) async {
      final parts = await _build(tester, text, entities);
      final spans = parts.where((p) => p.entity != null);

      for (final part in spans) {
        _expectTappableAccentSpan(part);
      }
    });
  });

  group('what descriptions carry without entities', () {
    testWidgets('mentions and hashtags in plain text become spans', (tester) async {
      final parts = await _build(tester, 'by @ann and #cool stuff', null);

      expect(parts.map(_textOf), ['by ', '@ann', ' and ', '#cool', ' stuff']);
      expect(parts[1].entity, isNotNull);
      expect(parts[3].entity, isNotNull);
    });

    testWidgets('an email address is not a mention', (tester) async {
      final parts = await _build(tester, 'mail me@example.com now', null);

      expect(parts.every((p) => p.entity == null), isTrue,
          reason: 'the @ sits mid-word, which the lookbehind rejects');
    });
  });

  group('rune indices', () {
    testWidgets('an emoji before an entity does not shift it', (tester) async {
      // Two emoji are two runes but four UTF-16 units; X indexes by rune.
      final parts = await _build(
        tester,
        '🎉🎉 @bob hi',
        Entities.fromJson({
          'user_mentions': [
            {'screen_name': 'bob', 'id_str': '1', 'indices': [3, 7]},
          ],
        }),
      );

      expect(parts.map(_textOf), ['🎉🎉 ', '@bob', ' hi']);
    });
  });

  group('media in the text', () {
    testWidgets('the t.co link a picture leaves in the text renders as nothing', (tester) async {
      final parts = await _build(
        tester,
        'look https://t.co/pic',
        Entities.fromJson({
          'media': [
            {'id_str': '9', 'media_url_https': 'https://pbs.example/x.jpg', 'indices': [5, 21]},
          ],
        }),
      );

      expect(parts.map(_textOf), ['look ', '']);
    });
  });

  group('letting the recognizers go', () {
    testWidgets('a parts list disposes once and tolerates twice', (tester) async {
      final parts = await _build(tester, 'just @one mention', null);

      disposeRichTextParts(parts);
      disposeRichTextParts(parts);
      disposeRichTextParts(<RichTextPart>[]);
    });
  });
}
