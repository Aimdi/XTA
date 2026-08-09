import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_gallery_loader.dart';

/// A client whose answer per permalink can be scripted call by call.
class _ScriptedClient extends RedditClient {
  final Map<String, List<List<String>>> script;
  final Map<String, int> asked = {};

  _ScriptedClient(this.script);

  @override
  Future<List<String>> fetchGalleryImages(String permalink) async {
    final n = asked[permalink] = (asked[permalink] ?? 0) + 1;
    final answers = script[permalink] ?? const [[]];
    return answers[(n - 1).clamp(0, answers.length - 1)];
  }
}

const _two = ['https://i.redd.it/1.jpg', 'https://i.redd.it/2.jpg'];

void main() {
  group('remembering a gallery answer', () {
    test('a successful answer is fetched once and reused', () async {
      final client = _ScriptedClient({
        '/p/a': const [_two],
      });
      final loader = RedditGalleryLoader(client);

      await loader.images('/p/a');
      await loader.images('/p/a');

      expect(client.asked['/p/a'], 1);
    });

    // The bug: an empty answer — which is also what every refusal looks like —
    // was memoised as a completed future, so one rate-limited moment hid that
    // gallery for the rest of the app's life.
    test('a refusal is asked again next time, and can recover', () async {
      final client = _ScriptedClient({
        '/p/a': const [[], _two],
      });
      final loader = RedditGalleryLoader(client);

      expect(await loader.images('/p/a'), isEmpty);
      expect(await loader.images('/p/a'), _two);
      expect(client.asked['/p/a'], 2);
    });

    test('a genuine non-gallery is not hammered forever either', () async {
      final client = _ScriptedClient({
        '/p/plain': const [[]],
      });
      final loader = RedditGalleryLoader(client);

      for (var i = 0; i < 10; i++) {
        await loader.images('/p/plain');
      }

      // Bounded retries: more than once (so a refusal can recover), far fewer
      // than every scroll-past (so Reddit is not pelted per rebuild).
      expect(client.asked['/p/plain'], lessThanOrEqualTo(3));
    });

    test('what it holds is bounded, oldest first out', () async {
      final loader = RedditGalleryLoader(
        _ScriptedClient({
          for (var i = 0; i < 300; i++) '/p/$i': const [_two],
        }),
      );

      for (var i = 0; i < 300; i++) {
        await loader.images('/p/$i');
      }

      expect(loader.size, lessThanOrEqualTo(kRedditGalleryCacheCap));
    });
  });

  group('the gallery card in a recycled list', () {
    testWidgets('a card reused for a different post does not keep the old pictures', (tester) async {
      final client = _ScriptedClient({
        '/p/a': const [_two],
        '/p/b': const [[]],
      });
      final loader = RedditGalleryLoader(client);

      Widget card(String permalink) => MaterialApp(
        home: RedditGalleryImages(
          loader: loader,
          permalink: permalink,
          placeholder: const Text('link card'),
          whenLoaded: (images) => Text('album of ${images.length}'),
        ),
      );

      await tester.pumpWidget(card('/p/a'));
      await tester.pumpAndSettle();
      expect(find.text('album of 2'), findsOneWidget);

      // Same element, new permalink — exactly what a refresh does to a list
      // without keys. Post B is not a gallery, so showing A's album is lying.
      await tester.pumpWidget(card('/p/b'));
      await tester.pumpAndSettle();

      expect(find.text('album of 2'), findsNothing);
      expect(find.text('link card'), findsOneWidget);
    });
  });
}
