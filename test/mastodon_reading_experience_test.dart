import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_store.dart';
import 'package:xta/plugins/mastodon/mastodon_reading_store.dart';
import 'package:xta/plugins/mastodon/mastodon_snapshot.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_store.dart';
import 'package:xta/plugins/mastodon/mastodon_media_grid.dart';
import 'support/mastodon_harness.dart';

MastodonPost reply(String id, String? parent) => MastodonPost(id: id, replyToId: parent,
  acct: 'reader@studio.example', authorName: 'Reader', text: id, url: 'https://studio.example/@reader/$id');

class DelayedProfileClient extends MastodonFixtureClient {
  final page = Completer<List<MastodonPost>>();
  int mediaReads = 0;
  @override
  Future<List<MastodonPost>> getStatuses(String instance, String id, {int limit = 20,
    bool excludeReplies = true, bool onlyMedia = false, bool pinned = false, String? maxId}) {
    if (onlyMedia) { mediaReads++; return Future.value([reply('media', null)]); }
    return page.future;
  }
}

void main() {
  test('branches retain sibling order and collapse only their descendants', () {
    final thread = MastodonThread(status: reply('root', null), descendants: [
      reply('a', 'root'), reply('b', 'root'), reply('a1', 'a'), reply('a2', 'a1'), reply('orphan', 'gone'),
    ]);
    expect(mastodonReplyRows(thread, {}).map((row) => row.post.id), ['a', 'a1', 'a2', 'b', 'orphan']);
    final folded = mastodonReplyRows(thread, {'a'});
    expect(folded.map((row) => row.post.id), ['a', 'b', 'orphan']);
    expect(folded.first.descendants, 2);
    expect(folded.first.collapsed, isTrue);
  });

  test('cyclic and duplicate replies cannot loop or duplicate a card', () {
    final thread = MastodonThread(status: reply('root', null), descendants: [
      reply('a', 'b'), reply('b', 'a'), reply('a', 'b'), reply('root', 'a')]);
    expect(mastodonReplyRows(thread, {}).map((row) => row.post.id).toSet(), {'a', 'b'});
    expect(mastodonReplyRows(thread, {}).length, 2);
  });

  test('status snapshots retain warnings, reply links, polls and quoted media', () {
    final post = MastodonPost(id: 's', acct: 'a@one.example', authorName: 'A', text: 'Text',
      url: 'https://one.example/@a/s', spoilerText: 'Warning', sensitive: true, replyToId: 'parent',
      images: const ['https://one.example/media.png'], editedAt: DateTime.utc(2026),
      quote: const MastodonQuotedPost(id: 'q', acct: 'b@two.example', authorName: 'B', text: 'Quote',
        url: 'https://two.example/@b/q', images: ['https://two.example/q.png']),
      poll: const MastodonPoll(options: [MastodonPollOption(title: 'Yes', votes: 7)], votesCount: 7));
    final restored = mastodonPostFromSnapshot(jsonDecode(jsonEncode(mastodonPostSnapshot(post))))!;
    expect(restored.sensitive, isTrue); expect(restored.spoilerText, 'Warning');
    expect(restored.replyToId, 'parent'); expect(restored.poll!.options.single.votes, 7);
    expect(restored.quote!.images.single, 'https://two.example/q.png'); expect(restored.editedAt, DateTime.utc(2026));
    expect(mastodonPostFromSnapshot({'version': 99}), isNull);
  });

  test('restart restores choice, content and anchor only for its server scope', () async {
    final prefs = PrefServiceCache();
    final memory = MastodonReadingStore(prefs, 'one');
    memory.select(2, false);
    memory.remember('client:2', MastodonReadPoint(posts: samplePosts, anchor: samplePosts[12].url,
      leading: -42, instance: 'https://studio.example'));
    await memory.destroy();
    final restored = MastodonReadingStore(prefs, 'one');
    expect(restored.state.tab, 2); expect(restored.state.points['client:2']!.anchor, samplePosts[12].url);
    expect(restored.layoutPoint('client:2').point!.leading, -42);
    expect(restored.layoutPoint('client:2').position, isFalse);
    final different = MastodonReadingStore(prefs, 'two');
    expect(different.state.points, isEmpty);
    await restored.destroy(); await different.destroy();
  });

  test('disabled reading memory and malformed snapshots are safe', () async {
    final prefs = PrefServiceCache(defaults: {mastodonReadingPreference: '{broken', optionFeedReadingPosition: false});
    final memory = MastodonReadingStore(prefs, 'one');
    expect(memory.state.points, isEmpty);
    await memory.destroy();
    expect(prefs.get<String>(mastodonReadingPreference), '');
    expect(MastodonReadPoint.parse({'posts': 'bad'}), isNull);
  });

  test('switching to media during posts pagination keeps both responses in their own tab', () async {
    final client = DelayedProfileClient();
    final store = MastodonProfileStore(client, ['https://studio.example'], sampleProfile.acct);
    await store.refresh();
    store.update(store.state.copy(morePosts: true));
    final pending = store.loadMore();
    store.selectMedia(true);
    client.page.complete([reply('older-post', null)]);
    await pending;
    expect(store.state.posts.last.id, 'older-post');
    expect(store.state.media.single.id, 'media');
    expect(store.state.mediaSelected, isTrue); expect(client.mediaReads, 1);
    await store.destroy(); client.httpClient.close();
  });

  testWidgets('sensitive media tiles expose a warning without fetching the image', (tester) async {
    final h = MastodonHarness();
    await tester.pumpWidget(h.app(child: const Scaffold(body: SizedBox(width: 150, height: 150,
      child: MastodonMediaTile(post: MastodonPost(id: 's', acct: 'a@one.example', authorName: 'A',
        text: '', url: 'https://one.example/s', sensitive: true, images: ['https://one.example/private.png']))))));
    await tester.pumpAndSettle();
    expect(find.text('Content warning'), findsOneWidget);
    expect(find.byType(Image), findsNothing);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
}
