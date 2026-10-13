import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/downloads/download_destination.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_download.dart';
import 'package:xta/plugins/booru/booru_models.dart';

BooruPost _post(
  String id, {
  String? md5,
  Map<String, BooruTagCategory> kinds = const {},
  String file = 'https://cdn.donmai.us/original/ab/cd/abcd.png',
}) => BooruPost(
  id: id,
  host: 'https://danbooru.donmai.us',
  engine: 'danbooru',
  tags: const ['kantoku', 'hatsune_miku'],
  rating: BooruRating.sensitive,
  score: 1,
  width: 1,
  height: 1,
  previewUrl: null,
  sampleUrl: null,
  fileUrl: file,
  fileExt: 'png',
  source: null,
  createdAt: null,
  md5: md5,
  tagCategories: kinds,
);

/// Records what it was asked to download instead of queueing it.
class _Downloader extends BooruDownloader {
  final asked = <List<BooruPost>>[];

  @override
  Future<({int queued, int skipped})?> downloadAll(BasePrefService prefs, List<BooruPost> posts) async {
    asked.add(posts);
    return (queued: posts.length - 1, skipped: 1);
  }
}

void main() {
  test('an empty template keeps the name Booru downloads had before', () {
    expect(booruDownloadFileName(_post('1'), ''), 'booru-abcd.png');
  });

  test('a template names the file from the post, keeping its extension', () {
    final post = _post(
      '42',
      md5: 'abcd',
      kinds: const {'kantoku': BooruTagCategory.artist, 'hatsune_miku': BooruTagCategory.character},
    );

    expect(
      booruDownloadFileName(post, '{site}_{id}_{artist}_{character}_{rating}'),
      'danbooru.donmai.us_42_kantoku_hatsune_miku_s.png',
    );
    expect(booruDownloadFileName(post, '{md5}'), 'abcd.png');
    expect(booruDownloadFileName(post, '{artist}/{id}'), 'kantoku_42.png', reason: 'a slash would make a folder');
    expect(
      booruDownloadFileName(_post('7'), '{copyright}'),
      'booru-abcd.png',
      reason: "nothing left: the file's own name",
    );
  });

  test('a batch skips files already downloaded or on their way, and fills only the room the queue has', () {
    final posts = [for (var i = 0; i < 5; i++) _post('$i', file: 'https://cdn.example/$i.png')];

    final plan = booruDownloadPlan(
      [...posts, posts.first],
      known: {Uri.parse('https://cdn.example/1.png')},
      room: 3,
      template: '{id}',
      destination: DownloadDestination.shared,
    );

    expect([for (final request in plan.requests) request.fileName], ['0.png', '2.png', '3.png']);
    expect(plan.requests.every((request) => request.background), isTrue);
    expect(plan.skipped, 2, reason: 'one already downloaded, one twice in the list');
  });

  testWidgets('download all asks first, then says what was queued and skipped', (tester) async {
    final downloader = _Downloader();
    final posts = [_post('1'), _post('2'), _post('3')];
    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(),
        child: Provider<BooruDownloader>.value(
          value: downloader,
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
            ],
            home: Scaffold(
              body: Builder(
                builder: (context) =>
                    TextButton(onPressed: () => confirmBooruDownloadAll(context, posts), child: const Text('all')),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('all'));
    await tester.pumpAndSettle();
    expect(find.text('Posts loaded so far: 3. Files already in your downloads are skipped.'), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(downloader.asked, isEmpty);

    await tester.tap(find.text('all'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Download'));
    await tester.pumpAndSettle();
    expect(downloader.asked.single, posts);
    expect(find.text('Queued: 2. Already downloaded: 1.'), findsOneWidget);
  });
}
