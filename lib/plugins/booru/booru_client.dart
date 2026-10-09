/// Read-only HTTP client for configured booru hosts.
library;

import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_detail_parse.dart';
import 'package:xta/plugins/booru/booru_endpoints.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';
import 'package:xta/plugins/booru/booru_popular.dart';
import 'package:xta/plugins/booru/booru_query.dart';

enum BooruErrorKind {
  notConfigured,
  network,
  unauthorized,
  rateLimited,
  notFound,
  badResponse,
}

class BooruException implements Exception {
  final BooruErrorKind kind;
  final String message;

  BooruException(this.kind, this.message);

  @override
  String toString() => 'BooruException{$kind: $message}';
}

class BooruClient {
  final http.Client httpClient;
  final BasePrefService prefs;

  BooruClient(this.prefs, {http.Client? httpClient})
    : httpClient = httpClient ?? http.Client();

  static const _timeout = Duration(seconds: 20);
  static const _userAgent =
      'XTA-Booru/0.1 (by Aimdi; read-only; +https://github.com/Aimdi/XTA)';
  static const defaultPageSize = 40;

  BooruEngine get engine =>
      BooruEngine.tryParse(prefs.get<String>(optionPluginBooruEngine)) ??
      BooruEngine.danbooru;

  String get host {
    final custom = normaliseBooruHost(
      prefs.get<String>(optionPluginBooruHost) ?? '',
    );
    if (custom.isNotEmpty) return custom;
    return booruPresets.first.host;
  }

  String get login => (prefs.get<String>(optionPluginBooruLogin) ?? '').trim();
  String get apiKey =>
      (prefs.get<String>(optionPluginBooruApiKey) ?? '').trim();

  BooruRating get maxRating {
    final stored = BooruRating.tryParse(
      prefs.get<String>(optionPluginBooruMaxRating),
    );
    if (stored != null) return stored;
    return booruHostIsMostlyExplicit(host)
        ? BooruRating.explicit
        : BooruRating.general;
  }

  Set<String> get mutedTags =>
      parseBooruBlacklist(prefs.get<String>(optionPluginBooruMutedTags));

  bool get isConfigured => host.isNotEmpty;

  Future<BooruPostPage> latest({int page = 1, int limit = defaultPageSize}) =>
      posts(tags: const [], page: page, limit: limit);

  Future<BooruPostPage> search(
    String query, {
    int page = 1,
    int limit = defaultPageSize,
  }) => posts(tags: booruQueryTokens(query), page: page, limit: limit);

  Future<BooruPostPage> posts({
    required List<String> tags,
    int page = 1,
    int limit = defaultPageSize,
  }) async {
    if (!isConfigured) {
      throw BooruException(
        BooruErrorKind.notConfigured,
        'No booru host configured',
      );
    }

    final effectiveTags = _withRatingTag(tags);
    final uri = _postsUri(tags: effectiveTags, page: page, limit: limit);
    final parsed = parseBooruPosts(
      await fetchJson(uri),
      engine: engine,
      host: host,
    );
    return _page(parsed, page: page, hasMore: parsed.length >= limit);
  }

  /// Pagination follows the raw API page: rating/mute filters must not make
  /// an empty filtered page look like the end of the feed.
  BooruPostPage _page(
    List<BooruPost> parsed, {
    required int page,
    required bool hasMore,
  }) {
    final muted = mutedTags;
    return BooruPostPage(
      posts: [
        for (final post in parsed)
          if (booruPostAllowed(post, maxRating) && !booruPostMuted(post, muted))
            post,
      ],
      page: page,
      hasMore: hasMore,
    );
  }

  BooruSite get site =>
      BooruSite(engine: engine, host: host, login: login, apiKey: apiKey);

  /// The host's popular list for [query]. Gelbooru keeps none, so it shows
  /// its highest-scored posts instead.
  Future<BooruPostPage> popular(
    BooruPopularQuery query, {
    int page = 1,
    int limit = defaultPageSize,
  }) async {
    final uri = booruPopularUri(site, query, page: page, limit: limit);
    if (uri == null) {
      return posts(
        tags: [booruScoreOrderMetatag(engine)],
        page: page,
        limit: limit,
      );
    }
    if (page > 1 && !booruPopularPages(engine)) {
      return BooruPostPage(posts: const [], page: page, hasMore: false);
    }
    final parsed = parseBooruPosts(
      await fetchJson(uri),
      engine: engine,
      host: host,
    );
    return _page(
      parsed,
      page: page,
      hasMore: booruPopularPages(engine) && parsed.length >= limit,
    );
  }

  /// Kind and post count of each of [post]'s tags. Kinds the post carries
  /// stand in where the lookup is missing or fails.
  Future<Map<String, BooruTagInfo>> tagInfo(BooruPost post) async {
    final fromPost = {
      for (final MapEntry(key: tag, value: category)
          in post.tagCategories.entries)
        tag: BooruTagInfo(category: category),
    };
    final uri = booruTagInfoUri(site, postId: post.id, tags: post.tags);
    if (uri == null) return fromPost;
    try {
      final looked = parseBooruTagInfo(await fetchJson(uri), engine: engine);
      return {
        ...fromPost,
        for (final MapEntry(key: tag, value: info) in looked.entries)
          tag: BooruTagInfo(
            category: info.category ?? fromPost[tag]?.category,
            postCount: info.postCount,
          ),
      };
    } catch (_) {
      return fromPost;
    }
  }

  /// The wiki text for [tag], or null when the host has no page for it.
  Future<String?> wiki(String tag) async {
    final uri = booruWikiUri(site, tag);
    if (uri == null) return null;
    try {
      return parseBooruWiki(await fetchJson(uri), tag: tag);
    } on BooruException catch (e) {
      if (e.kind == BooruErrorKind.notFound) return null;
      rethrow;
    }
  }

