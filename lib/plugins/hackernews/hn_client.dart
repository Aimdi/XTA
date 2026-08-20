import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:xta/plugins/hackernews/hn_html.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/utils/json.dart';

const hnAlgoliaHost = 'hn.algolia.com';
const hnFirebaseHost = 'hacker-news.firebaseio.com';
const hnPageSize = 30;

/// Public HN reads: Algolia for lists/search/threads, Firebase for Best + users.
class HackerNewsClient {
  static const userAgent = 'XTA Hacker News plugin';

  final http.Client httpClient;
  final Duration timeout;

  HackerNewsClient({
    http.Client? httpClient,
    this.timeout = const Duration(seconds: 12),
  }) : httpClient = httpClient ?? http.Client();

  Future<HnStoryPage> feed(HnFeed feed, {int page = 0}) {
    return switch (feed) {
      HnFeed.best => _bestPage(page: page),
      HnFeed.top => _algoliaPage(
        path: '/api/v1/search',
        tags: 'front_page',
        page: page,
      ),
      HnFeed.newest => _algoliaPage(
        path: '/api/v1/search_by_date',
        tags: 'story',
        page: page,
      ),
      HnFeed.ask => _algoliaPage(
        path: '/api/v1/search',
        tags: 'ask_hn',
        page: page,
      ),
      HnFeed.show => _algoliaPage(
        path: '/api/v1/search',
        tags: 'show_hn',
        page: page,
      ),
      HnFeed.jobs => _algoliaPage(
        path: '/api/v1/search_by_date',
        tags: 'job',
        page: page,
      ),
    };
  }

  Future<HnStoryPage> search(String query, {int page = 0}) {
    return _algoliaPage(
      path: '/api/v1/search',
      tags: 'story',
      page: page,
      query: query,
    );
  }

  Future<HnStoryPage> submissions(String author, {int page = 0}) {
    return _algoliaPage(
      path: '/api/v1/search_by_date',
      tags: 'story,author_${author.trim()}',
      page: page,
    );
  }

  Future<(HnStory, List<HnComment>)> thread(int id) async {
    final json = await _getJson(Uri.https(hnAlgoliaHost, '/api/v1/items/$id'));
    return (_storyFromAlgoliaItem(json), _comments(json['children']));
  }

  Future<HnUser> user(String id) async {
    final json = await _getJson(
      Uri.https(hnFirebaseHost, '/v0/user/${id.trim()}.json'),
    );
    if (!json.exists) {
      throw const HnException('User not found');
    }
    final created = json['created'].integer;
    return HnUser(
      id: json['id'].string ?? id,
      karma: json['karma'].integer ?? 0,
      about: hnHtmlToText(json['about'].string),
      createdAt: created == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(created * 1000, isUtc: true),
      submittedCount: json['submitted'].list.length,
    );
  }

  Future<HnStoryPage> _bestPage({required int page}) async {
    final ids = await _firebaseIds('beststories');
    final start = page * hnPageSize;
    if (start >= ids.length) {
      return HnStoryPage(stories: const [], page: page, hasMore: false);
    }
    final slice = ids.skip(start).take(hnPageSize).toList(growable: false);
    final stories = [
      for (final story in await Future.wait(slice.map(_firebaseStory)))
        if (story != null) story,
    ];
    return HnStoryPage(
      stories: stories,
      page: page,
      hasMore: start + hnPageSize < ids.length,
    );
  }

  Future<List<int>> _firebaseIds(String name) async {
    final json = await _getJson(Uri.https(hnFirebaseHost, '/v0/$name.json'));
    return [
      for (final id in json.list)
        if (id.integer != null) id.integer!,
    ];
  }

