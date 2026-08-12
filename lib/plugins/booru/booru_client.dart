/// Read-only HTTP client for configured booru hosts.
library;

import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';

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

  BooruRating get maxRating =>
      BooruRating.tryParse(prefs.get<String>(optionPluginBooruMaxRating)) ??
      BooruRating.general;

  Set<String> get mutedTags {
    final raw = prefs.get<String>(optionPluginBooruMutedTags) ?? '[]';
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        return {
          for (final tag in decoded.whereType<String>())
            ?normaliseBooruTag(tag),
        };
      }
    } catch (_) {}
    return const {};
  }

  bool get isConfigured => host.isNotEmpty;

  Future<BooruPostPage> latest({int page = 1, int limit = defaultPageSize}) =>
      posts(tags: const [], page: page, limit: limit);

  Future<BooruPostPage> search(
    String query, {
    int page = 1,
    int limit = defaultPageSize,
  }) {
    final tags = query
        .trim()
        .split(RegExp(r'\s+'))
        .where((t) => t.isNotEmpty)
        .toList(growable: false);
    return posts(tags: tags, page: page, limit: limit);
  }

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
    final response = await _get(uri);
    final decoded = _decodeJson(response, uri);
    final parsed = parseBooruPosts(decoded, engine: engine, host: host);
    final muted = mutedTags;
    final filtered = [
      for (final post in parsed)
        if (booruPostAllowed(post, maxRating) && !booruPostMuted(post, muted))
          post,
    ];

    // Pagination follows the raw API page: rating/mute filters must not make
    // an empty filtered page look like the end of the feed.
    return BooruPostPage(
      posts: filtered,
      page: page,
      hasMore: parsed.length >= limit,
    );
  }

  Future<List<BooruTagSuggestion>> suggestTags(
    String prefix, {
    int limit = 12,
  }) async {
    final query = prefix.trim();
    if (query.isEmpty || !isConfigured) return const [];

    final uri = _tagsUri(query: query, limit: limit);
    if (uri == null) return const [];

    try {
      final response = await _get(uri);
      final decoded = _decodeJson(response, uri);
      return parseBooruTagSuggestions(decoded, engine: engine);
    } catch (_) {
      return const [];
    }
  }

  /// One page per tag query, newest-first merge for interleaved feeds.
  Future<List<BooruPost>> postsForTags(
    Iterable<String> tags, {
    int limitPerTag = 10,
  }) async {
    final unique = <String>{for (final tag in tags) ?normaliseBooruTag(tag)};
    if (unique.isEmpty) return const [];

    final pages = await Future.wait([
      for (final tag in unique)
        posts(tags: [tag], page: 1, limit: limitPerTag).catchError(
          (_) => const BooruPostPage(posts: [], page: 1, hasMore: false),
        ),
    ]);

    final byId = <String, BooruPost>{};
    for (final page in pages) {
      for (final post in page.posts) {
        byId.putIfAbsent('${post.host}:${post.id}', () => post);
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

  Uri? _tagsUri({required String query, required int limit}) {
    final base = Uri.parse(host);
    final path = _trimPath(base.path);
    // Prefix match — engines differ on wildcard syntax.
    switch (engine) {
      case BooruEngine.danbooru:
        return base.replace(
          path: '$path/tags.json',
          queryParameters: {
            'search[name_matches]': '$query*',
            'search[order]': 'count',
            'limit': '$limit',
            if (login.isNotEmpty) 'login': login,
            if (apiKey.isNotEmpty) 'api_key': apiKey,
          },
        );
      case BooruEngine.moebooru:
        return base.replace(
          path: '$path/tag.json',
          queryParameters: {
            'name': '$query*',
            'order': 'count',
            'limit': '$limit',
          },
        );
      case BooruEngine.gelbooruV2:
        return base.replace(
          path: '$path/index.php',
          queryParameters: {
            'page': 'dapi',
            's': 'tag',
            'q': 'index',
            'json': '1',
            'limit': '$limit',
            'name_pattern': '$query%',
            if (login.isNotEmpty) 'user_id': login,
            if (apiKey.isNotEmpty) 'api_key': apiKey,
          },
        );
      case BooruEngine.e621:
        return base.replace(
          path: '$path/tags.json',
          queryParameters: {
            'search[name_matches]': '$query*',
            'search[order]': 'count',
            'limit': '$limit',
            if (login.isNotEmpty) 'login': login,
            if (apiKey.isNotEmpty) 'api_key': apiKey,
          },
        );
    }
  }

  Uri _postsUri({
    required List<String> tags,
    required int page,
    required int limit,
  }) {
    final base = Uri.parse(host);
    final tagQuery = tags.join(' ');
    final path = _trimPath(base.path);

    switch (engine) {
      case BooruEngine.danbooru:
        return base.replace(
          path: '$path/posts.json',
          queryParameters: {
            'limit': '$limit',
            'page': '$page',
            if (tagQuery.isNotEmpty) 'tags': tagQuery,
            if (login.isNotEmpty) 'login': login,
            if (apiKey.isNotEmpty) 'api_key': apiKey,
          },
        );
      case BooruEngine.moebooru:
        return base.replace(
          path: '$path/post.json',
          queryParameters: {
            'limit': '$limit',
            'page': '$page',
            if (tagQuery.isNotEmpty) 'tags': tagQuery,
            if (login.isNotEmpty) 'login': login,
            if (apiKey.isNotEmpty) 'password_hash': apiKey,
          },
        );
      case BooruEngine.gelbooruV2:
        return base.replace(
          path: '$path/index.php',
          queryParameters: {
            'page': 'dapi',
            's': 'post',
            'q': 'index',
            'json': '1',
            'limit': '$limit',
            'pid': '${page - 1}',
            if (tagQuery.isNotEmpty) 'tags': tagQuery,
            if (login.isNotEmpty) 'user_id': login,
            if (apiKey.isNotEmpty) 'api_key': apiKey,
          },
        );
      case BooruEngine.e621:
        return base.replace(
          path: '$path/posts.json',
          queryParameters: {
            'limit': '$limit',
            'page': '$page',
            if (tagQuery.isNotEmpty) 'tags': tagQuery,
            if (login.isNotEmpty) 'login': login,
            if (apiKey.isNotEmpty) 'api_key': apiKey,
          },
        );
    }
  }

  String _trimPath(String path) {
    if (path.isEmpty || path == '/') return '';
    return path.replaceAll(RegExp(r'/+$'), '');
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
