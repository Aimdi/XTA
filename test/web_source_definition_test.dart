import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';

import 'support/web_source_fixtures.dart';

WebSourceFile _read(Map<String, Object?> json) => readWebSource(jsonEncode(json));

Matcher _refused(WebSourceRefusal refusal, String detail) => isA<RefusedWebSource>()
    .having((file) => file.refusal, 'refusal', refusal)
    .having((file) => file.detail, 'detail', detail);

Map<String, Object?> _with(Map<String, Object?> json, String key, Object? value) => {...json, key: value};

Map<String, Object?> _withFeed(Map<String, Object?> feed) => _with(linksSourceJson(), 'feeds', [feed]);

void main() {
  test('a valid source is read with its defaults merged into each list', () {
    final source = sourceFrom(linksSourceJson());

    expect((source.id, source.version, source.name, source.layout), ('lobsters', 1, 'Lobsters', WebSourceLayout.links));
    expect(source.color, 0xFFAC130D);
    expect(source.feeds.map((feed) => feed.id), ['hottest', 'newest']);
    expect(source.feeds.first.type, WebSourcePageType.json);
    expect(source.feeds.first.fields[WebSourceField.title], 'title');
    expect(source.search?.url, contains('{query}'));
    expect(source.list('newest')?.name, 'Newest');
    expect(source.list('search'), same(source.search));
  });

  test('every fixture shape is a valid source', () {
    for (final json in [postsSourceJson(), htmlSourceJson(), gallerySourceJson()]) {
      expect(_read(json), isA<ReadWebSource>(), reason: '${json['id']}');
    }
  });

  test('a list overrides the fields it names and keeps the rest', () {
    final source = sourceFrom(
      _withFeed({
        'id': 'a',
        'name': 'A',
        'url': 'https://lobste.rs/a.json',
        'fields': {'title': 'headline'},
      }),
    );

    expect(source.feeds.single.fields[WebSourceField.title], 'headline');
    expect(source.feeds.single.fields[WebSourceField.link], 'url');
  });

  group('refuses', () {
    test('what is not a source file', () {
      expect(readWebSource('not json'), _refused(WebSourceRefusal.unreadable, ''));
      expect(readWebSource('[1, 2]'), _refused(WebSourceRefusal.unreadable, ''));
      expect(readWebSource('x' * (webSourceMaxFileBytes + 1)), _refused(WebSourceRefusal.tooLarge, '64 KB'));
    });

    test('a newer format, and a missing one', () {
      expect(_read(_with(linksSourceJson(), 'format', 2)), _refused(WebSourceRefusal.newerFormat, 'format 2'));
      expect(_read({...linksSourceJson()}..remove('format')), _refused(WebSourceRefusal.invalid, 'format'));
    });

    test('a bad id, version, name or layout', () {
      expect(_read(_with(linksSourceJson(), 'id', 'Bad Id')), _refused(WebSourceRefusal.invalid, 'id'));
      expect(_read(_with(linksSourceJson(), 'version', 0)), _refused(WebSourceRefusal.invalid, 'version'));
      expect(_read(_with(linksSourceJson(), 'name', ' ')), _refused(WebSourceRefusal.invalid, 'name'));
      expect(_read(_with(linksSourceJson(), 'layout', 'video')), _refused(WebSourceRefusal.invalid, 'layout'));
    });

    test('hosts that are missing, too many or malformed', () {
      expect(_read(_with(linksSourceJson(), 'hosts', [])), _refused(WebSourceRefusal.invalid, 'hosts'));
      expect(
        _read(_with(linksSourceJson(), 'hosts', [for (var i = 0; i < 9; i++) 'h$i.org'])),
        _refused(WebSourceRefusal.tooLarge, 'hosts: 8'),
      );
      expect(_read(_with(linksSourceJson(), 'hosts', ['lobste.rs/x'])), _refused(WebSourceRefusal.invalid, 'hosts[0]'));
    });

    test('an address off its hosts, without HTTPS, or with a port or credentials', () {
      expect(
        _read(_with(linksSourceJson(), 'homepage', 'https://elsewhere.org')),
        _refused(WebSourceRefusal.insecure, 'homepage'),
      );
      for (final url in ['http://lobste.rs/{page}.json', 'https://lobste.rs:8443/x', 'https://a@lobste.rs/x']) {
        expect(
          _read(_withFeed({'id': 'a', 'name': 'A', 'url': url})),
          _refused(WebSourceRefusal.insecure, 'feeds[0].url'),
          reason: url,
        );
      }
    });

    test('a subdomain only when its hosts allow subdomains', () {
      final feed = {'id': 'a', 'name': 'A', 'url': 'https://api.lobste.rs/a.json'};
      expect(_read(_withFeed(feed)), _refused(WebSourceRefusal.insecure, 'feeds[0].url'));
      expect(
        _read({
          ..._withFeed(feed),
          'hosts': ['*.lobste.rs'],
        }),
        isA<ReadWebSource>(),
      );
    });

    test('lists that are missing, too many, repeated or unknown in their address', () {
      expect(_read(_with(linksSourceJson(), 'feeds', [])), _refused(WebSourceRefusal.invalid, 'feeds'));
      final feed = {'id': 'a', 'name': 'A', 'url': 'https://lobste.rs/a.json'};
      expect(
        _read(_with(linksSourceJson(), 'feeds', List.filled(13, feed))),
        _refused(WebSourceRefusal.tooLarge, 'feeds: 12'),
      );
      expect(_read(_with(linksSourceJson(), 'feeds', [feed, feed])), _refused(WebSourceRefusal.invalid, 'feeds[1].id'));
      expect(
        _read(_withFeed({...feed, 'url': 'https://lobste.rs/{user}.json'})),
        _refused(WebSourceRefusal.invalid, 'feeds[0].url'),
      );
    });

    test('a search without a query', () {
      expect(
        _read(_with(linksSourceJson(), 'search', {'url': 'https://lobste.rs/search.json'})),
        _refused(WebSourceRefusal.invalid, 'search.url'),
      );
    });

    test('fields that cannot name or find a post, or a gallery without pictures', () {
      expect(
        _read(_withFeed({'id': 'a', 'name': 'A', 'url': 'https://lobste.rs/a.json', 'fields': {}})..['fields'] = {}),
        _refused(WebSourceRefusal.invalid, 'feeds[0].fields'),
      );
      final gallery = {...gallerySourceJson()};
      final feed = Map<String, Object?>.from((gallery['feeds']! as List).single as Map);
      feed['fields'] = {'id': 'data[0].nasa_id', 'title': 'data[0].title'};
      expect(
        _read({
          ...gallery,
          'feeds': [feed],
        }),
        _refused(WebSourceRefusal.invalid, 'feeds[0].fields'),
      );
    });

    test('a rule this page type cannot read', () {
      expect(
        _read(_withFeed({'id': 'a', 'name': 'A', 'url': 'https://lobste.rs/a.json', 'items': 'a..b'})),
        _refused(WebSourceRefusal.invalid, 'feeds[0].items'),
      );
      final html = {...htmlSourceJson(), 'items': 'article[['};
      expect(_read(html), _refused(WebSourceRefusal.invalid, 'feeds[0].items'));
      final field = {
        ...htmlSourceJson(),
        'fields': {...(htmlSourceJson()['fields']! as Map), 'title': 'h1[['},
      };
      expect(_read(field), _refused(WebSourceRefusal.invalid, 'feeds[0].fields.title'));
    });

    test('an article address that names no field of the post', () {
      expect(
        _read(_with(postsSourceJson(), 'article', {'url': 'https://lemmy.world/api/v3/post', 'html': 'post.body'})),
        _refused(WebSourceRefusal.invalid, 'article.url'),
      );
      expect(
        _read(_with(postsSourceJson(), 'article', {'url': 'https://lemmy.world/{nope}', 'html': 'post.body'})),
        _refused(WebSourceRefusal.invalid, 'article.url'),
      );
    });
  });

  test('a host check takes listed hosts and their subdomains when asked', () {
    expect(webSourceHostAllowed(['lobste.rs'], Uri.parse('https://lobste.rs/a')), isTrue);
    expect(webSourceHostAllowed(['lobste.rs'], Uri.parse('https://evil-lobste.rs/a')), isFalse);
    expect(webSourceHostAllowed(['*.lobste.rs'], Uri.parse('https://a.b.lobste.rs/a')), isTrue);
    expect(webSourceHostAllowed(['*.lobste.rs'], Uri.parse('https://lobste.rs/a')), isTrue);
    expect(webSourceHostAllowed(['*.lobste.rs'], Uri.parse('https://notlobste.rs/a')), isFalse);
    expect(webSourceHostAllowed(['lobste.rs'], Uri.parse('http://lobste.rs/a')), isFalse);
  });
}
