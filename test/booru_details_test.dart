import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_detail_parse.dart';
import 'package:xta/plugins/booru/booru_endpoints.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';
import 'package:xta/plugins/booru/booru_popular.dart';
import 'package:xta/plugins/booru/booru_text.dart';

BooruPost _post({
  String id = '1',
  String engine = 'danbooru',
  List<String> tags = const ['solo', 'smile'],
  BooruRating? rating = BooruRating.general,
  Map<String, BooruTagCategory> categories = const {},
}) => BooruPost(
  id: id,
  host: 'https://example.com',
  engine: engine,
  tags: tags,
  rating: rating,
  score: 0,
  width: 1,
  height: 1,
  previewUrl: null,
  sampleUrl: null,
  fileUrl: null,
  fileExt: 'jpg',
  source: null,
  createdAt: null,
  tagCategories: categories,
);

BooruClient _client(String engine, String host, http.Client http, {Map<String, Object> prefs = const {}}) =>
    BooruClient(
      PrefServiceCache(
        cache: {
          optionPluginBooruEngine: engine,
          optionPluginBooruHost: host,
          optionPluginBooruMaxRating: 'e',
          ...prefs,
        },
      ),
      httpClient: http,
    );

void main() {
  group('post details', () {
    test('Danbooru posts carry tag kinds, file facts and family', () {
      final post = parseBooruPosts(
        [
          {
            'id': 5,
            'tag_string': 'kantoku 1girl highres',
            'tag_string_artist': 'kantoku',
            'tag_string_general': '1girl',
            'tag_string_meta': 'highres',
            'file_size': 2048,
            'md5': 'abc',
            'fav_count': 12,
            'up_score': 9,
            'down_score': -2,
            'parent_id': 4,
            'has_children': false,
          },
        ],
        engine: BooruEngine.danbooru,
        host: 'https://danbooru.donmai.us',
      ).single;

      expect(post.tagCategories, {
        'kantoku': BooruTagCategory.artist,
        '1girl': BooruTagCategory.general,
        'highres': BooruTagCategory.meta,
      });
      expect(post.fileSize, 2048);
      expect(post.md5, 'abc');
      expect(post.favCount, 12);
      expect(post.upScore, 9);
      expect(post.downScore, 2);
      expect(post.parentId, '4');
      expect(post.hasFamily, isTrue);
    });

    test('Gelbooru dates, string flags, owner and empty parent', () {
      final post = parseBooruPosts(
        {
          'post': [
            {
              'id': 3,
              'tags': 'solo',
              'created_at': 'Sat Oct 05 13:52:20 -0500 2024',
              'owner': 'uploader_1',
              'hash': 'def',
              'parent_id': 0,
              'has_children': 'true',
            },
          ],
        },
        engine: BooruEngine.gelbooruV2,
        host: 'https://gelbooru.com',
      ).single;

      expect(post.createdAt, DateTime.utc(2024, 10, 5, 18, 52, 20));
      expect(post.uploader, 'uploader_1');
      expect(post.md5, 'def');
      expect(post.parentId, isNull);
      expect(post.hasChildren, isTrue);
    });

    test('an unreadable date falls back to the change time', () {
      final post = parseBooruPosts(
        [
          {'id': 3, 'tags': 'solo', 'created_at': 'someday', 'change': 1700000000},
        ],
        engine: BooruEngine.gelbooruV2,
        host: 'https://safebooru.org',
      ).single;
      expect(post.createdAt, DateTime.fromMillisecondsSinceEpoch(1700000000 * 1000));
    });

    test('Moebooru author and children', () {
      final post = parseBooruPosts(
        [
          {'id': 8, 'tags': 'sky', 'author': 'mod', 'parent_id': null, 'has_children': true, 'file_size': 99},
        ],
        engine: BooruEngine.moebooru,
        host: 'https://yande.re',
      ).single;
      expect(post.uploader, 'mod');
      expect(post.parentId, isNull);
      expect(post.hasChildren, isTrue);
      expect(post.fileSize, 99);
    });

    test('e621 groups, votes and relationships', () {
      final post = parseBooruPosts(
        {
          'posts': [
            {
              'id': 2,
              'score': {'up': 4, 'down': -1, 'total': 3},
              'fav_count': 7,
              'file': {'size': 300, 'md5': 'aa', 'url': 'https://static1.e621.net/a.png'},
              'tags': {
                'artist': ['someone'],
                'species': ['fox'],
                'lore': ['backstory'],
                'general': ['smile'],
              },
              'relationships': {
                'parent_id': null,
                'has_children': false,
                'children': [3],
              },
            },
          ],
        },
        engine: BooruEngine.e621,
        host: 'https://e621.net',
      ).single;

      expect(post.tagCategories['someone'], BooruTagCategory.artist);
      expect(post.tagCategories['fox'], BooruTagCategory.species);
      expect(post.tagCategories['backstory'], BooruTagCategory.meta);
      expect(post.upScore, 4);
      expect(post.downScore, 1);
      expect(post.favCount, 7);
      expect(post.fileSize, 300);
      expect(post.md5, 'aa');
      expect(post.hasChildren, isTrue);
    });
  });

  group('blacklist', () {
    test('one tag hides any post that has it', () {
      expect(booruPostMuted(_post(), {'solo'}), isTrue);
      expect(booruPostMuted(_post(), {'group'}), isFalse);
    });

    test('several tags hide a post only together', () {
      expect(booruPostMuted(_post(), {'solo smile'}), isTrue);
      expect(booruPostMuted(_post(), {'solo hat'}), isFalse);
    });

    test('negation and either-of', () {
      expect(booruPostMuted(_post(), {'solo -hat'}), isTrue);
      expect(booruPostMuted(_post(), {'solo -smile'}), isFalse);
      expect(booruPostMuted(_post(), {'~hat ~smile'}), isTrue);
      expect(booruPostMuted(_post(), {'~hat ~cap'}), isFalse);
    });

    test('ratings read in the host’s own letters', () {
      final sensitive = _post(rating: BooruRating.sensitive);
      expect(booruPostMuted(sensitive, {'rating:s'}), isTrue);
      final safe = _post(engine: 'moebooru', rating: BooruRating.general);
      expect(booruPostMuted(safe, {'rating:s'}), isTrue);
      expect(booruPostMuted(_post(), {'rating:e'}), isFalse);
    });

    test('an entry with a metatag the app cannot check never hides', () {
      expect(booruPostMuted(_post(), {'solo score:<0'}), isFalse);
      expect(booruPostMuted(_post(), {'solo -score:<0'}), isFalse);
    });

    test('stored entries keep their spaces and old single tags still load', () {
      expect(parseBooruBlacklist(jsonEncode(['Blue_Sky', 'solo  Smile', ''])), {'blue_sky', 'solo smile'});
      expect(parseBooruBlacklist('not json'), isEmpty);
    });
  });

  group('endpoints', () {
    final day = BooruPopularQuery(scale: BooruPopularScale.week, date: DateTime(2026, 3, 4));

    test('popular lists per engine', () {
      const danbooru = BooruSite(engine: BooruEngine.danbooru, host: 'https://danbooru.donmai.us');
      final uri = booruPopularUri(danbooru, day, page: 2, limit: 40)!;
      expect(uri.path, '/explore/posts/popular.json');
      expect(uri.queryParameters, {'date': '2026-03-04', 'scale': 'week', 'page': '2', 'limit': '40'});

      const e621 = BooruSite(engine: BooruEngine.e621, host: 'https://e621.net');
      expect(
        booruPopularUri(e621, day, page: 1, limit: 40).toString(),
        'https://e621.net/popular.json?date=2026-03-04&scale=week',
      );

      const moebooru = BooruSite(engine: BooruEngine.moebooru, host: 'https://yande.re');
      expect(
        booruPopularUri(moebooru, day, page: 1, limit: 40).toString(),
        'https://yande.re/post/popular_by_week.json?day=4&month=3&year=2026',
      );
      final month = day.withScale(BooruPopularScale.month);
      expect(booruPopularUri(moebooru, month, page: 1, limit: 40)!.queryParameters, {'month': '3', 'year': '2026'});

      const gelbooru = BooruSite(engine: BooruEngine.gelbooruV2, host: 'https://gelbooru.com');
      expect(booruPopularUri(gelbooru, day, page: 1, limit: 40), isNull);
    });

    test('credentials are named the way each engine expects', () {
      Map<String, String> auth(BooruEngine engine) =>
          BooruSite(engine: engine, host: 'https://x.org', login: 'me', apiKey: 'k').auth;
      expect(auth(BooruEngine.danbooru), {'login': 'me', 'api_key': 'k'});
      expect(auth(BooruEngine.gelbooruV2), {'user_id': 'me', 'api_key': 'k'});
      expect(auth(BooruEngine.moebooru), {'login': 'me', 'password_hash': 'k'});
    });

    test('Rule34 is asked on its API host', () {
      const site = BooruSite(engine: BooruEngine.gelbooruV2, host: 'https://rule34.xxx');
      final uri = booruTagInfoUri(site, postId: '1', tags: ['a', 'b'])!;
      expect(uri.host, 'api.rule34.xxx');
      expect(uri.queryParameters['names'], 'a b');
      expect(uri.queryParameters['s'], 'tag');
    });

    test('one request names every tag of a post', () {
      const danbooru = BooruSite(engine: BooruEngine.danbooru, host: 'https://danbooru.donmai.us');
      expect(booruTagInfoUri(danbooru, postId: '7', tags: ['a', 'b_c'])!.queryParameters, {
        'search[name_comma]': 'a,b_c',
        'only': 'name,category,post_count',
        'limit': '2',
      });
      const e621 = BooruSite(engine: BooruEngine.e621, host: 'https://e621.net');
      expect(booruTagInfoUri(e621, postId: '7', tags: ['a', 'b'])!.queryParameters, {
        'search[name]': 'a,b',
        'limit': '2',
      });
      const moebooru = BooruSite(engine: BooruEngine.moebooru, host: 'https://yande.re');
      expect(booruTagInfoUri(moebooru, postId: '7', tags: ['a'])!.queryParameters, {
        'tags': 'id:7',
        'api_version': '2',
        'include_tags': '1',
        'limit': '1',
      });
      expect(booruTagInfoUri(danbooru, postId: '7', tags: const []), isNull);
    });

    test('wiki pages and comments', () {
      const danbooru = BooruSite(engine: BooruEngine.danbooru, host: 'https://danbooru.donmai.us');
      expect(booruWikiUri(danbooru, 'fate/stay_night')!.queryParameters, {
        'search[title]': 'fate/stay_night',
        'limit': '1',
      });
      expect(booruCommentsUri(danbooru, '9')!.queryParameters['search[post_id]'], '9');
      const moebooru = BooruSite(engine: BooruEngine.moebooru, host: 'https://yande.re');
      expect(booruCommentsUri(moebooru, '9').toString(), 'https://yande.re/comment.json?post_id=9');
      const gelbooru = BooruSite(engine: BooruEngine.gelbooruV2, host: 'https://gelbooru.com');
      expect(booruWikiUri(gelbooru, 'a'), isNull);
      expect(booruCommentsUri(gelbooru, '9'), isNull);
    });
  });

  group('detail parsers', () {
    test('Moebooru tag map; Danbooru, e621 and Gelbooru tag lists with counts', () {
      Map<String, (BooruTagCategory?, int?)> read(Object raw, BooruEngine engine) => {
        for (final MapEntry(:key, :value) in parseBooruTagInfo(raw, engine: engine).entries)
          key: (value.category, value.postCount),
      };
      expect(
        read({
          'posts': [],
          'tags': {'kantoku': 'artist', 'clouds': 'general', 'jpeg_artifacts': 'faults', 'x': 'unknown'},
        }, BooruEngine.moebooru),
        {
          'kantoku': (BooruTagCategory.artist, null),
          'clouds': (BooruTagCategory.general, null),
          'jpeg_artifacts': (BooruTagCategory.meta, null),
        },
      );
      expect(
        read({
          'tag': [
            {'name': 'kantoku', 'type': 1, 'count': 50},
            {'name': 'highres', 'type': 5, 'count': 9000000},
          ],
        }, BooruEngine.gelbooruV2),
        {'kantoku': (BooruTagCategory.artist, 50), 'highres': (BooruTagCategory.meta, 9000000)},
      );
      expect(
        read([
          {'name': 'blush', 'category': 0, 'post_count': 3890000},
        ], BooruEngine.danbooru),
        {'blush': (BooruTagCategory.general, 3890000)},
      );
      expect(
        read([
          {'name': 'fox', 'category': 5, 'post_count': 12},
        ], BooruEngine.e621),
        {'fox': (BooruTagCategory.species, 12)},
      );
      expect(read({'tags': []}, BooruEngine.e621), isEmpty);
    });

    test('wiki bodies', () {
      expect(parseBooruWiki({'title': 'a', 'body': ' text '}, tag: 'a'), 'text');
      expect(parseBooruWiki({'body': 'x', 'is_deleted': true}, tag: 'a'), isNull);
      expect(
        parseBooruWiki([
          {'title': 'blue_sky_2', 'body': 'no'},
          {'title': 'Blue Sky', 'body': 'yes'},
        ], tag: 'blue_sky'),
        'yes',
      );
      expect(parseBooruWiki([], tag: 'x'), isNull);
    });

    test('comments are oldest first and hidden ones are left out', () {
      final comments = parseBooruComments([
        {
          'id': 2,
          'body': 'second',
          'created_at': '2024-01-02T00:00:00Z',
          'creator': {'name': 'b'},
        },
        {'id': 1, 'body': 'first', 'created_at': '2024-01-01T00:00:00Z', 'creator_name': 'a', 'score': 3},
        {'id': 3, 'body': 'gone', 'is_deleted': true},
        {'id': 4, 'body': '  '},
      ]);
      expect(comments.map((c) => c.body), ['first', 'second']);
      expect(comments.map((c) => c.author), ['a', 'b']);
      expect(comments.first.score, 3);
      expect(parseBooruComments({'comments': []}), isEmpty);
      expect(
        parseBooruComments([
          {'id': 5, 'body': 'moe', 'creator': 'c'},
        ]).single.author,
        'c',
      );
    });

    test('DText and HTML read as plain text', () {
      expect(
        booruPlainText(
          'h4. Summary\r\nSee [[blue_eyes|blue eyes]] and [[red_eyes]], {{solo rating:g}}. '
          '"Site":https://x.org [b]bold[/b] [expand=More]x[/expand]\n\n\n\nEnd &amp; &lt;ok&gt;<br>line',
        ),
        'Summary\nSee blue eyes and red_eyes, solo rating:g. Site bold x\n\nEnd & <ok>\nline',
      );
    });
  });

  group('popular dates', () {
    final today = DateTime(2026, 10, 9);

    test('days, weeks and months step and never pass today', () {
      final query = BooruPopularQuery(scale: BooruPopularScale.day, date: DateTime(2026, 10, 8, 23, 59));
      expect(query.date, DateTime(2026, 10, 8));
      expect(query.step(-1, today: today).date, DateTime(2026, 10, 7));
      expect(query.step(1, today: today).date, today);
      expect(query.step(1, today: today).step(1, today: today).date, today);
      expect(query.withScale(BooruPopularScale.week).step(-1, today: today).date, DateTime(2026, 10, 1));
      final march = BooruPopularQuery(scale: BooruPopularScale.month, date: DateTime(2026, 3, 31));
      expect(march.step(-1, today: today).date, DateTime(2026, 2, 28));
      expect(query.step(1, today: today).isLatest(today: today), isTrue);
      expect(query.isLatest(today: today), isFalse);
    });
  });

  group('client', () {
    test('Gelbooru popular shows its highest-scored posts', () async {
      final asked = <Uri>[];
      final client = _client(
        'gelbooru_v2',
        'https://safebooru.org',
        MockClient((request) async {
          asked.add(request.url);
          return http.Response('[]', 200);
        }),
      );
      await client.popular(BooruPopularQuery.today());
      expect(asked.single.queryParameters['tags'], 'sort:score');
    });

    test('engines with one popular page stop after it', () async {
      var calls = 0;
      final client = _client(
        'e621',
        'https://e621.net',
        MockClient((request) async {
          calls++;
          return http.Response(jsonEncode({'posts': []}), 200);
        }),
      );
      final first = await client.popular(BooruPopularQuery.today());
      final second = await client.popular(BooruPopularQuery.today(), page: 2);
      expect(first.hasMore, isFalse);
      expect(second.posts, isEmpty);
      expect(calls, 1);
    });

    test('popular posts still pass the rating cap and blacklist', () async {
      final client = _client(
        'danbooru',
        'https://danbooru.donmai.us',
        MockClient(
          (request) async => http.Response(
            jsonEncode([
              {'id': 1, 'rating': 'e', 'tag_string': 'a'},
              {'id': 2, 'rating': 'g', 'tag_string': 'muted'},
              {'id': 3, 'rating': 'g', 'tag_string': 'kept'},
            ]),
            200,
          ),
        ),
        prefs: {
          optionPluginBooruMaxRating: 'g',
          optionPluginBooruMutedTags: jsonEncode(['muted']),
        },
      );
      final page = await client.popular(BooruPopularQuery.today());
      expect(page.posts.map((p) => p.id), ['3']);
    });

    test('a missing wiki page is no page, not an error', () async {
      final client = _client(
        'danbooru',
        'https://danbooru.donmai.us',
        MockClient((request) async => http.Response('{}', 404)),
      );
      expect(await client.wiki('nothing'), isNull);
    });

    test('Moebooru kinds come from one post lookup', () async {
      var calls = 0;
      final client = _client(
        'moebooru',
        'https://yande.re',
        MockClient((request) async {
          calls++;
          return http.Response(
            jsonEncode({
              'posts': [],
              'tags': {'kantoku': 'artist'},
            }),
            200,
          );
        }),
      );
      final info = await client.tagInfo(_post(engine: 'moebooru'));
      expect(info.keys, ['kantoku']);
      expect(info['kantoku']!.category, BooruTagCategory.artist);
      expect(calls, 1);
    });

    test('counts from the lookup join the kinds the post carries', () async {
      final client = _client(
        'danbooru',
        'https://danbooru.donmai.us',
        MockClient(
          (request) async => http.Response(
            jsonEncode([
              {'name': 'solo', 'post_count': 4330000},
            ]),
            200,
          ),
        ),
      );
      final info = await client.tagInfo(
        _post(categories: {'solo': BooruTagCategory.general, 'smile': BooruTagCategory.general}),
      );
      expect(info['solo']!.postCount, 4330000);
      expect(info['solo']!.category, BooruTagCategory.general);
      expect(info['smile']!.category, BooruTagCategory.general);
      expect(info['smile']!.postCount, isNull);
    });

    test('a failed tag lookup leaves the tags ungrouped and uncounted', () async {
      final client = _client(
        'gelbooru_v2',
        'https://safebooru.org',
        MockClient((request) async => http.Response('<xml/>', 200)),
      );
      expect(await client.tagInfo(_post(engine: 'gelbooru_v2')), isEmpty);
    });
  });
}