  Future<HnStory?> _firebaseStory(int id) async {
    final json = await _getJson(Uri.https(hnFirebaseHost, '/v0/item/$id.json'));
    if (!json.exists || json['type'].string == 'comment') {
      return null;
    }
    final time = json['time'].integer;
    return HnStory(
      id: json['id'].integer ?? id,
      title: json['title'].string ?? '',
      url: json['url'].string,
      text: hnHtmlToText(json['text'].string),
      author: json['by'].string,
      score: json['score'].integer ?? 0,
      commentCount: json['descendants'].integer ?? 0,
      createdAt: time == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(time * 1000, isUtc: true),
      type: json['type'].string ?? 'story',
    );
  }

  Future<HnStoryPage> _algoliaPage({
    required String path,
    required String tags,
    required int page,
    String? query,
  }) async {
    final json = await _getJson(
      Uri.https(hnAlgoliaHost, path, {
        'tags': tags,
        'page': '$page',
        'hitsPerPage': '$hnPageSize',
        if (query != null && query.trim().isNotEmpty) 'query': query.trim(),
      }),
    );
    final stories = [
      for (final hit in json['hits'].list)
        if (_storyFromHit(hit) case final story?) story,
    ];
    final pages = json['nbPages'].integer ?? 0;
    return HnStoryPage(stories: stories, page: page, hasMore: page + 1 < pages);
  }

  HnStory? _storyFromHit(Json hit) {
    final id = hit['objectID'].integer ?? hit['story_id'].integer;
    if (id == null) {
      return null;
    }
    final created = hit['created_at_i'].integer;
    return HnStory(
      id: id,
      title: hit['title'].string ?? '',
      url: hit['url'].string,
      text: hnHtmlToText(hit['story_text'].string ?? hit['text'].string),
      author: hit['author'].string,
      score: hit['points'].integer ?? 0,
      commentCount: hit['num_comments'].integer ?? 0,
      createdAt: created == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(created * 1000, isUtc: true),
      type: _typeFromTags(hit['_tags']),
    );
  }

  HnStory _storyFromAlgoliaItem(Json item) {
    final created = item['created_at_i'].integer;
    return HnStory(
      id: item['id'].integer ?? 0,
      title: item['title'].string ?? '',
      url: item['url'].string,
      text: hnHtmlToText(item['text'].string),
      author: item['author'].string,
      score: item['points'].integer ?? 0,
      commentCount: _countComments(item['children']),
      createdAt: created == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(created * 1000, isUtc: true),
      type: item['type'].string ?? 'story',
    );
  }

  List<HnComment> _comments(Json children) {
    return [
      for (final child in children.list)
        if (_comment(child) case final comment?) comment,
    ];
  }

  HnComment? _comment(Json item) {
    final id = item['id'].integer;
    if (id == null) {
      return null;
    }
    final deleted =
        item['author'].string == null || item['text'].string == null;
    final created = item['created_at_i'].integer;
    return HnComment(
      id: id,
      author: item['author'].string,
      text: hnHtmlToText(item['text'].string),
      createdAt: created == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(created * 1000, isUtc: true),
      children: _comments(item['children']),
      deleted: deleted,
    );
  }

  int _countComments(Json children) {
    var total = 0;
    for (final child in children.list) {
      if (child['id'].integer != null) {
        total += 1 + _countComments(child['children']);
      }
    }
    return total;
  }

  String _typeFromTags(Json tags) {
    final values = {for (final tag in tags.list) tag.string};
    if (values.contains('job')) return 'job';
    if (values.contains('ask_hn')) return 'ask';
    if (values.contains('show_hn')) return 'show';
    return 'story';
  }

  Future<Json> _getJson(Uri uri) async {
    try {
      final response = await httpClient
          .get(
            uri,
            headers: {'User-Agent': userAgent, 'Accept': 'application/json'},
          )
          .timeout(timeout);
      if (response.statusCode == 404) {
        throw const HnException('Not found');
      }
      if (response.statusCode != 200) {
        throw HnException('HN answered ${response.statusCode}');
      }
      return Json(jsonDecode(response.body));
    } on HnException {
      rethrow;
    } catch (error) {
      throw HnException(error.toString());
    }
  }
}
