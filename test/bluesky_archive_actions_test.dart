import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_archive.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/saved/saved_content_index.dart';
import 'package:xta/saved/saved_media.dart';
import 'package:xta/saved/saved_source_filter.dart';

void main() {
  const post = BlueskyPost(uri: 'at://did:plc:a/app.bsky.feed.post/1', cid: 'cid', did: 'did:plc:a',
    handle: 'alice.test', authorName: 'Alice', text: 'A saved thought', url: 'https://bsky.app/profile/alice.test/post/1',
    images: ['https://cdn.test/image.jpg', 'https://cdn.test/video.jpg'], imageIsVideo: [false, true], repostedByDid: 'did:plc:b');

  test('Bluesky archive snapshot stays identifiable, searchable and retains repost actor', () {
    final stored = parseSavedContent(jsonEncode(blueskyArchiveBlob(post)));
    expect(stored.bluesky?.reposterActor, 'did:plc:b');
    expect(stored.tweet, isNull);
    expect(stored.matches('alice'), isTrue);
    expect(savedSourceOf(stored), SavedSource.bluesky);
    expect(savedContentHasMedia(stored), isTrue);
    expect(blueskyArchiveId(post), 'bluesky:${post.uri}');
  });

  test('Bluesky image downloads never treat video thumbnails as photos', () {
    final media = mediaOfSavedContent(blueskyArchiveBlob(post));
    expect(media.length, 1);
    expect(media.single.mediaUrlHttps, 'https://cdn.test/image.jpg');
  });

  test('Malformed plugin blobs are not parsed as X posts', () {
    final bad = parseSavedContent(jsonEncode({'xtaPlugin': 'bluesky', 'post': {'uri': 12}}));
    expect(bad.tweet, isNull);
    expect(bad.bluesky, isNull);
  });

  test('Other plugin notes retain a portable source link and local text', () {
    const post = PluginLinkPost(source: 'threads', url: 'https://threads.net/@alice/post/1', author: 'Alice', text: 'Remember this');
    final stored = parseSavedContent(jsonEncode(post.archive.content));
    expect(stored.plugin?.url, post.url);
    expect(stored.matches('remember'), isTrue);
    expect(savedSourceOf(stored), SavedSource.other);
    expect(stored.tweet, isNull);
  });
}
