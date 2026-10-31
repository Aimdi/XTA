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
import 'package:xta/plugins/booru/booru_links.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';
import 'package:xta/plugins/booru/booru_popular.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:xta/plugins/booru/booru_request_gate.dart';
import 'package:xta/plugins/booru/booru_tag_limit.dart';

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

  /// The host answered a guest that it needs credentials (Rule34).
  needsCredentials,

  /// The host pages no further: a 410, or gelbooru.com's "Too deep".
  tooDeep,

  /// A 422: the search has more tags than the host allows at once
  /// (Danbooru: two for guests and Members).
  tagLimit,

  /// A 500 saying the host's database gave up on the search: too many posts
  /// to sort (Danbooru's `order:score` on a big tag).
  timedOut,
  notFound,
  badResponse,
}

class BooruException implements Exception {
  final BooruErrorKind kind;
  final String message;

  /// With [BooruErrorKind.tagLimit]: how many tags the host said one search
  /// may have, when it said.
  final int? allowedTags;

  /// [message] often names the request URL; its credentials are masked.
  BooruException(this.kind, String message, {this.allowedTags})
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

  BooruClient(
    this.prefs, {
    http.Client? httpClient,
    BooruRequestGate Function()? requestGate,
  }) : httpClient = httpClient ?? http.Client(),
       _newGate = requestGate ?? BooruRequestGate.new;

  final BooruRequestGate Function() _newGate;

  /// One per host: a burst to one site (Following's fan-out, a post's
  /// lookups) waits its turn without holding up another.
  final _gates = <String, BooruRequestGate>{};

  /// How many tags one search may have, per site and account, once the
  /// host has said; kept while the app runs.
  final _tagLimits = <String, int>{};

