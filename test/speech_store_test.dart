import 'package:flutter_test/flutter_test.dart';
import 'package:xta/speech/speech_store.dart';

void main() {
  group('splitting an article into utterances', () {
    test('short text is one utterance', () {
      expect(chunkForSpeech('A short article.'), ['A short article.']);
    });

    test('nothing to read is nothing to say', () {
      expect(chunkForSpeech(''), isEmpty);
      expect(chunkForSpeech('   \n  '), isEmpty);
    });

    test('long text is broken at sentence ends, never mid-word', () {
      final text = List.filled(60, 'This is a sentence of a reasonable length.').join(' ');
      final chunks = chunkForSpeech(text, maxChars: 200);

      expect(chunks.length, greaterThan(1));
      for (final chunk in chunks) {
        expect(chunk.length, lessThanOrEqualTo(200));
        expect(chunk.trim(), chunk, reason: 'a chunk should not start or end on a space');
        expect(chunk, endsWith('.'), reason: 'the break lands after a sentence, not inside a word');
      }
    });

    test('every word survives the split', () {
      final text = List.generate(200, (i) => 'Word$i is here.').join(' ');
      final chunks = chunkForSpeech(text, maxChars: 300);

      expect(chunks.join(' '), text);
    });

    test('one sentence longer than the limit is cut where it has to be', () {
      final chunks = chunkForSpeech('a' * 500, maxChars: 200);

      expect(chunks, hasLength(3));
      expect(chunks.join(), 'a' * 500);
    });
  });

  group('starting Vorlesen from a held paragraph', () {
    const article = 'Title\n\nAuthor\n\nFirst paragraph is here.\n\nSecond paragraph follows.';

    test('starts at the held paragraph and keeps the rest', () {
      expect(
        textFromHere(article, 'Second paragraph follows.'),
        'Second paragraph follows.',
      );
    });

    test('starts at a needle in the middle of the article', () {
      expect(
        textFromHere(article, 'First paragraph is here.'),
        'First paragraph is here.\n\nSecond paragraph follows.',
      );
    });

    test('uses the remainder the web view sent when the article does not match', () {
      expect(
        textFromHere(article, 'A paragraph only in the page'),
        'A paragraph only in the page',
      );
    });

    test('empty needle keeps the whole article', () {
      expect(textFromHere(article, '  '), article);
    });
  });

  group('what the bar is told', () {
    test('nothing is playing to begin with', () {
      expect(SpeechPlayback.idle.speaking, isFalse);
      expect(SpeechPlayback.idle.title, isNull);
    });

    test('two readings of the same article are the same state', () {
      expect(
        const SpeechPlayback(title: 'A post', speaking: true),
        const SpeechPlayback(title: 'A post', speaking: true),
      );
      expect(
        const SpeechPlayback(title: 'A post', speaking: true),
        isNot(const SpeechPlayback(title: 'Another post', speaking: true)),
      );
    });
  });
}
