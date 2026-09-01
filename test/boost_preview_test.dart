import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/boost_run_carousel.dart';

void main() {
  group('boostPreviewText', () {
    test('reads the post rather than the entities X escaped it with', () {
      expect(boostPreviewText('silly femboy gooner &gt;,,&lt;'), 'silly femboy gooner >,,<');
    });

    test('unescapes ampersands too', () {
      expect(boostPreviewText('rock &amp; roll'), 'rock & roll');
    });

    test('no text at all is no preview', () {
      expect(boostPreviewText(null), '');
    });

    test('newlines become spaces so the peek stays one paragraph', () {
      expect(boostPreviewText('one\ntwo'), 'one two');
    });

    test('a long post is cut with an ellipsis', () {
      final preview = boostPreviewText('a' * 200);

      expect(preview, hasLength(78));
      expect(preview.endsWith('…'), isTrue);
    });

    test('a post that just fits is left whole', () {
      expect(boostPreviewText('b' * 80), 'b' * 80);
    });
  });
}
