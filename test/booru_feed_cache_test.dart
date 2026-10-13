import 'dart:io';

import 'package:ffcache/ffcache.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/booru/booru_feed_cache.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_json.dart';
import 'package:xta/utils/json.dart';

final _post = BooruPost(
  id: '42',
  host: 'https://danbooru.donmai.us',
  engine: 'danbooru',
  tags: const ['kantoku', 'solo'],
  rating: BooruRating.sensitive,
  score: 12,
  width: 800,
  height: 600,
  previewUrl: 'https://cdn.example/preview.jpg',
  sampleUrl: 'https://cdn.example/sample.jpg',
  fileUrl: 'https://cdn.example/file.png',
  fileExt: 'png',
  source: 'https://example.com/source',
  createdAt: DateTime.utc(2024, 1, 2, 3, 4),
  updatedAt: DateTime.utc(2024, 2, 3, 4, 5),
  tagCategories: const {'kantoku': BooruTagCategory.artist, 'solo': BooruTagCategory.general},
  fileSize: 2048,
  md5: 'abc',
  favCount: 5,
  upScore: 14,
  downScore: 2,
  parentId: '41',
  hasChildren: true,
  uploader: 'someone',
  variants: const [BooruImageVariant(url: 'https://cdn.example/360.jpg', width: 360)],
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('a post reads back with every field it was written with', () {
    final back = booruPostFromJson(Json(booruPostToJson(_post)))!;

    expect(booruPostToJson(back), booruPostToJson(_post));
    expect(back.tagCategories, _post.tagCategories);
    expect(back.variants.single.width, 360);
    expect(back.createdAt, _post.createdAt);
  });

  test('something that is not a post reads as nothing', () {
    expect(booruPostFromJson(const Json({'id': '1'})), isNull);
    expect(booruPostFromJson(const Json('post')), isNull);
  });

  test('a page kept on the device reads back under its key only', () async {
    final temporary = await Directory.systemTemp.createTemp('xta_booru_feeds');
    addTearDown(() => temporary.delete(recursive: true));
    const pathProvider = MethodChannel('plugins.flutter.io/path_provider');
    final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(pathProvider, (_) async => temporary.path);
    addTearDown(() => messenger.setMockMethodCallHandler(pathProvider, null));
    final cache = BooruFeedCache(cache: FFCache(name: 'booru_feeds_test'));

    await cache.write('latest\nhttps://danbooru.donmai.us', BooruPostPage(posts: [_post], page: 2, hasMore: false));
    final page = await cache.read('latest\nhttps://danbooru.donmai.us');

    expect(page?.posts.single.key, _post.key);
    expect(page?.page, 2);
    expect(page?.hasMore, isFalse);
    expect(await cache.read('latest\nhttps://e621.net'), isNull);
  });
}