  /// How each search past a limit was shared out, so that its pages follow
  /// on from one another.
  final _splits = <String, BooruTagSplit>{};
  static const _keptSplits = 200;

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
    final ({Object? raw, List<String> onDevice}) answer;
    try {
      answer = await _postsFor(from, tags, page: page, limit: limit);
    } on BooruException catch (error) {
      if (error.kind != BooruErrorKind.tooDeep) rethrow;
      return BooruPostPage(posts: const [], page: page, hasMore: false);
    }
    final parsed = parseBooruPosts(
      answer.raw,
      engine: from.engine,
      host: from.host,
    );
    final onDevice = answer.onDevice;
    return _page(
      onDevice.isEmpty
          ? parsed
          : [
              for (final post in parsed)
                if (booruPostMatches(post, onDevice)) post,
            ],
      page: page,
      hasMore: parsed.length >= limit,
    );
  }

  /// The host's answer for [tags], and the tags left for the device to
  /// check on it. Until a site says how many tags it takes, a search goes
  /// out whole; after that, one with more is shared out
  /// ([splitBooruQuery]).
  Future<({Object? raw, List<String> onDevice})> _postsFor(
    BooruSite site,
    List<String> tags, {
    required int page,
    required int limit,
  }) async {
    final account = _accountOf(site);
    final known = _tagLimits[account];
    final split = await _splitFor(site, tags, known);
    try {
      final raw = await fetchJson(
        _postsUri(
          site,
          tags: _withRatingTag(split.host, site),
          page: page,
          limit: limit,
        ),
      );
      return (raw: raw, onDevice: split.device);
    } on BooruException catch (error) {
      final allowed = error.allowedTags;
      if (allowed == null || (known != null && known <= allowed)) rethrow;
      _tagLimits[account] = allowed;
      return _postsFor(site, tags, page: page, limit: limit);
    }
  }

  /// How [tags] are shared out on [site] under [limit]: whole while the
  /// limit is not known or they fit. Decided once per search, from how
  /// many posts the host has for each tag.
  Future<BooruTagSplit> _splitFor(
    BooruSite site,
    List<String> tags,
    int? limit,
  ) async {
    if (limit == null || booruCountedTokens(tags) <= limit) {
      return (host: tags, device: const <String>[]);
    }
    final key = '${_accountOf(site)}\n$limit\n${tags.join(' ')}';
    final kept = _splits[key];
    if (kept != null) return kept;
    final uses = await _tagUses(site, booruPlainTags(tags));
    final split = splitBooruQuery(tags, limit: limit, uses: uses);
    if (split == null) {
      throw BooruException(
        BooruErrorKind.tagLimit,
        '${site.host}: more counted metatags than $limit',
        allowedTags: limit,
      );
    }
    if (_splits.length >= _keptSplits) _splits.clear();
    return _splits[key] = split;
  }

  /// How many posts [site] has for each of [tags], by lower-cased name;
  /// none when it cannot say.
  Future<Map<String, int>> _tagUses(BooruSite site, List<String> tags) async {
    final uri = booruTagListUri(site, tags);
    if (uri == null) return const {};
    try {
      final rows = parseBooruTagSuggestions(
        await fetchTags(uri),
        engine: site.engine,
      );
      return {
        for (final row in rows) row.name.toLowerCase(): row.postCount ?? 0,
      };
    } catch (_) {
      return const {};
    }
  }

  /// Limits differ by account: a site's guests and Members take two tags,
  /// its Gold accounts more.
  String _accountOf(BooruSite site) => '${site.host}\n${site.login}';

  /// Pagination follows the raw API page: the file, rating and mute filters
  /// must not make an empty filtered page look like the end of the feed.
  /// The posts the blacklist hid stay in the page's `allowed` list, for a
  /// feed to count and show on request; those over the rating cap do not.
  BooruPostPage _page(
    List<BooruPost> parsed, {
    required int page,
    required bool hasMore,
  }) {
    final muted = mutedTags;
    final allowed = [
      for (final post in parsed)
        if (post.hasFile && booruPostAllowed(post, maxRating)) post,
    ];
    return BooruPostPage(
      posts: [
        for (final post in allowed)
          if (!booruPostMuted(post, muted)) post,
      ],
      allowed: allowed,
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
    return siteAt(host, BooruEngine.tryParse(post.engine) ?? engine);
  }

  /// [host], spoken to as [engine], with the saved credentials only if they
  /// belong to it.
  BooruSite siteAt(String host, BooruEngine engine) => BooruSite(
    engine: engine,
    host: host,
    login: booruCredential(prefs, optionPluginBooruLogin, host: host).trim(),
    apiKey: booruCredential(prefs, optionPluginBooruApiKey, host: host).trim(),
  );

  /// The post [ref] names, from its own site; null when the site has no such
  /// post or it is over the rating cap. The blacklist does not hide it: it
  /// was asked for by name.
  Future<BooruPost?> postAt(BooruPostRef ref) async {
    final page = await posts(
      tags: ['id:${ref.id}'],
      limit: 1,
      site: siteAt(ref.host, ref.engine),
    );
    return page.allowed.where((post) => post.id == ref.id).firstOrNull;
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
        await fetchTags(uri),
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

  /// What [site] (the site in use by default) keeps about the artist behind
  /// [tag]: other names and links. Null where it keeps no artist entries,
  /// has none for the tag, or cannot be reached; the page simply goes
  /// without.
  Future<BooruArtist?> artist(String tag, {BooruSite? site}) async {
    final uri = booruArtistUri(site ?? this.site, tag);
    if (uri == null) return null;
    try {
      return parseBooruArtist(await fetchJson(uri), tag);
    } catch (_) {
      return null;
    }
  }

  /// The pools [post] is in, on its own site; none where the host keeps no
  /// pools or cannot be reached.
  Future<List<BooruPool>> pools(BooruPost post) async {
    final uri = booruPostPoolsUri(siteOf(post), post.id);
    if (uri == null) return const [];
    try {
      return parseBooruPools(await fetchJson(uri));
    } catch (_) {
      return const [];
    }
  }

  /// The translation notes on [post], on its own site; none where the host
  /// keeps none or cannot be reached.
  Future<List<BooruNote>> notes(BooruPost post) async {
    final uri = booruNotesUri(siteOf(post), post.id);
    if (uri == null) return const [];
    try {
      return parseBooruNotes(await fetchJson(uri));
    } catch (_) {
      return const [];
    }
  }

  Future<List<BooruComment>> comments(BooruPost post) async {
    final uri = booruCommentsUri(siteOf(post), post.id);
    if (uri == null) return const [];
    return parseBooruComments(await fetchJson(uri));
  }

  /// Tags the host finds alongside [tags], most related first; none where
  /// it keeps no such list or the request fails.
  Future<List<String>> relatedTags(List<String> tags) async {
    final uri = booruRelatedTagsUri(site, tags);
    if (uri == null || !isConfigured) return const [];
    try {
      return parseBooruRelatedTags(await fetchJson(uri), engine);
    } catch (_) {
      return const [];
    }
  }

  /// Tags for what is being typed: the site's own autocomplete where it
  /// keeps one, else the tags whose names start with it.
  Future<List<BooruTagSuggestion>> suggestTags(
    String prefix, {
    int limit = 12,
  }) async {
    final query = prefix.trim();
    if (query.isEmpty || !isConfigured) return const [];

    final completed = await _autocomplete(query, limit: limit);
    if (completed != null) return completed;
    final uri = _tagsUri(query: query, limit: limit);
    try {
      return parseBooruTagSuggestions(await fetchTags(uri), engine: engine);
    } catch (_) {
      return const [];
    }
  }

  /// The site's autocomplete for [query]: null where it keeps none or did
  /// not answer with rows, none when it could not be reached.
  Future<List<BooruTagSuggestion>?> _autocomplete(
    String query, {
    required int limit,
  }) async {
    final uri = booruAutocompleteUri(site, query, limit: limit);
    if (uri == null) return null;
    try {
      return parseBooruAutocomplete(await fetchJson(uri));
    } on BooruException catch (error) {
      return error.kind == BooruErrorKind.network ? const [] : null;
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

  List<String> _withRatingTag(List<String> tags, BooruSite site) {
    final hasRating = tags.any((t) => t.toLowerCase().startsWith('rating:'));
    if (hasRating) return tags;
    final engine = site.engine;

    // Gelbooru spells ratings out and its forks know only safe, questionable
    // and explicit, so between general and explicit only "not explicit" is
    // said the same way on both; the client-side filter does the rest.
    if (engine == BooruEngine.gelbooruV2) {
      return [
        ...tags,
        if (maxRating == BooruRating.general)
          ?booruRatingMetatag(engine, maxRating, host: site.host),
        if (maxRating == BooruRating.sensitive ||
            maxRating == BooruRating.questionable)
          '-rating:explicit',
      ];
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
    final gate = _gates.putIfAbsent(uri.host, _newGate);
    await gate.admit();
    final http.Response response;
    try {
      response = await httpClient
          .get(
            uri,
            headers: {'User-Agent': _userAgent, 'Accept': 'application/json'},
          )
          .timeout(_timeout);
    } catch (e) {
      throw BooruException(BooruErrorKind.network, '$e');
    }
    gate.answered(
      response.statusCode,
      retryAfter: booruRetryAfter(response.headers['retry-after']),
    );
    return response;
  }

  Future<Object?> fetchJson(Uri uri) async => _decodeJson(await _get(uri), uri);

  /// A tag list: decoded JSON, or the XML text Gelbooru 0.2 forks send for
  /// tags whatever `json=1` asks.
  Future<Object?> fetchTags(Uri uri) async {
    final response = await _get(uri);
    final body = response.body.trimLeft();
    if (response.statusCode == 200 && body.startsWith('<')) return body;
    return _decodeJson(response, uri);
  }

  /// What a status says went wrong, when it says something more specific than
  /// an unexpected answer. A 403 blames the credentials only if some went out.
  BooruErrorKind? _statusFailure(http.Response response, Uri uri) =>
      switch (response.statusCode) {
        401 => BooruErrorKind.unauthorized,
        403 when _sentCredentials(uri) => BooruErrorKind.unauthorized,
        403 => BooruErrorKind.forbidden,
        404 => BooruErrorKind.notFound,
        410 => BooruErrorKind.tooDeep,
        422 => BooruErrorKind.tagLimit,
        429 => BooruErrorKind.rateLimited,
        500 when _timedOut(response.body) => BooruErrorKind.timedOut,
        503 => BooruErrorKind.unavailable,
        _ => null,
      };

  /// Danbooru's answer when its database gave up on a query.
  static bool _timedOut(String body) =>
      body.contains('QueryCanceled') || body.contains('timed out');

  /// What a successful answer in plain text says went wrong: Rule34 asks a
  /// guest for credentials that way, and gelbooru.com stops paging.
  BooruErrorKind? _textFailure(String body, Uri uri) {
    if (body.startsWith('[') || body.startsWith('{') || body.startsWith('<')) {
      return null;
    }
    final text = body.toLowerCase();
    if (text.startsWith('missing authentication')) {
      return _sentCredentials(uri)
          ? BooruErrorKind.unauthorized
          : BooruErrorKind.needsCredentials;
    }
    return text.contains('too deep') ? BooruErrorKind.tooDeep : null;
  }

  bool _sentCredentials(Uri uri) =>
      uri.queryParameters.keys.any(booruCredentialParameters.contains);

  Object? _decodeJson(http.Response response, Uri uri) {
    final failure = _statusFailure(response, uri);
    if (failure != null) {
      throw BooruException(
        failure,
        '$uri: ${response.statusCode}',
        allowedTags: failure == BooruErrorKind.tagLimit
            ? booruTagLimitIn(response.body)
            : null,
      );
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
    final refusal = _textFailure(body, uri);
    if (refusal != null) {
      throw BooruException(refusal, '$uri: ${refusal.name}');
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