  Future<List<BooruComment>> comments(BooruPost post) async {
    final uri = booruCommentsUri(site, post.id);
    if (uri == null) return const [];
    return parseBooruComments(await fetchJson(uri));
  }

  Future<List<BooruTagSuggestion>> suggestTags(
    String prefix, {
    int limit = 12,
  }) async {
    final query = prefix.trim();
    if (query.isEmpty || !isConfigured) return const [];

    final uri = _tagsUri(query: query, limit: limit);
    try {
      return parseBooruTagSuggestions(await fetchJson(uri), engine: engine);
    } catch (_) {
      return const [];
    }
  }

  /// One page per followed tag or saved search, newest-first merge for
  /// interleaved feeds.
  Future<List<BooruPost>> postsForTags(
    Iterable<String> tags, {
    int limitPerTag = 10,
  }) async {
    final unique = <String>{for (final tag in tags) ?normaliseBooruQuery(tag)};
    if (unique.isEmpty) return const [];

    final pages = await Future.wait([
      for (final query in unique)
        posts(
          tags: booruQueryTokens(query),
          page: 1,
          limit: limitPerTag,
        ).catchError(
          (_) => const BooruPostPage(posts: [], page: 1, hasMore: false),
        ),
    ]);

    final byId = <String, BooruPost>{};
    for (final page in pages) {
      for (final post in page.posts) {
        byId.putIfAbsent(post.key, () => post);
      }
    }

    final merged = byId.values.toList(growable: false);
    merged.sort((a, b) {
      final ad = a.createdAt ?? DateTime.fromMillisecondsSinceEpoch(0);
      final bd = b.createdAt ?? DateTime.fromMillisecondsSinceEpoch(0);
      return bd.compareTo(ad);
    });
    return merged;
  }

  List<String> _withRatingTag(List<String> tags) {
    final hasRating = tags.any((t) => t.toLowerCase().startsWith('rating:'));
    if (hasRating) return tags;

    // Gelbooru-family metatags differ; client-side filter still applies.
    if (engine == BooruEngine.gelbooruV2) {
      return tags;
    }

    // Moebooru / e621: s = safe. Danbooru: g = general, s = sensitive.
    if (engine == BooruEngine.moebooru || engine == BooruEngine.e621) {
      return [
        ...tags,
        if (maxRating == BooruRating.general) 'rating:s',
        if (maxRating == BooruRating.sensitive) ...['-rating:q', '-rating:e'],
        if (maxRating == BooruRating.questionable) '-rating:e',
      ];
    }

    return [
      ...tags,
      if (maxRating == BooruRating.general) 'rating:g',
      if (maxRating == BooruRating.sensitive) ...['-rating:q', '-rating:e'],
      if (maxRating == BooruRating.questionable) '-rating:e',
    ];
  }

  Uri _tagsUri({required String query, required int limit}) {
    // Prefix match — engines differ on wildcard syntax.
    return switch (engine) {
      BooruEngine.danbooru || BooruEngine.e621 => site.endpoint('/tags.json', {
        'search[name_matches]': '$query*',
        'search[order]': 'count',
        'limit': '$limit',
      }),
      BooruEngine.moebooru => site.endpoint('/tag.json', {
        'name': '$query*',
        'order': 'count',
        'limit': '$limit',
      }),
      BooruEngine.gelbooruV2 => site.dapi('tag', {
        'limit': '$limit',
        'name_pattern': '$query%',
      }),
    };
  }

  Uri _postsUri({
    required List<String> tags,
    required int page,
    required int limit,
  }) {
    final tagQuery = tags.join(' ');
    final query = {
      'limit': '$limit',
      if (tagQuery.isNotEmpty) 'tags': tagQuery,
    };
    return switch (engine) {
      BooruEngine.danbooru || BooruEngine.e621 => site.endpoint('/posts.json', {
        ...query,
        'page': '$page',
      }),
      BooruEngine.moebooru => site.endpoint('/post.json', {
        ...query,
        'page': '$page',
      }),
      BooruEngine.gelbooruV2 => site.dapi('post', {
        ...query,
        'pid': '${page - 1}',
      }),
    };
  }

  Future<http.Response> _get(Uri uri) async {
    try {
      return await httpClient
          .get(
            uri,
            headers: {'User-Agent': _userAgent, 'Accept': 'application/json'},
          )
          .timeout(_timeout);
    } catch (e) {
      throw BooruException(BooruErrorKind.network, '$e');
    }
  }

  Future<Object?> fetchJson(Uri uri) async => _decodeJson(await _get(uri), uri);

  Object? _decodeJson(http.Response response, Uri uri) {
    if (response.statusCode == 401 || response.statusCode == 403) {
      throw BooruException(
        BooruErrorKind.unauthorized,
        '$uri: ${response.statusCode}',
      );
    }
    if (response.statusCode == 404) {
      throw BooruException(BooruErrorKind.notFound, '$uri: 404');
    }
    if (response.statusCode == 429) {
      throw BooruException(BooruErrorKind.rateLimited, '$uri: 429');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw BooruException(
        BooruErrorKind.badResponse,
        '$uri: ${response.statusCode}',
      );
    }

    final body = response.body.trim();
    if (body.isEmpty || body == '[]' || body == '{}') {
      return const [];
    }

    try {
      return jsonDecode(body);
    } catch (e) {
      throw BooruException(
        BooruErrorKind.badResponse,
        '$uri: invalid JSON ($e)',
      );
    }
  }
}
