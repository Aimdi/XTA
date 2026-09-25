import 'dart:async';
import 'dart:convert';

import 'package:ffcache/ffcache.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_article_cache.dart';
import 'package:xta/plugins/substack/substack_article_navigation.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_reader_store.dart';

SubstackPost post(String title, {String domain = 'example.substack.com', String slug = 'article', bool paid = false}) =>
    SubstackPost(
      id: '42',
      title: title,
      slug: slug,
      publicationBaseUrl: 'https://$domain',
      publicationName: 'Example',
      bodyHtml: '<h2>$title</h2><p>Already available public writing.</p>',
      audience: paid ? 'only_paid' : 'everyone',
    );

class MemoryCache implements FFCache {
  final entries = <String, dynamic>{};
  bool fail = false;
  @override
  Future<bool> has(String key) async => entries.containsKey(key);
  @override
  Future<dynamic> getJSON(String key) async => entries[key];
  @override
  Future<void> setJSONWithTimeout(String key, dynamic value, Duration timeout) async {
    if (fail) throw StateError('Storage unavailable');
    entries[key] = value;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  test('refresh keeps the existing readable document after a network failure', () async {
    final store = SubstackReaderStore(post('Original'));
    final document = SubstackArticleDocument.parse(store.state.post.bodyHtml!);
    store.change(document: document, loading: false);
    final pending = Completer<SubstackPost>();
    final load = store.load(() => pending.future, refresh: true);
    expect(store.state.refreshing, isTrue);
    expect(store.state.loading, isFalse);
    expect(store.state.document, same(document));
    pending.completeError(StateError('Offline'));
    expect(await load, isNull);
    expect(store.state.post.title, 'Original');
    expect(store.state.document, same(document));
    expect(store.state.error, isNull);
    expect(store.state.refreshError, isA<StateError>());
    expect(store.state.refreshing, isFalse);
    await store.load(() async => post('Updated'), refresh: true);
    expect(store.state.post.title, 'Updated');
    expect(store.state.refreshError, isNull);
    await store.destroy();
  });

  test('latest request wins when older reads finish last', () async {
    final store = SubstackReaderStore(post('Original'));
    final old = Completer<SubstackPost>();
    final pending = store.load(() => old.future);
    final oldGeneration = store.generation;
    await store.load(() async => post('New'));
    old.complete(post('Old'));
    expect(await pending, isNull);
    expect(store.state.post.title, 'New');
    expect(store.accepts(oldGeneration), isFalse);
    await store.destroy();
  });

  test('initial failure is retryable and stale errors cannot replace success', () async {
    final store = SubstackReaderStore(post('Original'));
    await store.load(() => Future.error(StateError('Unavailable')));
    expect(store.state.error, isA<StateError>());
    expect(store.state.loading, isFalse);
    final old = Completer<SubstackPost>();
    final pending = store.load(() => old.future);
    await store.load(() async => post('Recovered'));
    old.completeError(StateError('Older failure'));
    await pending;
    expect(store.state.error, isNull);
    expect(store.state.post.title, 'Recovered');
    await store.destroy();
  });

  test('in-flight reads safely stop after store disposal', () async {
    final store = SubstackReaderStore(post('Original'));
    final pending = Completer<SubstackPost>();
    final read = store.load(() => pending.future);
    await store.destroy();
    pending.complete(post('Too late'));
    expect(await read, isNull);
    var called = false;
    await store.load(() async {
      called = true;
      return post('Never');
    });
    expect(called, isFalse);
  });

  test('navigation store restores contents after clearing a local query', () async {
    final store = SubstackArticleNavigationStore(SubstackArticleDocument.parse('<h2>Title</h2><p>Useful text</p>'));
    expect(store.results.single.text, 'Title');
    store.search('useful');
    expect(store.results.single.text, 'Useful text');
    store.search('  ');
    expect(store.results.single.text, 'Title');
    await store.destroy();
  });

  test('article cache isolates full custom domain, scheme and case-sensitive slug', () async {
    final cache = SubstackArticleCache(cache: MemoryCache());
    final original = post('One', domain: 'example.org', slug: 'Article');
    await cache.put(original);
    expect((await cache.get(original.publication, 'Article'))?.title, 'One');
    expect(await cache.get(post('Two', domain: 'example.net').publication, 'Article'), isNull);
    expect(await cache.get(original.publication, 'article'), isNull);
    const httpPublication = SubstackPublication(subdomain: 'example', name: 'Example', baseUrl: 'http://example.org');
    expect(await cache.get(httpPublication, 'Article'), isNull);
  });

  test('cache preserves the paid-preview boundary and rejects the wrong article', () async {
    final memory = MemoryCache();
    final cache = SubstackArticleCache(cache: memory);
    final preview = post('Preview', paid: true);
    await cache.put(preview);
    final loaded = await cache.get(preview.publication, preview.slug);
    expect(loaded!.isPaywalled, isTrue);
    expect(loaded.bodyHtml, preview.bodyHtml);
    final key = SubstackArticleCache.keyFor(preview.publication, preview.slug);
    memory.entries[key] = jsonEncode({'slug': 'other', 'body_html': 'Other writing'});
    expect(await cache.get(preview.publication, preview.slug), isNull);
  });

  test('malformed or unavailable optional cache never breaks reading', () async {
    final memory = MemoryCache();
    final cache = SubstackArticleCache(cache: memory);
    final article = post('Title');
    final key = SubstackArticleCache.keyFor(article.publication, article.slug);
    for (final value in ['not json', '[]', 'null', '42', null]) {
      memory.entries[key] = value;
      expect(await cache.get(article.publication, article.slug), isNull);
    }
    memory.fail = true;
    await cache.put(article);
  });
}
