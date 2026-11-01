import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/threads/threads_models.dart';
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

    test('finds #tags in plain text, not inside a link or a word', () {
      final hits = hitsInThreadsCaption('Glaze day #Keramik #töpfern, see https://example.com/a#notes and C#7');
      final tags = [
        for (final hit in hits)
          if (hit.kind == ThreadsFragmentKind.tag) hit.value,
      ];
      expect(tags, ['Keramik', 'töpfern']);

      final runs = threadsCaptionRuns('see https://example.com/a#notes', const []);
      expect(runs.where((run) => run.kind == ThreadsFragmentKind.tag), isEmpty, reason: 'the # is part of the link');
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
