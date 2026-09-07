import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/mastodon/mastodon_archive.dart';
import 'package:xta/plugins/mastodon/mastodon_bookmark.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/saved/saved_content_index.dart';
import 'package:xta/saved/saved_media.dart';
import 'package:xta/saved/saved_source_filter.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'support/mastodon_harness.dart';

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

void main() {
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
    final h = MastodonHarness();
    final saved = MemorySaved();
    final folders = SavedTweetFolderModel();
    await tester.pumpWidget(
      h.app(
        child: MultiProvider(
          providers: [
            Provider<SavedTweetModel>.value(value: saved),
            Provider<SavedTweetFolderModel>.value(value: folders),
          ],
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
    expect(tester.takeException(), isNull);
    await h.close(tester);
    await saved.destroy();
    await folders.destroy();
  });
}
