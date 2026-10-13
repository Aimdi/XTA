/// Read-only HTTP client for configured booru hosts.
library;

import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_credentials.dart';
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

  /// A 403 although no credentials were sent: the host wants a login, or a
  /// bot check stands in front of it.
  forbidden,
  rateLimited,

  /// A 503: the host is overloaded, or rate limits that way (e621).
  unavailable,
  notFound,
  badResponse,
}

class BooruException implements Exception {
  final BooruErrorKind kind;
  final String message;

  /// [message] often names the request URL; its credentials are masked.
  BooruException(this.kind, String message)
    : message = redactBooruCredentials(message);

  @override
  String toString() => 'BooruException{$kind: $message}';
}

/// The newest posts of the followed tags and saved searches, merged; of
/// the [asked] queries, [failed] did not load, and [error] is the first such
/// failure.
typedef BooruFollowedPosts = ({
  List<BooruPost> posts,
  int asked,
  int failed,
  Object? error,
});

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

  String get host => booruHostOf(prefs);

  /// Sent only to the host it was entered for.
  String get login => booruCredential(prefs, optionPluginBooruLogin).trim();
  String get apiKey => booruCredential(prefs, optionPluginBooruApiKey).trim();

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

  /// What a feed's posts depend on: the site, the rating cap and the
  /// blacklist. A feed loaded under another key is stale.
  String get feedKey => [
    engine.id,
    host,
    maxRating.code,
    ...(mutedTags.toList()..sort()),
  ].join('\n');

  Future<BooruPostPage> latest({int page = 1, int limit = defaultPageSize}) =>
      posts(tags: const [], page: page, limit: limit);

  Future<BooruPostPage> search(
    String query, {
    int page = 1,
    int limit = defaultPageSize,
    BooruSite? site,
  }) => posts(
    tags: booruQueryTokens(query),
    page: page,
    limit: limit,
    site: site,
  );

  /// Posts from [site], the site in use by default.
  Future<BooruPostPage> posts({
    required List<String> tags,
    int page = 1,
    int limit = defaultPageSize,
    BooruSite? site,
  }) async {
    if (!isConfigured) {
      throw BooruException(
        BooruErrorKind.notConfigured,
        'No booru host configured',
      );
    }

    final from = site ?? this.site;
    final effectiveTags = _withRatingTag(tags, from.engine);
    final uri = _postsUri(from, tags: effectiveTags, page: page, limit: limit);
    final parsed = parseBooruPosts(
      await fetchJson(uri),
      engine: from.engine,
      host: from.host,
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

  /// Where [post] came from: its own host and engine, with the saved
  /// credentials only if they belong to that host.
  BooruSite siteOf(BooruPost post) {
    final host = normaliseBooruHost(post.host);
    if (host.isEmpty) return site;
    return BooruSite(
      engine: BooruEngine.tryParse(post.engine) ?? engine,
      host: host,
      login: booruCredential(prefs, optionPluginBooruLogin, host: host).trim(),
      apiKey: booruCredential(
        prefs,
        optionPluginBooruApiKey,
        host: host,
      ).trim(),
    );
  }

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
    final from = siteOf(post);
    final uri = booruTagInfoUri(from, postId: post.id, tags: post.tags);
    if (uri == null) return fromPost;
    try {
      final looked = parseBooruTagInfo(
        await fetchJson(uri),
        engine: from.engine,
      );
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

  /// The wiki text for [tag] on [site] (the site in use by default), or
  /// null when the host has no page for it.
  Future<String?> wiki(String tag, {BooruSite? site}) async {
    final uri = booruWikiUri(site ?? this.site, tag);
    if (uri == null) return null;
    try {
      return parseBooruWiki(await fetchJson(uri), tag: tag);
    } on BooruException catch (e) {
      if (e.kind == BooruErrorKind.notFound) return null;
      rethrow;
    }
  }

  Future<List<BooruComment>> comments(BooruPost post) async {
    final uri = booruCommentsUri(siteOf(post), post.id);
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
  /// interleaved feeds. Queries that fail are left out.
  Future<List<BooruPost>> postsForTags(
    Iterable<String> tags, {
    int limitPerTag = 10,
  }) async => (await followedPosts(tags, limitPerTag: limitPerTag)).posts;

  /// One page per followed tag or saved search, merged newest first, with how
  /// many of the queries failed.
  Future<BooruFollowedPosts> followedPosts(
    Iterable<String> tags, {
    int limitPerTag = 10,
  }) async {
    final unique = <String>{for (final tag in tags) ?normaliseBooruQuery(tag)};
    final settled = await Future.wait([
      for (final query in unique)
        posts(
          tags: booruQueryTokens(query),
          page: 1,
          limit: limitPerTag,
        ).then<(BooruPostPage?, Object?)>(
          (page) => (page, null),
          onError: (Object error) => (null, error),
        ),
    ]);
    final errors = [for (final (_, error) in settled) ?error];
    return (
      posts: _newestFirst([for (final (page, _) in settled) ?page]),
      asked: unique.length,
      failed: errors.length,
      error: errors.firstOrNull,
    );
  }

  List<BooruPost> _newestFirst(List<BooruPostPage> pages) {
    final byId = <String, BooruPost>{};
    for (final page in pages) {
      for (final post in page.posts) {
        byId.putIfAbsent(post.key, () => post);
      }
    }

    final merged = byId.values.toList(growable: false);
    merged.sort((a, b) {
      final ad = a.feedDate ?? DateTime.fromMillisecondsSinceEpoch(0);
      final bd = b.feedDate ?? DateTime.fromMillisecondsSinceEpoch(0);
      return bd.compareTo(ad);
    });
    return merged;
  }

  List<String> _withRatingTag(List<String> tags, BooruEngine engine) {
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

  Uri _postsUri(
    BooruSite site, {
    required List<String> tags,
    required int page,
    required int limit,
  }) {
    final tagQuery = tags.join(' ');
    final query = {
      'limit': '$limit',
      if (tagQuery.isNotEmpty) 'tags': tagQuery,
    };
    return switch (site.engine) {
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

  /// What a status says went wrong, when it says something more specific than
  /// an unexpected answer. A 403 blames the credentials only if some went out.
  BooruErrorKind? _statusFailure(int status, Uri uri) => switch (status) {
    401 => BooruErrorKind.unauthorized,
    403 when uri.queryParameters.keys.any(booruCredentialParameters.contains) =>
      BooruErrorKind.unauthorized,
    403 => BooruErrorKind.forbidden,
    404 => BooruErrorKind.notFound,
    429 => BooruErrorKind.rateLimited,
    503 => BooruErrorKind.unavailable,
    _ => null,
  };

  Object? _decodeJson(http.Response response, Uri uri) {
    final failure = _statusFailure(response.statusCode, uri);
    if (failure != null) {
      throw BooruException(failure, '$uri: ${response.statusCode}');
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
