import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_comments_store.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_rss.dart';
import 'package:xta/plugins/substack/substack_store.dart';

const _base = 'https://fieldnotes.substack.com';
const _fieldNotes = SubstackPublication(subdomain: 'fieldnotes', baseUrl: _base, name: 'Field Notes');

SubstackPost _api(String slug, {int id = 4242}) => SubstackPost.fromJson(
  {'id': id, 'slug': slug, 'title': 'Title of $slug', 'canonical_url': '$_base/p/$slug'},
  publicationBaseUrl: _base,
  publicationName: 'Field Notes',
);

SubstackPost _rss(String slug) => parseSubstackRss(
  '<rss version="2.0"><channel><title>Field Notes</title><generator>Substack</generator>'
  '<item><title>Title of $slug</title><link>$_base/p/$slug</link>'
  '<pubDate>Fri, 25 Sep 2026 10:00:00 GMT</pubDate><description>Hi</description></item></channel></rss>',
  publicationBaseUrl: _base,
  publicationName: 'Field Notes',
).posts.single;

class _Publications extends SubstackPublicationsStore {
  _Publications(List<SubstackPublication> pubs) : super(PrefServiceCache(cache: {})) {
    update(pubs);
  }
}

class _Client extends SubstackClient {
  _Client() : super(httpClient: MockClient((_) async => http.Response('[]', 200)));

  final commentCalls = <String>[];
  final postLookups = <String>[];

  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) async => [
    for (var i = 0; i < limit; i++) offset == 0 ? _api('post-$i', id: 1000 + i) : _rss('post-${i + (offset - limit)}'),
  ];

  @override
  Future<SubstackPost> fetchPost(SubstackPublication publication, String slug) async {
    postLookups.add(slug);
    return _api(slug, id: 777);
  }

  @override
  Future<List<SubstackComment>> fetchComments(SubstackPublication publication, String postId) async {
    commentCalls.add(postId);
    return const [];
  }
}

void main() {
  group('substackPostKey', () {
    test('is the canonical URL without scheme, www, query, fragment or trailing slash', () {
      const post = SubstackPost(
        id: '1',
        title: 'Hello',
        slug: 'hello',
        canonicalUrl: 'https://www.platformer.news/p/hello/?utm_source=x#comments',
        publicationBaseUrl: 'https://platformer.substack.com',
        publicationName: 'Platformer',
      );
      expect(substackPostKey(post), 'platformer.news/p/hello');
    });

    test('falls back to the publication host and slug, then to the id', () {
      const bySlug = SubstackPost(
        id: 'x',
        title: 'Hello',
        slug: 'hello',
        publicationBaseUrl: 'https://www.example.com',
        publicationName: 'Example',
      );
      const byId = SubstackPost(id: '99', title: 'Note', slug: '', publicationBaseUrl: '', publicationName: '');
      expect(substackPostKey(bySlug), 'example.com/p/hello');
      expect(substackPostKey(byId), '99');
    });

    test('tells apart two publications that use the same slug', () {
      const other = SubstackPost(
        id: 'welcome',
        title: 'Welcome',
        slug: 'welcome',
        publicationBaseUrl: 'https://harbour.substack.com',
        publicationName: 'Harbour',
      );
      expect(substackPostKey(other), isNot(substackPostKey(_rss('welcome'))));
      expect(sameSubstackPost(other, _rss('welcome')), isFalse);
    });

    test('the API copy and the RSS copy of a post are the same post', () {
      expect(_rss('hello').id, isNot(_api('hello').id));
      expect(sameSubstackPost(_rss('hello'), _api('hello')), isTrue);
      expect(sameSubstackPost(_api('hello'), _api('renamed')), isTrue, reason: 'the same numeric id');
    });
  });

  group('read state', () {
    test('reading one copy marks the other, and unread clears both', () async {
      final store = SubstackReadStore(PrefServiceCache(cache: {}));
      addTearDown(store.destroy);

      await store.markPostRead(_rss('hello'));
      expect(store.isPostRead(_api('hello')), isTrue);
      expect(store.isPostRead(_api('other', id: 7)), isFalse);

      await store.markPostUnread(_api('hello'));
      expect(store.isPostRead(_rss('hello')), isFalse);
      expect(store.isPostRead(_api('hello')), isFalse);

      await store.markPostsRead([_api('hello'), _rss('second')]);
      expect(store.isPostRead(_rss('hello')), isTrue);
      expect(store.isPostRead(_api('second', id: 8)), isTrue);
      expect(store.state, hasLength(2), reason: 'one entry per read post');
    });

    test('ids stored before the change still count', () async {
      final prefs = PrefServiceCache(cache: {});
      await prefs.set(optionPluginSubstackReadIds, readIdsToPrefs(['4242', 'legacy']));
      final store = SubstackReadStore(prefs);
      addTearDown(store.destroy);
      await store.load();

      expect(store.isPostRead(_api('hello')), isTrue);
      expect(store.isPostRead(_rss('legacy')), isTrue);
      expect(substackPostIsRead(store.state, _rss('hello')), isFalse, reason: 'a numeric id names no slug');
    });
  });

  test('a like or a save on one copy is on the other, also for entries stored before', () async {
    final prefs = PrefServiceCache(cache: {});
    await prefs.set(optionPluginSubstackSavedPosts, SubstackPost.listToPrefs([_api('kept')]));
    final likes = SubstackLikesStore(PrefServiceCache(cache: {}));
    final saved = SubstackSavedStore(prefs);
    addTearDown(likes.destroy);
    addTearDown(saved.destroy);
    await saved.load();

    await likes.toggle(_api('hello'));
    expect(likes.contains(_rss('hello')), isTrue);
    await likes.toggle(_rss('hello'));
    expect(likes.state, isEmpty, reason: 'the RSS copy unlikes the API copy');

    expect(saved.contains(_rss('kept')), isTrue);
  });

  test('an RSS page after an API page repeats no post, and the API copy stays', () async {
    final store = SubstackFeedStore(_Client(), _Publications([_fieldNotes]));
    addTearDown(store.destroy);

    await store.refresh(force: true);
    await store.loadMore();

    final slugs = store.allPosts.map((post) => post.slug).toList();
    expect(slugs.toSet(), hasLength(slugs.length), reason: 'duplicates before the fix');
    expect(store.allPosts.firstWhere((post) => post.slug == 'post-0').id, '1000');
  });

  test('comments of an RSS copy are asked for under the numeric id', () async {
    final client = _Client();
    final store = SubstackCommentsStore(client, _rss('hello'));
    addTearDown(store.destroy);

    await store.refresh();

    expect(client.postLookups, ['hello']);
    expect(client.commentCalls, ['777'], reason: 'the slug was sent before the fix');
  });
}
