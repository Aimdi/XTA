import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';

const _alpha = SubstackPublication(subdomain: 'alpha', baseUrl: 'https://alpha.substack.com', name: 'Alpha');
const _beta = SubstackPublication(subdomain: 'beta', baseUrl: 'https://beta.substack.com', name: 'Beta');

const _dates = {
  'alpha': {'1': '2026-09-01T10:00:00.000Z', '2': null, '3': '2026-09-03T08:00:00.000Z', '4': '2026-09-01T10:00:00Z'},
  'beta': {'5': '2026-09-01T12:00:00+02:00', '6': '2026-09-02T00:00:00.000Z', '7': null, '8': 'not a date'},
};

class _Publications extends SubstackPublicationsStore {
  _Publications(List<SubstackPublication> pubs) : super(PrefServiceCache(cache: {})) {
    update(pubs);
  }
}

class _Client extends SubstackClient {
  _Client() : super(httpClient: MockClient((_) async => http.Response('[]', 200)));

  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) async => [
    for (final MapEntry(key: id, value: date) in _dates[publication.subdomain]!.entries)
      SubstackPost(
        id: id,
        title: 'Post $id',
        slug: 'post-$id',
        postDate: date,
        publicationBaseUrl: publication.baseUrl,
        publicationName: publication.name,
      ),
  ];
}

void main() {
  test('the feed is newest first, ties by feed key, undated posts last', () async {
    final store = SubstackFeedStore(_Client(), _Publications([_alpha, _beta]));
    addTearDown(store.destroy);

    await store.refresh(force: true);

    expect(store.allPosts.map((post) => post.id), ['3', '6', '1', '4', '5', '2', '7', '8']);
  });
}
