import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/mastodon/mastodon_archive.dart';
import 'package:xta/plugins/mastodon/mastodon_bookmark.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_screen.dart';
import 'package:xta/saved/saved_screen.dart';
import 'package:xta/saved/saved_chrome.dart';
import 'package:xta/saved/saved_content_index.dart';
import 'package:xta/saved/saved_media.dart';
import 'package:xta/saved/saved_source_filter.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'support/mastodon_harness.dart';
import 'support/reader_review_harness.dart';

const archivedPost = MastodonPost(
  id: '123',
  acct: 'artist@studio.example',
  authorName: 'Artist',
  text: 'Sketches for the garden',
  url: 'https://studio.example/@artist/123',
  spoilerText: 'Spoiler',
  sensitive: true,
  images: ['https://studio.example/garden.png'],
);

class MemorySaved extends SavedTweetModel {
  @override
  Future<void> saveTweet(String id, String? user, Map<String, dynamic> content, {String? folderId}) async =>
      update([...state, SavedTweet(id: id, user: user, content: jsonEncode(content), folderId: folderId)]);
  @override
  Future<void> deleteSavedTweet(String id) async => update(state.where((post) => post.id != id).toList());
}

class MemoryFolders extends SavedTweetFolderModel {
  int reads = 0;
  MemoryFolders() {
    update([SavedTweetFolder(id: 'inspiration', name: 'Inspiration', createdAt: DateTime(2026))]);
  }
  @override
  Future<void> listFolders() async {
    reads++;
  }
}

void main() {
  testWidgets('Saved combines network, folder and note search, then opens the stored conversation', (tester) async {
    final h = ReaderReviewHarness();
    h.saved.update([
      SavedTweet(
        id: mastodonArchiveId(reviewPost('root')),
        user: 'maya',
        folderId: 'inspiration',
        note: 'Colours to come back to.',
        content: jsonEncode(mastodonArchiveBlob(reviewPost('root'))),
      ),
      SavedTweet(
        id: mastodonArchiveId(reviewPost('b')),
        user: 'maya',
        content: jsonEncode(mastodonArchiveBlob(reviewPost('b'))),
      ),
    ]);
    await tester.pumpWidget(h.providers(h.app(child: SavedScreen(scrollController: h.scroll))));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('saved-source-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('X'));
    await tester.pumpAndSettle();
    expect(find.byType(MastodonPostCard), findsNothing);
    await tester.tap(find.byKey(const ValueKey('saved-source-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Mastodon'));
    await tester.pumpAndSettle();
    final folders = find.descendant(of: find.byType(SavedControlBar), matching: find.byType(Scrollable));
    await tester.scrollUntilVisible(find.text('Inspiration'), 300, scrollable: folders);
    await tester.tap(find.text('Inspiration'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.search));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'colours');
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pumpAndSettle();
    expect(find.byType(MastodonPostCard), findsOneWidget);
    await tester.tap(find.byIcon(Icons.mode_comment_outlined));
    await tester.pumpAndSettle();
    expect(find.byType(MastodonThreadScreen), findsOneWidget);
    expect(find.byKey(const ValueKey('mastodon-thread-selected')), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byType(MastodonPostCard), findsOneWidget);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });

  test('same status id from two servers never collides in Saved', () {
    const remote = MastodonPost(
      id: '123',
      acct: 'artist@other.example',
      authorName: 'Artist',
      text: 'Different',
      url: 'https://other.example/@artist/123',
    );
    expect(mastodonArchiveId(archivedPost), isNot(mastodonArchiveId(remote)));
    expect(mastodonArchiveId(archivedPost), startsWith('mastodon:'));
  });

  test('the shared index retains warning, author and searchable text', () {
    final content = parseSavedContent(jsonEncode(mastodonArchiveBlob(archivedPost)));
    expect(content.mastodon!.sensitive, isTrue);
    expect(content.tweet, isNull);
    expect(content.reddit, isNull);
    expect(content.matches('garden'), isTrue);
    expect(content.matches('artist@studio'), isTrue);
    expect(matchesSavedSource(content, SavedSource.mastodon), isTrue);
    expect(matchesSavedSource(content, SavedSource.x), isFalse);
    expect(savedContentHasMedia(content), isTrue);
    expect(parseSavedContent('{"xtaPlugin":"mastodon","post":null}').tweet, isNull);
  });

  test('folder media extraction preserves server URLs and unique asset ids', () {
    final media = mediaOfSavedContent(mastodonArchiveBlob(archivedPost)).single;
    expect(media.mediaUrlHttps, 'https://studio.example/garden.png');
    expect(media.idStr, startsWith('mastodon:https://studio.example/'));
    expect(media.type, 'photo');
  });

  testWidgets('bookmark writes the shared archive and can undo it', (tester) async {
    final messenger = tester.binding.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(SystemChannels.platform, (_) async => null);
    addTearDown(() => messenger.setMockMethodCallHandler(SystemChannels.platform, null));
    final h = MastodonHarness();
    final saved = MemorySaved();
    final folders = MemoryFolders();
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          Provider<SavedTweetModel>.value(value: saved),
          Provider<SavedTweetFolderModel>.value(value: folders),
        ],
        child: h.app(
          child: const Scaffold(body: MastodonBookmark(post: archivedPost)),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.bookmark_border));
    await tester.pumpAndSettle();
    expect(saved.state.single.id, mastodonArchiveId(archivedPost));
    expect(saved.contentOf(saved.state.single.id)!.mastodon!.spoilerText, 'Spoiler');
    await tester.tap(find.byIcon(Icons.bookmark));
    await tester.pumpAndSettle();
    expect(saved.state, isEmpty);
    await tester.longPress(find.byIcon(Icons.bookmark_border));
    expect(folders.reads, 1);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Inspiration'));
    await tester.pumpAndSettle();
    expect(saved.state.single.folderId, 'inspiration');
    expect(saved.contentOf(saved.state.single.id)!.mastodon!.url, archivedPost.url);
    expect(tester.takeException(), isNull);
    await h.close(tester);
    await saved.destroy();
    await folders.destroy();
  });
}
