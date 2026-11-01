import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/plugins/threads/threads_archive.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/saved/saved_content_index.dart';
import 'package:xta/saved/saved_media.dart';
import 'package:xta/saved/saved_screen.dart';
import 'package:xta/saved/saved_source_filter.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/feed_ink_harness.dart';

final _post = ThreadsPost(
  id: '3471',
  handle: 'maya',
  authorName: 'Maya Chen',
  text: 'Field notes from the studio: the kiln held.',
  url: 'https://www.threads.com/@maya/post/C9xYz',
  publishedAt: DateTime.utc(2026, 10, 31, 9),
  images: const ['https://cdn.example/a.jpg', 'https://cdn.example/b.jpg'],
  videoUrls: const [null, 'https://cdn.example/b.mp4'],
  topicTag: 'Ceramics',
  likeCount: 12,
);

/// docs/specs/threads-x-parity.md, Saving.
void main() {
  group('a saved Threads post', () {
    test('keeps the whole post and is filed under Threads', () {
      final stored = parseSavedContent(jsonEncode(threadsArchive(_post).content));

      expect(stored.threads?.text, _post.text);
      expect(stored.threads?.images, _post.images);
      expect(stored.threads?.url, _post.url);
      expect(stored.threads?.publishedAt, _post.publishedAt!.toLocal());
      expect(savedSourceOf(stored), SavedSource.threads);
      expect(stored.matches('kiln'), isTrue);
      expect(stored.matches('ceramics'), isTrue);
      expect(savedContentHasMedia(stored), isTrue);
    });

    test('keeps the id a link save used, and an old link save is filed under Threads too', () {
      final link = PluginLinkPost(source: 'threads', url: _post.url!, author: _post.handle, text: _post.text).archive;

      expect(threadsArchiveId(_post), link.id);
      expect(savedSourceOf(parseSavedContent(jsonEncode(link.content))), SavedSource.threads);
    });

    test('offers its pictures, not its video, to the saved media', () {
      final media = mediaOfSavedContent(threadsArchive(_post).content);

      expect(media.map((item) => item.mediaUrlHttps), ['https://cdn.example/a.jpg']);
    });

    test('a damaged blob is nothing rather than a crash', () {
      expect(threadsPostFromArchive({'xtaPlugin': 'threads', 'post': 'nope'}), isNull);
      expect(parseSavedContent(jsonEncode({'xtaPlugin': 'threads'})).threads, isNull);
    });
  });

  group('on screen', () {
    late ThreadsAccountsStore accounts;
    late ThreadsLikesStore likes;

    setUp(() {
      accounts = ThreadsAccountsStore();
      likes = ThreadsLikesStore(PrefServiceCache());
    });

    tearDown(() async {
      await accounts.destroy();
      await likes.destroy();
    });

    Future<void> pump(WidgetTester tester, Widget child) => pumpInk(
      tester,
      MultiProvider(
        providers: [
          Provider<ThreadsAccountsStore>.value(value: accounts),
          Provider<ThreadsLikesStore>.value(value: likes),
        ],
        child: Scaffold(body: ListView(children: [child])),
      ),
      theme: xLookLightsOutTheme(null),
    );

    testWidgets('a post offers the bookmark in its actions', (tester) async {
      await pump(tester, ThreadsPostCard(post: _post, showSourceBadge: false));

      expect(find.byTooltip(L10n.current.save_on_this_device), findsOneWidget);
    });

    testWidgets('Saved shows a saved Threads post as a Threads card', (tester) async {
      await pump(tester, SavedTweetTile(id: threadsArchiveId(_post), content: jsonEncode(threadsArchiveBlob(_post))));

      expect(find.byType(ThreadsPostCard), findsOneWidget);
      expect(find.textContaining('the kiln held', findRichText: true), findsOneWidget);
    });
  });
}
