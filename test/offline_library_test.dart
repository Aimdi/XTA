import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:html/parser.dart' as html;
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/offline/offline_article.dart';
import 'package:xta/offline/offline_store.dart';
import 'package:xta/offline/offline_transfer.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/substack/substack_models.dart';

final _png = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==',
);
OfflineArticle _article({String body = '<p>A retained article</p>', String feed = 'example'}) => OfflineArticle.rss(
  RssItem(
    id: '1',
    title: 'Reading in the city',
    feedId: feed,
    feedTitle: 'Daily journal',
    link: 'https://example.com/read/1',
    bodyHtml: body,
  ),
);

class _StreamClient extends http.BaseClient {
  final Future<http.StreamedResponse> Function(http.BaseRequest) respond;
  _StreamClient(this.respond);
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) => respond(request);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory directory;
  final stores = <OfflineStore>[];
  setUp(() async {
    directory = await Directory.systemTemp.createTemp('xta-offline-test-');
  });
  tearDown(() async {
    for (final store in stores) {
      await store.destroy();
    }
    stores.clear();
    if (await directory.exists()) await directory.delete(recursive: true);
  });
  OfflineStore store({
    http.Client? client,
    int storageLimit = OfflineStore.defaultStorageLimit,
    int imageLimit = 8 * 1024 * 1024,
  }) {
    final model = OfflineStore(
      directory: () async => directory,
      client: client ?? MockClient((_) async => http.Response.bytes(_png, 200)),
      storageLimit: storageLimit,
      imageLimit: imageLimit,
    );
    stores.add(model);
    return model;
  }

  test('article snapshots preserve identity, body, author and media metadata', () {
    final rss = OfflineArticle.rss(
      RssItem(
        id: '1',
        title: 'News',
        feedId: 'feed',
        feedTitle: 'Journal',
        author: 'Maya',
        bodyHtml: '<p>Body</p>',
        categories: ['Science'],
        publishedAt: DateTime.utc(2026, 9, 7),
      ),
    );
    final rssCopy = OfflineArticle.fromJson(jsonDecode(jsonEncode(rss.toJson())) as Map<String, dynamic>);
    expect(rssCopy.rssItem!.bodyHtml, '<p>Body</p>');
    expect(rssCopy.rssItem!.categories, ['Science']);
    expect(rssCopy.rssItem!.author, 'Maya');
    expect(rss.id, isNot(_article(feed: 'another').id));
    const post = SubstackPost(
      id: '12',
      title: 'An episode',
      slug: 'episode',
      publicationBaseUrl: 'https://journal.substack.com',
      publicationName: 'Journal',
      bodyHtml: '<p>Full text</p>',
      authorName: 'Maya',
      audience: 'only_paid',
      audioUrl: 'https://example.com/audio.mp3',
      hasVideoUpload: true,
    );
    final substack = OfflineArticle.fromJson(OfflineArticle.substack(post).toJson()).substackPost!;
    expect(substack.bodyHtml, post.bodyHtml);
    expect(substack.authorName, 'Maya');
    expect(substack.isPaywalled, isTrue);
    expect(substack.hasVideoUpload, isTrue);
    expect(substack.audioUrl, post.audioUrl);
  });

  test('cold restart reports exact partial images and renders without remote fetches', () async {
    var calls = 0;
    final model = store(
      client: MockClient((request) async {
        calls++;
        return request.url.path == '/good.png' ? http.Response.bytes(_png, 200) : http.Response('', 503);
      }),
    );
    final article = _article(body: '<p>Text</p><img src="/good.png"><img src="/missing.png">');
    await model.keepArticle(article);
    final entry = model.state.entry(article.id)!;
    expect(entry.availability, OfflineAvailability.partial);
    expect(entry.totalMedia, 2);
    expect(entry.files.length, 1);
    expect(calls, 2);
    final cold = store(client: MockClient((_) async => throw StateError('No network on offline read')));
    expect((await cold.article(article.id))!.bodyHtml, article.bodyHtml);
    final rendered = await cold.renderArticle(
      article.id,
      '<html><head><style>p{color:red}</style></head><body><p>Text</p>'
      '<img src="https://example.com/good.png" srcset="https://example.com/missing.png 2x">'
      '<img src="/missing.png"><video src="https://example.com/v.mp4"></video></body></html>',
    );
    final document = html.parse(rendered);
    expect(document.querySelector('img')!.attributes['src'], startsWith('data:image/png;base64,'));
    expect(document.querySelectorAll('img')[1].attributes.containsKey('src'), isFalse);
    expect(document.querySelector('img')!.attributes.containsKey('srcset'), isFalse);
    expect(document.querySelectorAll('video'), isEmpty);
    expect(document.querySelector('meta')!.attributes['content'], contains("default-src 'none'"));
    expect(cold.state.bytes, entry.articleBytes + _png.length);
  });

  test('missing files downgrade actual availability and storage bytes', () async {
    final model = store();
    final article = _article(body: '<img src="https://example.com/a.png">');
    await model.keepArticle(article);
    final entry = model.state.entry(article.id)!;
    await (await model.mediaFile(entry, entry.files.single))!.delete();
    await model.refresh();
    expect(model.state.entry(article.id)!.availability, OfflineAvailability.partial);
    expect(model.state.bytes, entry.articleBytes);
    await File('${directory.path}/${entry.directory}/article.json').delete();
    await model.refresh();
    expect(model.state.entry(article.id)!.availability, OfflineAvailability.unavailable);
    expect(await model.article(article.id), isNull);
    expect(model.state.bytes, 0);
  });

  test('Substack slug links find numeric-ID retained articles without a fetch', () async {
    final model = store();
    const post = SubstackPost(
      id: '123',
      title: 'Story',
      slug: 'a-story',
      publicationBaseUrl: 'https://journal.substack.com',
      publicationName: 'Journal',
      bodyHtml: '<p>Retained body</p>',
    );
    await model.keepArticle(OfflineArticle.substack(post));
    final cold = store(client: MockClient((_) async => throw StateError('Must not fetch')));
    final retained = await cold.article(
      'substack:journal:a-story',
      canonicalUrl: 'https://journal.substack.com/p/a-story#comments',
    );
    expect(retained!.substackPost!.id, '123');
    expect(retained.bodyHtml, post.bodyHtml);
  });

  test('empty bodies never become available and SVG is not embeddable', () async {
    final model = store();
    final article = _article(body: '');
    await model.keepArticle(article);
    expect(model.state.entry(article.id), isNull);
    expect(model.state.failed, contains(article.id));
    expect(offlineImageMime(utf8.encode('<svg onload="evil()"/>')), isNull);
  });

  test('storage and per-image limits apply before publication', () async {
    final model = store(storageLimit: _png.length - 1);
    await model.keepMedia(
      id: 'saved:1',
      title: 'Photo',
      source: 'Maya',
      media: const [OfflineMediaSource('https://example.com/a.png')],
    );
    expect(model.state.entries, isEmpty);
    expect(model.state.failed, contains('saved:1'));
    final limited = store(imageLimit: _png.length - 1);
    final article = _article(body: '<img src="https://example.com/a.png">');
    await limited.keepArticle(article);
    expect(limited.state.entry(article.id)!.files, isEmpty);
    expect(limited.state.entry(article.id)!.availability, OfflineAvailability.partial);
  });

  test('streamed size overflow cannot create an available file', () async {
    final model = store(
      imageLimit: 32,
      client: _StreamClient(
        (_) async => http.StreamedResponse(Stream.fromIterable([_png.sublist(0, 16), _png.sublist(16)]), 200),
      ),
    );
    await model.keepMedia(
      id: 'saved:1',
      title: 'Photo',
      source: 'Maya',
      media: const [OfflineMediaSource('https://example.com/a.png')],
    );
    expect(model.state.entries, isEmpty);
    expect(await directory.list().toList(), isEmpty);
  });

  test('interrupted response cleans staging and is never available', () async {
    final model = store(
      client: _StreamClient(
        (_) async => http.StreamedResponse(
          Stream<List<int>>.multi((events) {
            events.add(_png.sublist(0, 16));
            events.addError(const SocketException('Interrupted'));
            events.close();
          }),
          200,
        ),
      ),
    );
    await model.keepMedia(
      id: 'saved:1',
      title: 'Photo',
      source: 'Maya',
      media: const [OfflineMediaSource('https://example.com/a.png')],
    );
    expect(model.state.entries, isEmpty);
    expect(model.state.busy, isEmpty);
    expect(model.state.failed, contains('saved:1'));
    expect(await directory.list().toList(), isEmpty);
  });

  test('remove cancels an in-flight copy before publication', () async {
    final started = Completer<void>();
    final stream = StreamController<List<int>>();
    final model = store(
      client: _StreamClient((_) async {
        started.complete();
        return http.StreamedResponse(stream.stream, 200);
      }),
    );
    final keeping = model.keepMedia(
      id: 'saved:1',
      title: 'Photo',
      source: 'Maya',
      media: const [OfflineMediaSource('https://example.com/a.png')],
    );
    await started.future;
    final removal = model.remove('saved:1');
    stream.add(_png);
    await stream.close();
    await Future.wait([keeping, removal]);
    expect(model.state.entries, isEmpty);
    expect(model.state.busy, isEmpty);
    expect(await directory.list().toList(), isEmpty);
  });

  test('a failed replacement keeps the previous durable copy', () async {
    final model = store();
    final article = _article();
    await model.keepArticle(article);
    final originalBytes = model.state.bytes;
    await Directory('${directory.path}/manifest.tmp').create();
    await model.keepArticle(_article(body: '<p>Replacement</p>'));
    expect(model.state.failed, contains(article.id));
    expect((await model.article(article.id))!.bodyHtml, article.bodyHtml);
    expect(model.state.bytes, originalBytes);
  });

  test('failed manifest write after remove cannot leave a ready state', () async {
    final model = store();
    final article = _article();
    await model.keepArticle(article);
    await Directory('${directory.path}/manifest.tmp').create();
    await model.remove(article.id);
    expect(model.state.storageError, isTrue);
    expect(model.state.entry(article.id)!.availability, OfflineAvailability.unavailable);
    expect(model.state.bytes, 0);
    expect(await model.article(article.id), isNull);
    final cold = store();
    await cold.load();
    expect(cold.state.entry(article.id)!.canOpen, isFalse);
  });

  test('failed manifest write after clear cannot leave ready entries', () async {
    final model = store();
    final article = _article();
    await model.keepArticle(article);
    await Directory('${directory.path}/manifest.tmp').create();
    await model.clear();
    expect(model.state.storageError, isTrue);
    expect(model.state.entry(article.id)!.availability, OfflineAvailability.unavailable);
    expect(model.state.bytes, 0);
  });

  test('clear removes copies and startup removes interrupted staging', () async {
    final model = store();
    await model.keepArticle(_article());
    await Directory('${directory.path}/interrupted.tmp').create();
    await File('${directory.path}/interrupted.tmp/media-0').writeAsBytes(_png);
    await model.refresh();
    expect(await Directory('${directory.path}/interrupted.tmp').exists(), isFalse);
    await model.clear();
    expect(model.state.entries, isEmpty);
    expect(model.state.bytes, 0);
    final cold = store();
    await cold.load();
    expect(cold.state.entries, isEmpty);
    expect((await directory.list().toList()).length, 1);
  });

  test('malformed manifest reports an error and explicit retry recovers', () async {
    await File('${directory.path}/manifest.json').writeAsString('{broken');
    final model = store();
    await model.load();
    expect(model.state.storageError, isTrue);
    await File('${directory.path}/manifest.json').writeAsString('{"version":1,"entries":[]}');
    await model.refresh();
    expect(model.state.storageError, isFalse);
    expect(model.state.loading, isFalse);
  });
}
