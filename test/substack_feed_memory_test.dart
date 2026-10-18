import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_feed_memory.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_plugin.dart';
import 'package:xta/plugins/substack/substack_store.dart';

SubstackPublication _pub(String id) =>
    SubstackPublication(subdomain: id, baseUrl: 'https://$id.substack.com', name: id);

SubstackPost _post(SubstackPublication pub, int i) => SubstackPost(
  id: '${pub.id}-$i',
  title: '${pub.id} post $i',
  slug: '${pub.id}-post-$i',
  postDate: DateTime.utc(2026, 9, 1 + i).toIso8601String(),
  bodyHtml: '<p>Body</p>',
  publicationBaseUrl: pub.baseUrl,
  publicationName: pub.name,
);

class _Publications extends SubstackPublicationsStore {
  _Publications(super.prefs, List<SubstackPublication> pubs) {
    update(pubs);
  }
}

class _Client extends SubstackClient {
  _Client() : super(httpClient: MockClient((_) async => http.Response('[]', 200)));

  final asked = <String>[];

  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) async {
    asked.add(publication.id);
    return [for (var i = 0; i < limit; i++) _post(publication, offset + i)];
  }
}

Future<({SubstackFeedStore store, _Client client})> _start(
  PrefServiceCache prefs,
  List<SubstackPublication> pubs,
) async {
  final client = _Client();
  final store = SubstackFeedStore(client, _Publications(prefs, pubs));
  addTearDown(store.destroy);
  return (store: store, client: client);
}

Future<void> _settle() => Future<void>.delayed(Duration.zero);

void main() {
  final alpha = _pub('alpha');
  final beta = _pub('beta');
  final gamma = _pub('gamma');

  test('the memory keeps first pages without bodies, and an odd value is empty', () {
    final memory = SubstackFeedMemory({
      'alpha': (answeredAt: DateTime.utc(2026, 10, 14, 9), posts: [for (var i = 0; i < 12; i++) _post(alpha, i)]),
    });
    final back = SubstackFeedMemory.decode(memory.encode()).pages['alpha']!;

    expect(back.answeredAt, DateTime.utc(2026, 10, 14, 9));
    expect(back.posts.map((post) => post.id), [for (var i = 0; i < substackFeedPageSize; i++) 'alpha-$i']);
    expect(back.posts.first.bodyHtml, isNull);
    for (final odd in [null, '', 'not json', '{"v":2,"pages":{}}', '{"v":1,"pages":[]}', '{"v":1,"pages":{"a":{}}}']) {
      expect(SubstackFeedMemory.decode(odd).pages, isEmpty, reason: '$odd');
    }
  });

  test('a restart inside the cache window paints the feed and asks Substack nothing', () async {
    final prefs = PrefServiceCache(cache: {});
    final before = await _start(prefs, [alpha, beta]);
    await before.store.refresh(force: true);
    await _settle();

    final after = await _start(prefs, [alpha, beta]);
    expect(after.store.restore(), isTrue);
    expect(after.store.allPosts.map((post) => post.id), before.store.allPosts.map((post) => post.id));
    await after.store.refresh();

    expect(after.client.asked, isEmpty);
    expect(after.store.restore(), isFalse, reason: 'once per store');
  });

  test('only publications that answered too long ago are asked again; a forced refresh asks all', () async {
    final prefs = PrefServiceCache(cache: {});
    final stale = DateTime.now().subtract(kAccountPostsCacheTtl * 2);
    await prefs.set(
      optionPluginSubstackFeedMemory,
      SubstackFeedMemory({
        'alpha\nhttps://alpha.substack.com': (answeredAt: stale, posts: [_post(alpha, 0)]),
        'beta\nhttps://beta.substack.com': (answeredAt: DateTime.now(), posts: [_post(beta, 0)]),
        'gamma\nhttps://gamma.substack.com': (answeredAt: DateTime.now(), posts: [_post(gamma, 0)]),
      }).encode(),
    );
    final run = await _start(prefs, [alpha, beta]);

    expect(run.store.restore(), isTrue);
    expect(run.store.allPosts.map((post) => post.publicationName).toSet(), {
      'alpha',
      'beta',
    }, reason: 'gamma unfollowed');
    await run.store.refresh();
    expect(run.client.asked, ['alpha']);

    await run.store.refresh(force: true);
    expect(run.client.asked.skip(1), unorderedEquals(['alpha', 'beta']));
  });

  test('resetting the plugin forgets the feed', () async {
    final prefs = PrefServiceCache(cache: {});
    final run = await _start(prefs, [alpha]);
    await run.store.refresh(force: true);
    await _settle();
    expect(SubstackFeedMemory.decode(prefs.get<String>(optionPluginSubstackFeedMemory)).pages, isNotEmpty);

    await SubstackPlugin().resetPreferences(prefs);

    expect(SubstackFeedMemory.decode(prefs.get<String>(optionPluginSubstackFeedMemory)).pages, isEmpty);
  });
}
