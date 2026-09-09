import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_sources.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/utils/ai_client.dart';

DiscoveryAccount account(
  String id, {
  DiscoverySource source = DiscoverySource.x,
  String? handle,
  String name = '',
  String text = '',
}) => DiscoveryAccount(
  source: source,
  id: id,
  handle: handle ?? id,
  name: name,
  text: text,
  postUrl: 'https://example.test/post/$id',
);

BlueskyPost sky(String id, {bool repost = false, BlueskyPost? quote}) => BlueskyPost(
  uri: 'at://$id/app.bsky.feed.post/p',
  cid: 'cid',
  did: id,
  handle: '$id.bsky.social',
  authorName: id,
  text: 'A real post',
  url: 'https://bsky.app/profile/$id/post/p',
  repostedByHandle: repost ? 'group.member' : null,
  quotedPost: quote,
);

void main() {
  const ai = AiConfig(baseUrl: 'https://example.test/v1', apiKey: 'key', model: 'model');

  test('deduplicates identities per network and excludes followed aliases', () {
    final ranked = rankDiscoveryAccounts(
      [
        account('1', handle: 'Followed'),
        account('2', name: 'Space'),
        account('2'),
        account('2', source: DiscoverySource.mastodon),
        account('did:plc:member', source: DiscoverySource.bluesky, handle: 'member.bsky.social'),
      ],
      followed: {'x:followed', 'bluesky:member.bsky.social'},
      groupName: 'Space',
    );
    expect(ranked.map((e) => e.key), ['x:2', 'mastodon:2']);
  });

  test('rejects invented AI ids and preserves candidates omitted by AI', () {
    final candidates = [account('1'), account('2'), account('3')];
    final result = parseDiscoveryRanking('{"ids":["x:2","invented:9","x:2"]}', candidates);
    expect(result!.map((e) => e.key), ['x:2', 'x:1', 'x:3']);
    expect(parseDiscoveryRanking('{"ids":["invented:9"]}', candidates), isNull);
    expect(parseDiscoveryRanking('{"ids":"x:1"}', candidates), isNull);
    expect(parseDiscoveryRanking('not json', candidates), isNull);
  });

  test('Bluesky candidates come from actual reposts and quotes only', () {
    final direct = sky('direct');
    final repost = sky('repost', repost: true);
    final quoted = sky('quoted');
    final candidates = blueskyDiscoveryAccounts([direct, repost, sky('quoter', quote: quoted)]);
    expect(candidates.map((e) => e.id), ['repost', 'quoted']);
    expect(candidates.last.supportingPost, same(quoted));
  });

  test('Mastodon candidates come from boosts and quoted authors', () {
    const direct = MastodonPost(
      id: '1',
      acct: 'member@server.test',
      authorName: 'Member',
      text: 'Direct',
      url: 'https://server.test/1',
    );
    const boost = MastodonPost(
      id: '2',
      acct: 'other@server.test',
      authorName: 'Other',
      text: 'Boost',
      url: 'https://server.test/2',
      boosted: true,
    );
    expect(mastodonDiscoveryAccounts([direct, boost]).single.id, 'other@server.test');
  });

  test('source failure preserves candidates and normal discovery never uses AI', () async {
    var aiCalls = 0;
    final store = GroupDiscoveryStore(
      chat: (_, _) async {
        aiCalls++;
        return '{"ids":["x:1"]}';
      },
    );
    addTearDown(store.destroy);
    await store.load(
      sources: [
        () async => [account('1')],
        () async => throw StateError('offline'),
      ],
      followed: {},
      groupName: 'Space',
    );
    expect(store.state.accounts.single.id, '1');
    expect(store.state.sourceFailed, isTrue);
    expect(aiCalls, 0);
  });

  test('unusable AI response keeps verified local candidates and reports fallback', () async {
    final store = GroupDiscoveryStore(chat: (_, _) async => '{"ids":["fictional"]}');
    addTearDown(store.destroy);
    await store.load(
      sources: [
        () async => [account('1')],
      ],
      followed: {},
      groupName: 'Space',
      ai: ai,
    );
    expect(store.state.accounts.single.id, '1');
    expect(store.state.usedAi, isFalse);
    expect(store.state.aiFailed, isTrue);
  });

  test('all-followed candidates never trigger the configured AI', () async {
    var calls = 0;
    final store = GroupDiscoveryStore(
      chat: (_, _) async {
        calls++;
        return '';
      },
    );
    addTearDown(store.destroy);
    await store.load(
      sources: [
        () async => [account('1')],
      ],
      followed: {'x:1'},
      groupName: 'Space',
      ai: ai,
    );
    expect(store.state.accounts, isEmpty);
    expect(calls, 0);
  });

  test('following from a profile removes its cached discovery card without refetch', () async {
    var loads = 0;
    final store = GroupDiscoveryStore();
    addTearDown(store.destroy);
    await store.load(
      sources: [
        () async {
          loads++;
          return [account('1', handle: 'newfollow'), account('2')];
        },
      ],
      followed: {},
      groupName: 'Space',
    );
    store.excludeFollowed({'x:newfollow'});
    expect(store.state.accounts.single.id, '2');
    expect(loads, 1);
  });

  test('switching from Discover remembers that its pane has been opened', () {
    final mode = GroupDiscoveryModeStore();
    addTearDown(mode.destroy);
    expect(mode.opened, isFalse);
    mode.select(true);
    expect(mode.selected, isTrue);
    mode.select(false);
    expect(mode.selected, isFalse);
    expect(mode.opened, isTrue);
  });

  test('a superseded source request cannot call AI or replace newer results', () async {
    final older = Completer<List<DiscoveryAccount>>();
    final newer = Completer<List<DiscoveryAccount>>();
    var aiCalls = 0;
    final store = GroupDiscoveryStore(
      chat: (_, _) async {
        aiCalls++;
        return '{"ids":["x:old"]}';
      },
    );
    addTearDown(store.destroy);
    final first = store.load(sources: [() => older.future], followed: {}, groupName: 'Old', ai: ai);
    final second = store.load(sources: [() => newer.future], followed: {}, groupName: 'New');
    older.complete([account('old')]);
    await first;
    expect(aiCalls, 0);
    expect(store.isLoading, isTrue);
    newer.complete([account('new')]);
    await second;
    expect(store.state.accounts.single.id, 'new');
    expect(store.isLoading, isFalse);
  });

  test('a superseded AI response cannot replace newer discovery', () async {
    final reply = Completer<String>();
    final started = Completer<void>();
    final store = GroupDiscoveryStore(
      chat: (_, _) {
        started.complete();
        return reply.future;
      },
    );
    addTearDown(store.destroy);
    final first = store.load(
      sources: [
        () async => [account('old')],
      ],
      followed: {},
      groupName: 'Old',
      ai: ai,
    );
    await started.future;
    await store.load(
      sources: [
        () async => [account('new')],
      ],
      followed: {},
      groupName: 'New',
    );
    reply.complete('{"ids":["x:old"]}');
    await first;
    expect(store.state.accounts.single.id, 'new');
    expect(store.state.usedAi, isFalse);
  });

  test('following during a source request excludes its eventual result', () async {
    final response = Completer<List<DiscoveryAccount>>();
    final store = GroupDiscoveryStore();
    addTearDown(store.destroy);
    final loading = store.load(sources: [() => response.future], followed: {}, groupName: 'Space');
    store.excludeFollowed({'x:followed'});
    response.complete([account('1', handle: 'followed'), account('2')]);
    await loading;
    expect(store.state.accounts.single.id, '2');
  });

  test('closing discovery retires pending sources before AI starts', () async {
    final response = Completer<List<DiscoveryAccount>>();
    var aiCalls = 0;
    final store = GroupDiscoveryStore(
      chat: (_, _) async {
        aiCalls++;
        return '{"ids":["x:1"]}';
      },
    );
    final loading = store.load(sources: [() => response.future], followed: {}, groupName: 'Space', ai: ai);
    await store.destroy();
    response.complete([account('1')]);
    await loading;
    expect(aiCalls, 0);
  });
}
