import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/offline/offline_saved_media.dart';
import 'package:xta/plugins/feed_snapshot_posts.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/plugins/threads/threads_archive.dart';
import 'package:xta/plugins/threads/threads_interleaved.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/saved/saved_content_index.dart';
import 'package:xta/tweet/feed_snapshot_cache.dart';

import 'support/memory_json_store.dart';

ThreadsPost _post(String handle, int minutesAgo, {String? reposter, String? name, String? avatar}) => ThreadsPost(
  id: '$handle-$minutesAgo',
  handle: handle,
  authorName: name ?? handle,
  avatarUrl: avatar,
  text: 'post $minutesAgo by $handle',
  url: 'https://www.threads.com/@$handle/post/C$minutesAgo',
  publishedAt: DateTime.utc(2026, 11, 1).subtract(Duration(minutes: minutesAgo)),
  repostedByHandle: reposter,
);

void main() {
  group('mixed feeds', () {
    test('each followed account brings up to ten posts, in the order they came', () {
      final posts = [
        for (var i = 0; i < 12; i++) ...[_post('busy', i * 2), _post('quiet', i * 2 + 1)],
      ];
      final items = threadsInterleavedItems(posts);
      expect(items, hasLength(20));
      expect(items.where((item) => item.id!.contains(':busy-')), hasLength(10));
      expect(items.where((item) => item.id!.contains(':quiet-')), hasLength(10));
      expect(items.first.id, '$pluginIdThreads:busy-0');
    });

    test('a repost counts for the account that reposted it', () {
      final kept = threadsPerAccount([
        for (var i = 0; i < 3; i++) _post('author', i, reposter: 'fan'),
        _post('author', 9),
      ], 2);
      expect([for (final post in kept) post.id], ['author-0', 'author-1', 'author-9']);
    });

    testWidgets('a feed read offline shows a Threads post as a Threads card', (tester) async {
      final storage = MemoryJsonStore();
      final cache = FeedSnapshotCache(storage: storage);
      final key = cache.key('home', ['a']);
      await cache.write(key, threadsInterleavedItems([_post('maya', 1)]));

      final read = await cache.read(key);
      expect(read.items, hasLength(1));
      expect(read.items.single.source, pluginIdThreads);
      await tester.pumpWidget(const SizedBox());
      final context = tester.element(find.byType(SizedBox));
      final card = read.items.single.build(context);
      expect(card, isA<ThreadsPostCard>().having((card) => card.post.text, 'text', 'post 1 by maya'));
    });

    testWidgets('a link post kept before still reads back as a link post', (tester) async {
      final snapshot = {
        'xtaPlugin': 'link',
        'source': 'rss',
        'url': 'https://example.com/a',
        'author': 'Example',
        'text': 'an article',
        'images': <String>[],
      };
      final post = feedSnapshotPost(snapshot)!;
      expect(post.source, 'rss');
      await tester.pumpWidget(const SizedBox());
      expect(post.build(tester.element(find.byType(SizedBox))), isA<PluginLinkPostCard>());
      expect(feedSnapshotPost({'xtaPlugin': 'nothing'}), isNull);
    });
  });

  group('followed accounts', () {
    const kept = ThreadsAccount(handle: 'maya', name: 'Maya', avatarUrl: 'https://cdn.example/old.jpg?oe=1');

    test('take the name and picture their posts show now', () {
      final changed = threadsAccountsChangedBy(
        [kept, const ThreadsAccount(handle: 'ola', name: 'Ola')],
        [_post('maya', 1, name: 'Maya Chen', avatar: 'https://cdn.example/new.jpg?oe=2'), _post('maya', 9)],
      );
      expect(changed, hasLength(1));
      expect(changed.single.handle, 'maya');
      expect(changed.single.name, 'Maya Chen');
      expect(changed.single.avatarUrl, 'https://cdn.example/new.jpg?oe=2');
    });

    test('nothing changes when the posts show them as kept, or show nothing', () {
      expect(threadsAccountsChangedBy([kept], [_post('maya', 1, name: 'Maya', avatar: kept.avatarUrl)]), isEmpty);
      expect(threadsAccountsChangedBy([kept], [_post('maya', 1, name: ' ')]), isEmpty);
      expect(threadsAccountsChangedBy([kept], [_post('someone', 1, name: 'Else')]), isEmpty);
    });
  });

  group('saved posts', () {
    test('a saved Threads post offers its pictures and videos to keep offline', () {
      const post = ThreadsPost(
        id: 'saved',
        handle: 'maya',
        authorName: 'Maya',
        text: 'the kiln held',
        images: ['https://cdn.example/a.jpg', 'https://cdn.example/b.jpg'],
        videoUrls: [null, 'https://cdn.example/b.mp4'],
      );
      final content = SavedContent(threads: post, haystack: threadsArchiveHaystack(post));

      final media = offlineMediaOf(content);
      expect(
        [for (final source in media) (source.url, source.video)],
        [('https://cdn.example/a.jpg', false), ('https://cdn.example/b.mp4', true)],
      );
      expect(hasOfflineMedia(content), isTrue);
      expect(offlineSavedTitle(content), 'the kiln held');
      expect(offlineSavedSource(content), 'maya');
    });
  });
}
