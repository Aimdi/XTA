import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';

class _PubsStub extends SubstackPublicationsStore {
  _PubsStub(List<SubstackPublication> pubs)
    : super(PrefServiceCache(cache: {})) {
    update(pubs);
  }

  @override
  Future<void> load() async {}
}

class _ClientStub extends SubstackClient {
  var fetches = 0;

  @override
  Future<List<SubstackPost>> fetchPosts(
    SubstackPublication publication, {
    int limit = 12,
    int offset = 0,
  }) async {
    fetches++;
    return [
      SubstackPost(
        id: 'p$fetches',
        title: 'Post $fetches',
        slug: 'post-$fetches',
        publicationBaseUrl: publication.baseUrl,
        publicationName: publication.name,
      ),
    ];
  }
}

void main() {
  const pub = SubstackPublication(
    subdomain: 'example',
    baseUrl: 'https://example.substack.com',
    name: 'Example',
  );

  test('a second home-strip open inside the TTL does not refetch', () async {
    final client = _ClientStub();
    final store = SubstackFeedStore(client, _PubsStub([pub]));

    await store.refresh();
    await store.refresh();

    expect(client.fetches, 1);
    expect(store.allPosts, hasLength(1));
  });

  test('pull-to-refresh asks again even inside the TTL', () async {
    final client = _ClientStub();
    final store = SubstackFeedStore(client, _PubsStub([pub]));

    await store.refresh();
    await store.refresh(force: true);

    expect(client.fetches, 2);
  });
}
