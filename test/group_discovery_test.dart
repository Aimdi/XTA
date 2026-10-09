import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_sources.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/subscriptions/group_ungrouped.dart';
import 'package:xta/utils/ai_client.dart';
import 'support/memory_json_store.dart';

DiscoveryAccount account(
  String id, {
  DiscoverySource source = DiscoverySource.x,
  String? handle,
  String name = '',
  String text = '',
  List<DiscoverySupporter> supporters = const [],
  DateTime? date,
}) => DiscoveryAccount(
  source: source,
  id: id,
  handle: handle ?? id,
  name: name,
  text: text,
  postUrl: 'https://example.test/post/$id',
  supporters: supporters,
  date: date,
);

DiscoverySupporter supporter(String member, {DiscoverySignal kind = DiscoverySignal.reposted, DateTime? date}) =>
    DiscoverySupporter(memberId: member, handle: member, name: member, kind: kind, date: date);

/// A source that answers [read] in one go and speaks for [members] members.
DiscoveryLoad source(
  Future<List<DiscoveryAccount>> Function() read, {
  DiscoverySource kind = DiscoverySource.x,
  int members = 1,
}) => DiscoveryLoad(kind, members: members, read: (_) async => DiscoveryBatch(await read(), read: members));

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
  final old = DateTime(2026, 9, 1);
  final fresh = DateTime(2026, 10, 7);

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

  test('merges one account seen through several members and ranks it by how many vouch', () {
    final ranked = rankDiscoveryAccounts(
      [
        account('once', name: 'Once', supporters: [supporter('m9', date: fresh)], date: fresh),
        for (final member in ['m1', 'm2', 'm3'])
          account('popular', name: 'Popular', supporters: [supporter(member, date: old)], date: old),
        account('popular', supporters: [supporter('m1', kind: DiscoverySignal.quoted, date: old)], date: old),
      ],
      followed: {},
      groupName: '🍫',
    );
    expect(ranked.map((e) => e.id), ['popular', 'once']);
    expect(ranked.first.memberCount, 3);
    expect(ranked.first.name, 'Popular');
    expect(
      ranked.first.supporters.map((s) => s.identity),
      unorderedEquals(['m1:reposted', 'm2:reposted', 'm3:reposted', 'm1:quoted']),
    );
  });

  test('among equally supported accounts the stronger signal, then the newer support, wins', () {
    final ranked = rankDiscoveryAccounts(
      [
        account('mentioned', supporters: [supporter('m1', kind: DiscoverySignal.mentioned, date: fresh)]),
        account('older', supporters: [supporter('m1', date: old)]),
        account('newer', supporters: [supporter('m2', date: fresh)]),
      ],
      followed: {},
      groupName: 'イラスト',
    );
    expect(ranked.map((e) => e.id), ['newer', 'older', 'mentioned']);
  });

  test('the Unicode tokenizer keeps every script while nameTokens stays as it was', () {
    expect(unicodeNameTokens('Künstler イラスト 🍫 AI art'), {'künstler', 'イラスト', 'ai', 'art'});
    expect(unicodeNameTokens('🍫'), isEmpty);
    expect(nameTokens('Künstler'), {'nstler'});
  });

  test('the group name only breaks ties, in any script', () {
    final ranked = rankDiscoveryAccounts(
      [account('other', name: 'Sonstiges'), account('match', name: 'Künstlerin')],
      followed: {},
      groupName: 'Künstlerin 🎨',
    );
    expect(ranked.first.id, 'match');
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
    expect(candidates.first.supporters.single.memberId, 'group.member');
    expect(candidates.last.supporters.single.kind, DiscoverySignal.quoted);
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
      boostedByAcct: 'member@server.test',
    );
    final candidate = mastodonDiscoveryAccounts([direct, boost]).single;
    expect(candidate.id, 'other@server.test');
    expect(candidate.supporters.single.memberId, 'member@server.test');
  });

  test('a failing source keeps the others and names itself', () async {
    var aiCalls = 0;
    final store = GroupDiscoveryStore(
      storage: MemoryJsonStore(),
      chat: (_, _) async {
        aiCalls++;
        return '{"ids":["x:1"]}';
      },
    );
    addTearDown(store.destroy);
    await store.load(
      sources: [
        source(() async => [account('1')]),
        source(() async => throw StateError('offline'), kind: DiscoverySource.bluesky),
      ],
      followed: {},
      groupName: 'Space',
    );
    expect(store.state.accounts.single.id, '1');
    expect(store.state.sourceFailed, isTrue);
    expect(store.state.failures[DiscoverySource.bluesky], isA<StateError>());
    expect(store.state.loadingMore, isFalse);
    expect(aiCalls, 0);
  });

  test('rerankWithAi orders what is loaded without fetching again', () async {
    var reads = 0;
    String? prompt;
    final store = GroupDiscoveryStore(
      storage: MemoryJsonStore(),
      chat: (_, p) async {
        prompt = p;
        return '{"ids":["x:2"]}';
      },
    );
    addTearDown(store.destroy);
    await store.load(
      sources: [
        source(() async {
          reads++;
          return [account('1', supporters: [supporter('m1')]), account('2')];
        }),
      ],
      followed: {},
      groupName: 'Space',
    );
    expect(store.state.accounts.map((e) => e.id), ['1', '2']);
    await store.rerankWithAi(ai);
    expect(reads, 1);
    expect(store.state.usedAi, isTrue);
    expect(store.state.accounts.map((e) => e.id), ['2', '1']);
    expect(prompt, contains('"members":1'));
    expect(prompt, contains('"reposted":1'));
  });

  test('unusable AI response keeps verified local candidates and reports fallback', () async {
    final store = GroupDiscoveryStore(storage: MemoryJsonStore(), chat: (_, _) async => '{"ids":["fictional"]}');
    addTearDown(store.destroy);
    await store.load(sources: [source(() async => [account('1')])], followed: {}, groupName: 'Space');
    await store.rerankWithAi(ai);
    expect(store.state.accounts.single.id, '1');
    expect(store.state.usedAi, isFalse);
    expect(store.state.aiFailed, isTrue);
  });

  test('all-followed candidates never trigger the configured AI', () async {
    var calls = 0;
    final store = GroupDiscoveryStore(
      storage: MemoryJsonStore(),
      chat: (_, _) async {
        calls++;
        return '';
      },
    );
    addTearDown(store.destroy);
    await store.load(sources: [source(() async => [account('1')])], followed: {'x:1'}, groupName: 'Space');
    await store.rerankWithAi(ai);
    expect(store.state.accounts, isEmpty);
    expect(calls, 0);
  });

  test('Scan more reads another rotating batch and keeps what the first one found', () async {
    var reads = 0;
    final store = GroupDiscoveryStore(storage: MemoryJsonStore());
    addTearDown(store.destroy);
    await store.load(
      sources: [
        DiscoveryLoad(
          DiscoverySource.x,
          members: 149,
          read: (scan) async {
            reads++;
            return DiscoveryBatch([account(scan.more ? 'second' : 'first')], read: scan.more ? 12 : 6);
          },
        ),
      ],
      followed: {},
      groupName: 'Space',
    );
    expect(store.state.coverage, (read: 6, total: 149));
    await store.scanMore();
    expect(reads, 2);
    expect(store.state.coverage, (read: 12, total: 149));
    expect(store.state.accounts.map((e) => e.id), unorderedEquals(['first', 'second']));
    expect(store.state.loadingMore, isFalse);
  });

  test('hiding an account removes it until Undo brings it back', () async {
    final store = GroupDiscoveryStore(storage: MemoryJsonStore());
    addTearDown(store.destroy);
    await store.load(
      sources: [source(() async => [account('1'), account('2')])],
      followed: {},
      groupName: 'Space',
      groupId: 'g',
    );
    final hidden = store.state.accounts.first;
    await store.feedback(hidden, 0);
    expect(store.state.accounts.map((e) => e.key), isNot(contains(hidden.key)));
    expect(store.state.accounts, hasLength(1));
    expect(store.canUndo, isTrue);
    await store.resetFeedback(undo: true);
    expect(store.state.accounts, hasLength(2));
    expect(store.canUndo, isFalse);
  });

  test('following from a profile removes its cached discovery card without refetch', () async {
    var loads = 0;
    final store = GroupDiscoveryStore(storage: MemoryJsonStore());
    addTearDown(store.destroy);
    await store.load(
      sources: [
        source(() async {
          loads++;
          return [account('1', handle: 'newfollow'), account('2')];
        }),
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

  test('a superseded source request cannot replace newer results', () async {
    final older = Completer<List<DiscoveryAccount>>();
    final newer = Completer<List<DiscoveryAccount>>();
    final store = GroupDiscoveryStore(storage: MemoryJsonStore());
    addTearDown(store.destroy);
    final first = store.load(sources: [source(() => older.future)], followed: {}, groupName: 'Old');
    final second = store.load(sources: [source(() => newer.future)], followed: {}, groupName: 'New');
    older.complete([account('old')]);
    await first;
    expect(store.state.accounts, isEmpty);
    expect(store.isLoading, isTrue);
    newer.complete([account('new')]);
    await second;
    expect(store.state.accounts.single.id, 'new');
    expect(store.isLoading, isFalse);
  });

  test('a superseded AI response cannot replace newer discovery', () async {
    final reply = Completer<String>();
    final store = GroupDiscoveryStore(storage: MemoryJsonStore(), chat: (_, _) => reply.future);
    addTearDown(store.destroy);
    await store.load(sources: [source(() async => [account('old')])], followed: {}, groupName: 'Old');
    final ranking = store.rerankWithAi(ai);
    await store.load(sources: [source(() async => [account('new')])], followed: {}, groupName: 'New');
    reply.complete('{"ids":["x:old"]}');
    await ranking;
    expect(store.state.accounts.single.id, 'new');
    expect(store.state.usedAi, isFalse);
    expect(store.state.loadingMore, isFalse);
  });

  test('following during a source request excludes its eventual result', () async {
    final response = Completer<List<DiscoveryAccount>>();
    final store = GroupDiscoveryStore(storage: MemoryJsonStore());
    addTearDown(store.destroy);
    final loading = store.load(sources: [source(() => response.future)], followed: {}, groupName: 'Space');
    store.excludeFollowed({'x:followed'});
    response.complete([account('1', handle: 'followed'), account('2')]);
    await loading;
    expect(store.state.accounts.single.id, '2');
  });

  test('closing discovery retires pending sources', () async {
    final response = Completer<List<DiscoveryAccount>>();
    final store = GroupDiscoveryStore(storage: MemoryJsonStore());
    final loading = store.load(sources: [source(() => response.future)], followed: {}, groupName: 'Space');
    await store.destroy();
    response.complete([account('1')]);
    await loading;
    expect(store.state.accounts, isEmpty);
  });

  testWidgets('a finished source paints before a stalled one times out, and survives it', (tester) async {
    final store = GroupDiscoveryStore(storage: MemoryJsonStore());
    final loading = store.load(
      sources: [
        source(() => Completer<List<DiscoveryAccount>>().future, kind: DiscoverySource.bluesky, members: 10),
        source(() async => [account('available')], members: 5),
      ],
      followed: {},
      groupName: 'Space',
    );
    // Let preference restoration and the quick source finish before any deadline.
    await tester.pump();
    await tester.pump();
    expect(store.state.accounts.single.id, 'available');
    expect(store.isLoading, isFalse);
    expect(store.state.loadingMore, isTrue);
    expect(store.state.coverage, (read: 5, total: 15));
    await tester.pump(const Duration(seconds: 61));
    await loading;
    expect(store.isLoading, isFalse);
    expect(store.state.loadingMore, isFalse);
    expect(store.state.failures[DiscoverySource.bluesky], isA<TimeoutException>());
    expect(store.state.accounts.single.id, 'available');
    await store.destroy();
  });

  test('reporting a setup failure retires an older pending request', () async {
    final response = Completer<List<DiscoveryAccount>>();
    final store = GroupDiscoveryStore(storage: MemoryJsonStore());
    addTearDown(store.destroy);
    final loading = store.load(sources: [source(() => response.future)], followed: {}, groupName: 'Space');
    store.fail(StateError('Missing source'));
    response.complete([account('old')]);
    await loading;
    expect(store.error, isA<StateError>());
    expect(store.isLoading, isFalse);
    expect(store.state.accounts, isEmpty);
    await store.load(sources: [source(() async => [account('new')])], followed: {}, groupName: 'Space');
    expect(store.error, isNull);
    expect(store.state.accounts.single.id, 'new');
  });
}
