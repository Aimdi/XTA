import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/threads/threads_rich_text.dart';

void main() {
  group('hitsInThreadsCaption', () {
    test('finds @handles and http(s) URLs', () {
      final hits = hitsInThreadsCaption('Hi @Alice and https://example.com/x, bye');

      expect(hits, hasLength(2));
      expect(hits[0].isMention, isTrue);
      expect(hits[0].value, 'Alice');
      expect(hits[1].isMention, isFalse);
      expect(hits[1].value, 'https://example.com/x');
    });

    test('ignores email-shaped text as a mention', () {
      final hits = hitsInThreadsCaption('write me@example.com please');

      expect(hits.where((h) => h.isMention), isEmpty);
    });
  });

  test('trimThreadsCaptionUrl drops trailing punctuation', () {
    expect(trimThreadsCaptionUrl('https://a.co/x.'), 'https://a.co/x');
    expect(trimThreadsCaptionUrl('https://a.co/x)'), 'https://a.co/x');
  });
}
