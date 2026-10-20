import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_links.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/plugin_url.dart';

BooruPostRef? _ref(String url, [List<BooruPreset> sites = booruPresets]) => booruPostRefOf(Uri.parse(url), sites);

void main() {
  group('booru post links', () {
    test('each engine opens its own post page form', () {
      expect(_ref('https://danbooru.donmai.us/posts/7'), (
        host: 'https://danbooru.donmai.us',
        engine: BooruEngine.danbooru,
        id: '7',
      ));
      expect(_ref('https://e621.net/posts/12?q=wolf'), (host: 'https://e621.net', engine: BooruEngine.e621, id: '12'));
      expect(_ref('https://yande.re/post/show/3'), (host: 'https://yande.re', engine: BooruEngine.moebooru, id: '3'));
      expect(_ref('https://konachan.com/post/show/4/long_hair-original'), (
        host: 'https://konachan.com',
        engine: BooruEngine.moebooru,
        id: '4',
      ));
      expect(_ref('https://safebooru.org/index.php?page=post&s=view&id=5&tags=solo'), (
        host: 'https://safebooru.org',
        engine: BooruEngine.gelbooruV2,
        id: '5',
      ));
    });

    test('a post page is the one the post itself links to', () {
      for (final preset in booruPresets) {
        final post = BooruPost(
          id: '42',
          host: preset.host,
          engine: preset.engine.id,
          tags: const [],
          rating: null,
          score: 0,
          width: 1,
          height: 1,
          previewUrl: null,
          sampleUrl: null,
          fileUrl: null,
          fileExt: null,
          source: null,
          createdAt: null,
        );
        expect(_ref(post.hostPageUrl!), (host: preset.host, engine: preset.engine, id: '42'), reason: preset.name);
      }
    });

    test('www, http and letter case do not matter', () {
      expect(_ref('http://www.danbooru.donmai.us/posts/7')?.id, '7');
      expect(_ref('https://sakugabooru.com/post/show/8')?.host, 'https://www.sakugabooru.com');
      expect(_ref('https://Rule34.xxx/index.php?page=post&s=view&id=9')?.host, 'https://rule34.xxx');
    });

    test('other pages and hosts are left alone', () {
      expect(_ref('https://danbooru.donmai.us/posts'), isNull);
      expect(_ref('https://danbooru.donmai.us/posts?tags=solo'), isNull);
      expect(_ref('https://danbooru.donmai.us/posts/7.json'), isNull);
      expect(_ref('https://danbooru.donmai.us/posts/7/edit'), isNull);
      expect(_ref('https://danbooru.donmai.us/wiki_pages/solo'), isNull);
      expect(_ref('https://yande.re/post/show'), isNull);
      expect(_ref('https://yande.re/posts/3'), isNull);
      expect(_ref('https://safebooru.org/index.php?page=post&s=list&tags=solo'), isNull);
      expect(_ref('https://safebooru.org/index.php?page=post&s=view'), isNull);
      expect(_ref('https://example.com/posts/7'), isNull);
      expect(_ref('https://notdanbooru.donmai.us/posts/7'), isNull);
    });

    test('a site added in settings is known by its own path', () {
      const custom = BooruPreset(
        id: 'custom:example.com',
        name: 'Example',
        engine: BooruEngine.danbooru,
        host: 'https://example.com/booru',
      );
      expect(_ref('https://example.com/booru/posts/7', [custom])?.host, 'https://example.com/booru');
      expect(_ref('https://example.com/posts/7', [custom]), isNull);
    });

    test('the known sites are the built-in ones and those added in settings', () {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginBooruCustomSites: jsonEncode([
            {'id': 'custom:example.com', 'name': 'Example', 'engine': 'moebooru', 'host': 'https://example.com'},
          ]),
        },
      );
      final sites = booruKnownSites(prefs);
      expect(sites.length, booruPresets.length + 1);
      expect(sites.last.host, 'https://example.com');
    });

    test('parsePluginLink knows the built-in boorus', () {
      expect(
        parsePluginLink('https://danbooru.donmai.us/posts/7'),
        isA<BooruPostLink>().having((link) => link.ref.id, 'id', '7'),
      );
      expect(parsePluginLink('https://example.com/posts/7'), isNull);
      const custom = BooruPreset(id: 'c', name: 'C', engine: BooruEngine.e621, host: 'https://example.com');
      expect(parsePluginLink('https://example.com/posts/7', booruSites: [custom]), isA<BooruPostLink>());
    });
  });

  group('post by link', () {
    BooruClient client(http.Client http, {Map<String, Object> prefs = const {}}) => BooruClient(
      PrefServiceCache(
        cache: {
          optionPluginBooruEngine: 'danbooru',
          optionPluginBooruHost: 'https://danbooru.donmai.us',
          optionPluginBooruMaxRating: 's',
          ...prefs,
        },
      ),
      httpClient: http,
    );

    Map<String, Object> post(int id, {String rating = 's', String tags = 'solo'}) => {
      'id': id,
      'rating': rating,
      'tag_string': tags,
      'file_url': 'https://cdn.example/$id.png',
    };

    test("asks the link's own site for the post by id", () async {
      final asked = <Uri>[];
      final found = await client(
        MockClient((request) async {
          asked.add(request.url);
          return http.Response(jsonEncode({'posts': []}), 200);
        }),
      ).postAt((host: 'https://e621.net', engine: BooruEngine.e621, id: '12'));
      expect(found, isNull);
      expect(asked.single.host, 'e621.net');
      expect(asked.single.queryParameters['tags']?.split(' '), contains('id:12'));
    });

    test('the post comes back even when the blacklist would hide it in a feed', () async {
      final found = await client(
        MockClient((_) async => http.Response(jsonEncode([post(7, tags: 'solo spoilers')]), 200)),
        prefs: {
          optionPluginBooruMutedTags: jsonEncode(['spoilers']),
        },
      ).postAt((host: 'https://danbooru.donmai.us', engine: BooruEngine.danbooru, id: '7'));
      expect(found?.id, '7');
      expect(found?.host, 'https://danbooru.donmai.us');
    });

    test('a post over the rating cap is not shown', () async {
      final found = await client(
        MockClient((_) async => http.Response(jsonEncode([post(7, rating: 'e')]), 200)),
      ).postAt((host: 'https://danbooru.donmai.us', engine: BooruEngine.danbooru, id: '7'));
      expect(found, isNull);
    });

    test('another post the site sends back is not taken for it', () async {
      final found = await client(
        MockClient((_) async => http.Response(jsonEncode([post(8)]), 200)),
      ).postAt((host: 'https://danbooru.donmai.us', engine: BooruEngine.danbooru, id: '7'));
      expect(found, isNull);
    });

    test("the saved login goes only to its own site", () async {
      final asked = <Uri>[];
      final withLogin = client(
        MockClient((request) async {
          asked.add(request.url);
          return http.Response('[]', 200);
        }),
        prefs: {
          optionPluginBooruLogin: 'reader',
          optionPluginBooruApiKey: 'secret',
          optionPluginBooruCredentialsHost: 'https://danbooru.donmai.us',
        },
      );
      await withLogin.postAt((host: 'https://danbooru.donmai.us', engine: BooruEngine.danbooru, id: '7'));
      await withLogin.postAt((host: 'https://aibooru.online', engine: BooruEngine.danbooru, id: '7'));
      expect(asked.first.queryParameters['login'], 'reader');
      expect(asked.last.host, 'aibooru.online');
      expect(asked.last.queryParameters.containsKey('login'), isFalse);
      expect(asked.last.queryParameters.containsKey('api_key'), isFalse);
    });
  });
}
