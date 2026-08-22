import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_rss.dart';

/// Read-only Substack client using public per-publication JSON endpoints,
/// with RSS `/feed` as a fallback when JSON is empty or fails.
class SubstackClient {
  static final log = Logger('SubstackClient');

  final http.Client httpClient;

  SubstackClient({http.Client? httpClient})
    : httpClient = httpClient ?? http.Client();

  static const _ua =
      'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

  static const _rssPaths = ['/feed', '/rss', '/feed.xml'];

  Future<SubstackPublication> fetchPublication(Uri base) async {
    final bases = substackHostCandidates(base);
    for (final candidate in bases) {
      final fromJson = await _publicationFromListing(candidate);
      if (fromJson != null) return _bindFollowedHost(fromJson, base);
    }
    for (final candidate in bases) {
      final rss = await _fetchRss(candidate);
      if (rss != null && _rssIsSubstack(candidate, rss)) {
        return _bindFollowedHost(_publicationFromRss(candidate, rss), base);
      }
    }
    for (final candidate in bases) {
      if (!isSubstackPublicationHost(candidate.host)) continue;
      final fromHtml = await _publicationFromHomepage(candidate);
      if (fromHtml != null) return _bindFollowedHost(fromHtml, base);
    }
    throw SubstackNotPublicationException();
  }

  /// Handle, share URL, @profile, custom domain, or leftover domain after a
  /// newsletter left Substack — always a host that still serves Substack.
  Future<SubstackPublication> resolvePublication(String input) async {
    final trimmed = input.trim();
    final tried = <String>{};

    Future<SubstackPublication?> tryBase(Uri? base) async {
      if (base == null || !tried.add(base.host.toLowerCase())) return null;
      try {
        return await fetchPublication(base);
      } catch (_) {
        return null;
      }
    }

    final fromPost = await tryBase(resolveSubstackPostRef(trimmed)?.base);
    if (fromPost != null) return fromPost;

    final resolved = resolveSubstackBase(trimmed);
    if (resolved != null) {
      for (final candidate in substackHostCandidates(resolved)) {
        final found = await tryBase(candidate);
        if (found != null) return found;
      }
    }

    final fromProfile = await _fromProfileHandle(
      resolveSubstackProfileHandle(trimmed),
    );
    if (fromProfile != null) return fromProfile;

    final leftover = await _fromLeftoverCustomDomain(trimmed);
    if (leftover != null) return leftover;

    final redirected = await _fromRedirect(trimmed);
    if (redirected != null) return redirected;

    throw SubstackNotPublicationException();
  }

  Future<List<SubstackPost>> fetchPosts(
    SubstackPublication publication, {
    int limit = 12,
    int offset = 0,
  }) async {
    final bases = publicationFetchBases(publication);
    if (bases.isEmpty) {
      throw SubstackNotPublicationException();
    }

    var reachable = false;
    for (final base in bases) {
      try {
        final posts = await _postsFromJson(
          base,
          publication,
          limit: limit,
          offset: offset,
        );
        reachable = true;
        if (posts.isNotEmpty || offset > 0) return posts;
      } catch (e) {
        log.info('JSON posts failed for $base: $e — trying next host');
      }
    }

    if (offset > 0) return const [];
    for (final base in bases) {
      final rss = await _fetchRss(base, publication: publication);
      if (rss != null && _rssIsSubstack(base, rss) && rss.posts.isNotEmpty) {
        return rss.posts.take(limit).toList(growable: false);
      }
    }
    if (!reachable) throw SubstackNotPublicationException();
    return const [];
  }

  Future<SubstackPost> fetchPost(
    SubstackPublication publication,
    String slug,
  ) async {
    for (final base in publicationFetchBases(publication)) {
      try {
        final post = await _postFromJson(base, publication, slug);
        if (post != null) return post;
      } catch (e) {
        log.info('JSON post failed for $slug at $base: $e');
      }
    }

    for (final base in publicationFetchBases(publication)) {
      final rss = await _fetchRss(base, publication: publication);
      final match = rss?.posts.where((p) => p.slug == slug).firstOrNull;
      if (match != null) return match;
    }
    throw SubstackClientException('Post not found: $slug');
  }

  Future<List<SubstackPost>> _postsFromJson(
    Uri base,
    SubstackPublication publication, {
    required int limit,
    required int offset,
  }) async {
    final result = await _fetchListingMaps(base, limit: limit, offset: offset);
    if (result == null) {
      throw SubstackClientException('No JSON listing at $base');
    }
    return result.posts
        .map(
          (e) => SubstackPost.fromJson(
            e,
            publicationBaseUrl: publication.baseUrl,
            publicationName: _resolvedName(publication, e, result.base),
            includeBody: false,
          ),
        )
        .where((e) => e.title.isNotEmpty && e.slug.isNotEmpty)
        .toList();
  }

  Future<SubstackPost?> _postFromJson(
    Uri base,
    SubstackPublication publication,
    String slug,
  ) async {
    final uri = base.replace(path: '/api/v1/posts/$slug');
    final response = await _get(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! Map) return null;
    final post = SubstackPost.fromJson(
      Map<String, dynamic>.from(decoded),
      publicationBaseUrl: publication.baseUrl,
      publicationName: _resolvedName(publication, decoded, base),
      includeBody: true,
    );
    if (post.title.isEmpty || post.slug.isEmpty) return null;
    return post;
  }

  Future<SubstackPublication?> fetchPrimaryPublication(String handle) async {
    final uri = Uri.https(
      'substack.com',
      '/api/v1/user/$handle/public_profile',
    );
    try {
      final response = await _get(uri);
      final decoded = jsonDecode(response.body);
      if (decoded is! Map) return null;
      return publicationFromProfileJson(Map<String, dynamic>.from(decoded));
    } catch (e) {
      log.info('Profile lookup failed for @$handle: $e');
      return null;
    }
  }

  Future<SubstackPublication?> _fromProfileHandle(String? handle) async {
    if (handle == null || handle.isEmpty) return null;
    final pub = await fetchPrimaryPublication(handle);
    if (pub == null) return null;
    for (final base in publicationFetchBases(pub)) {
      try {
        return await fetchPublication(base);
      } catch (_) {}
    }
    if (pub.subdomain.isEmpty) return null;
    try {
      return await fetchPublication(
        Uri(scheme: 'https', host: '${pub.subdomain}.substack.com'),
      );
    } catch (_) {
      return null;
    }
  }

  Future<SubstackPublication?> _fromLeftoverCustomDomain(String input) async {
    final raw = input.contains('://') ? input : 'https://$input';
    final uri = Uri.tryParse(raw);
    if (uri == null || uri.host.isEmpty) return null;
    if (isSubstackPublicationHost(uri.host) ||
        isSubstackServiceHost(uri.host)) {
      return null;
    }
    if (isObviousNonSubstackHost(uri.host)) return null;
    for (final candidate in substackHostCandidates(uri)) {
      if (sameSubstackHost(candidate.host, uri.host)) continue;
      try {
        return _bindFollowedHost(await fetchPublication(candidate), uri);
      } catch (_) {}
    }
    return null;
  }

  Future<SubstackPublication?> _fromRedirect(String input) async {
    final raw = input.contains('://') ? input : 'https://$input';
    final uri = Uri.tryParse(raw);
    if (uri == null || !isSubstackServiceHost(uri.host)) return null;
    try {
      final response = await httpClient.get(
        uri,
        headers: {
          'Accept': 'text/html,application/xhtml+xml',
          'User-Agent': _ua,
        },
      );
      final landed = response.request?.url;
      if (landed == null || landed.host.isEmpty) return null;
      if (sameSubstackHost(landed.host, uri.host)) return null;
      final postRef = resolveSubstackPostRef(landed.toString());
      if (postRef != null) return fetchPublication(postRef.base);
      final base = resolveSubstackBase(landed.toString());
      if (base == null) return null;
      return fetchPublication(base);
    } catch (e) {
      log.info('Redirect follow failed for $uri: $e');
      return null;
    }
  }

  bool _rssIsSubstack(Uri base, SubstackRssChannel rss) =>
      isSubstackPublicationHost(base.host) || rss.looksLikeSubstack;

  Future<_PostsResult> _fetchPostMaps(
    Uri base, {
    required int limit,
    required int offset,
  }) async {
    return _fetchJsonList(base, '/api/v1/posts', {
      'limit': '$limit',
      'offset': '$offset',
    });
  }

  Future<_PostsResult> _fetchArchiveMaps(
    Uri base, {
    required int limit,
    required int offset,
  }) async {
    return _fetchJsonList(base, '/api/v1/archive', {
      'sort': 'new',
      'limit': '$limit',
      'offset': '$offset',
    });
  }

  Future<_PostsResult> _fetchJsonList(
    Uri base,
    String path,
    Map<String, String> query,
  ) async {
    final uri = base.replace(path: path, queryParameters: query);
    final response = await _get(uri);
    final effectiveBase = _effectiveBase(response, base);
    final decoded = jsonDecode(response.body);
    if (decoded is! List) {
      return _PostsResult(base: effectiveBase, posts: const []);
    }
    return _PostsResult(
      base: effectiveBase,
      posts: decoded
          .whereType<Map>()
          .map((e) => Map<String, dynamic>.from(e))
          .toList(),
    );
  }

  Future<_PostsResult?> _fetchListingMaps(
    Uri base, {
    required int limit,
    required int offset,
  }) async {
    for (final fetch in [_fetchPostMaps, _fetchArchiveMaps]) {
      try {
        final result = await fetch(base, limit: limit, offset: offset);
        if (result.posts.isNotEmpty) return result;
      } catch (e) {
        log.info('Listing failed for $base: $e');
      }
    }
    return null;
  }

  Future<SubstackPublication?> _publicationFromListing(Uri base) async {
    try {
      final result = await _fetchListingMaps(base, limit: 1, offset: 0);
      if (result == null || result.posts.isEmpty) return null;
      return publicationFromPostJson(
        result.posts.first,
        fallbackBase: result.base,
      );
    } catch (e) {
      log.info('JSON publication probe failed for $base: $e');
      return null;
    }
  }

  SubstackPublication _publicationFromRss(Uri base, SubstackRssChannel rss) {
    final title = rss.title?.trim();
    return SubstackPublication(
      subdomain: subdomainOf(base),
      baseUrl: base.origin,
      name: title != null && title.isNotEmpty ? title : subdomainOf(base),
      description: rss.description,
      logoUrl: rss.imageUrl,
    );
  }

  Future<SubstackPublication?> _publicationFromHomepage(Uri base) async {
    try {
      final response = await httpClient.get(
        base,
        headers: {
          'Accept': 'text/html,application/xhtml+xml',
          'User-Agent': _ua,
        },
      );
      if (response.statusCode != 200) return null;
      return publicationFromHomepageHtml(utf8.decode(response.bodyBytes), base);
    } catch (e) {
      log.info('Homepage probe failed for $base: $e');
      return null;
    }
  }

  SubstackPublication _bindFollowedHost(
    SubstackPublication found,
    Uri requested,
  ) {
    if (isSubstackPublicationHost(requested.host) ||
        isSubstackServiceHost(requested.host)) {
      return found;
    }
    return SubstackPublication(
      subdomain: found.subdomain.isNotEmpty
          ? found.subdomain
          : subdomainOf(requested),
      baseUrl: Uri(scheme: 'https', host: requested.host).origin,
      name: found.name,
      description: found.description,
      logoUrl: found.logoUrl,
    );
  }

  String _resolvedName(
    SubstackPublication publication,
    Map<dynamic, dynamic> post,
    Uri fallbackBase,
  ) {
    final stored = publication.name.trim();
    final fromJson = publicationFromPostJson(
      Map<String, dynamic>.from(post),
      fallbackBase: fallbackBase,
    ).name.trim();
    if (fromJson.isEmpty) return stored;
    if (publicationNameLooksGeneric(stored) ||
        stored.toLowerCase() == publication.subdomain.toLowerCase() ||
        stored.toLowerCase() == subdomainOf(fallbackBase)) {
      return fromJson;
    }
    return stored.isNotEmpty ? stored : fromJson;
  }

  Future<SubstackRssChannel?> _fetchRss(
    Uri base, {
    SubstackPublication? publication,
  }) async {
    for (final path in _rssPaths) {
      final uri = base.replace(path: path, queryParameters: {});
      try {
        final response = await httpClient.get(
          uri,
          headers: {
            'Accept': 'application/rss+xml, application/xml, text/xml, */*',
            'User-Agent': _ua,
          },
        );
        if (response.statusCode != 200) continue;
        final body = utf8.decode(response.bodyBytes);
        if (!body.contains('<rss') && !body.contains('<feed')) continue;
        return parseSubstackRss(
          body,
          publicationBaseUrl: publication?.baseUrl ?? base.origin,
          publicationName: publication?.displayName ?? subdomainOf(base),
        );
      } catch (e) {
        log.info('RSS failed for $uri: $e');
      }
    }
    return null;
  }

  /// The discussion under a post, in reading order. An unreadable payload is
  /// an empty discussion rather than an error — the article is already shown.
  Future<List<SubstackComment>> fetchComments(
    SubstackPublication publication,
    String postId,
  ) async {
    Object? lastError;
    for (final base in publicationFetchBases(publication)) {
      final uri = base.replace(
        path: '/api/v1/post/$postId/comments',
        queryParameters: {'all_comments': 'true', 'sort': 'best_first'},
      );
      try {
        final response = await _get(uri);
        return flattenSubstackComments(jsonDecode(response.body));
      } catch (e) {
        lastError = e;
      }
    }
    if (lastError is SubstackClientException) throw lastError;
    return const [];
  }

  /// Posts matching [query] in the publication's archive, newest first.
  Future<List<SubstackPost>> searchPosts(
    SubstackPublication publication,
    String query, {
    int limit = 25,
  }) async {
    for (final base in publicationFetchBases(publication)) {
      try {
        return await _searchPostsOn(base, publication, query, limit);
      } catch (e) {
        log.info('Archive search failed for $base: $e');
      }
    }
    return const [];
  }

  Future<List<SubstackPost>> _searchPostsOn(
    Uri base,
    SubstackPublication publication,
    String query,
    int limit,
  ) async {
    final uri = base.replace(
      path: '/api/v1/archive',
      queryParameters: {
        'sort': 'new',
        'search': query,
        'limit': '$limit',
        'offset': '0',
      },
    );
    final response = await _get(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! List) {
      return const [];
    }
    return decoded
        .whereType<Map>()
        .map(
          (e) => SubstackPost.fromJson(
            Map<String, dynamic>.from(e),
            publicationBaseUrl: base.origin,
            publicationName: publication.displayName,
            includeBody: false,
          ),
        )
        .where((post) => post.title.isNotEmpty && post.slug.isNotEmpty)
        .toList();
  }

  /// Public Notes discovery feed (global mix served from a publication host).
  ///
  /// Not a personalized Following Notes timeline — that needs a Substack
  /// session. This is the open reader stream as a supplement.
  Future<SubstackNotesPage> fetchReaderNotes({
    String? host,
    String? cursor,
    int limit = 20,
  }) async {
    final baseHost = (host == null || host.isEmpty) ? 'substack.com' : host;
    final uri = Uri.https(baseHost, '/api/v1/reader/feed', {
      'limit': '$limit',
      if (cursor != null && cursor.isNotEmpty) 'cursor': cursor,
    });
    final response = await _get(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! Map) {
      return const SubstackNotesPage();
    }
    final root = Map<String, dynamic>.from(decoded);
    final items = root['items'];
    final notes = <SubstackNote>[];
    if (items is List) {
      for (final item in items.whereType<Map>()) {
        final note = SubstackNote.fromReaderItem(
          Map<String, dynamic>.from(item),
        );
        if (note.id.isNotEmpty && note.body.isNotEmpty) notes.add(note);
      }
    }
    return SubstackNotesPage(
      notes: notes,
      nextCursor: root['nextCursor'] as String?,
    );
  }

  /// Type-ahead publication search on substack.com.
  ///
  /// Some networks soft-empty this endpoint; [discoverPublications] pairs it
  /// with a slug probe so a typed handle still resolves.
  Future<List<SubstackPublication>> searchPublications(
    String query, {
    int page = 0,
  }) async {
    final trimmed = query.trim();
    if (trimmed.isEmpty) return const [];

    final uri = Uri.https('substack.com', '/api/v1/publication/search', {
      'query': trimmed,
      'page': '$page',
    });
    final response = await _get(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! Map) return const [];
    final results = decoded['results'];
    if (results is! List) return const [];
    return results
        .whereType<Map>()
        .map((e) => publicationFromDiscoveryJson(Map<String, dynamic>.from(e)))
        .whereType<SubstackPublication>()
        .where((p) => p.subdomain.isNotEmpty && p.baseUrl.isNotEmpty)
        .toList();
  }

  /// Publications this author recommends on their public `/recommendations` page.
  Future<List<SubstackRecommendation>> fetchRecommendedPublications(
    SubstackPublication publication,
  ) async {
    Object? lastError;
    for (final base in publicationFetchBases(publication)) {
      final uri = Uri(
        scheme: 'https',
        host: base.host,
        path: '/recommendations',
      );
      try {
        final response = await httpClient.get(
          uri,
          headers: {
            'Accept': 'text/html,application/xhtml+xml',
            'User-Agent': _ua,
          },
        );
        if (response.statusCode != 200) {
          lastError = SubstackClientException(
            'HTTP ${response.statusCode} loading $uri',
          );
          continue;
        }
        return parseSubstackRecommendationsHtml(
          utf8.decode(response.bodyBytes),
        );
      } catch (e) {
        lastError = e;
      }
    }
    throw lastError ?? SubstackClientException('Recommendations unavailable');
  }

  /// Author recommendations, padded with name-search hits for discovery.
  Future<List<SubstackRecommendation>> fetchSimilarPublications(
    SubstackPublication publication,
  ) async {
    Object? recError;
    var recommended = const <SubstackRecommendation>[];
    try {
      recommended = await fetchRecommendedPublications(publication);
    } catch (e) {
      recError = e;
      log.info('Recommendations page failed for ${publication.baseUrl}: $e');
    }

    Object? searchError;
    var searched = const <SubstackPublication>[];
    try {
      final query = publication.name.trim().isNotEmpty
          ? publication.name.trim()
          : publication.subdomain;
      searched = await searchPublications(query);
    } catch (e) {
      searchError = e;
      log.info('Similar search failed for ${publication.subdomain}: $e');
    }

    final merged = mergeSubstackSimilar(
      seed: publication,
      recommended: recommended,
      searched: searched,
    );
    if (merged.isEmpty && recError != null && searchError != null) {
      throw recError;
    }
    return merged;
  }

  /// Search plus handle/URL probe — what the Discover sheet actually calls.
  Future<List<SubstackPublication>> discoverPublications(String query) async {
    final trimmed = query.trim();
    if (trimmed.isEmpty) return const [];

    List<SubstackPublication> searched = const [];
    try {
      searched = await searchPublications(trimmed);
    } catch (e) {
      log.info('Publication search failed: $e');
    }
    if (searched.isNotEmpty) return searched;

    // Multi-word names are not a handle; the search API already had its chance.
    if (trimmed.contains(' ') &&
        !trimmed.contains('.') &&
        !trimmed.contains('/')) {
      return const [];
    }

    try {
      return [await resolvePublication(trimmed)];
    } catch (e) {
      log.info('Publication resolve failed: $e');
      if (trimmed.contains('://') || trimmed.contains('.')) rethrow;
      return const [];
    }
  }

  Future<List<SubstackCategory>> fetchCategories() async {
    final uri = Uri.https('substack.com', '/api/v1/categories');
    final response = await _get(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! List) return const [];
    return decoded
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .where((e) => e['parent_tag_id'] == null && e['deprecated'] != true)
        .map(SubstackCategory.fromJson)
        .where((c) => c.id > 0 && c.name.isNotEmpty)
        .toList();
  }

  /// Leaderboard / browse list for a category (`all` ≈ top free+paid mix).
  Future<List<SubstackPublication>> fetchCategoryPublications(
    int categoryId, {
    String tier = 'all',
    int page = 0,
  }) async {
    final uri = Uri.https(
      'substack.com',
      '/api/v1/category/public/$categoryId/$tier',
      {'page': '$page'},
    );
    final response = await _get(uri);
    final decoded = jsonDecode(response.body);
    if (decoded is! Map) return const [];
    final pubs = decoded['publications'];
    if (pubs is! List) return const [];
    return pubs
        .whereType<Map>()
        .map((e) => publicationFromDiscoveryJson(Map<String, dynamic>.from(e)))
        .whereType<SubstackPublication>()
        .where((p) => p.subdomain.isNotEmpty && p.baseUrl.isNotEmpty)
        .toList();
  }

  Future<http.Response> _get(Uri uri) async {
    final response = await httpClient.get(
      uri,
      headers: {'Accept': 'application/json', 'User-Agent': _ua},
    );
    if (response.statusCode != 200) {
      throw SubstackClientException('HTTP ${response.statusCode} loading $uri');
    }
    return response;
  }

  Uri _effectiveBase(http.Response response, Uri fallback) {
    final finalUrl = response.request?.url;
    if (finalUrl == null || finalUrl.host.isEmpty) return fallback;
    return Uri(scheme: 'https', host: finalUrl.host);
  }
}

class _PostsResult {
  final Uri base;
  final List<Map<String, dynamic>> posts;

  const _PostsResult({required this.base, required this.posts});
}

SubstackPublication publicationFromPostJson(
  Map<String, dynamic> post, {
  required Uri fallbackBase,
}) {
  final bylines = post['publishedBylines'];
  Map<String, dynamic>? publication;
  if (bylines is List && bylines.isNotEmpty) {
    final first = bylines.first;
    if (first is Map) {
      final users = first['publicationUsers'];
      if (users is List && users.isNotEmpty && users.first is Map) {
        final nested = users.first['publication'];
        if (nested is Map) publication = Map<String, dynamic>.from(nested);
      }
    }
  }

  var subdomain = (publication?['subdomain'] as String?)?.trim() ?? '';
  if (subdomain.isEmpty || subdomain.toLowerCase() == 'www') {
    subdomain = subdomainOf(fallbackBase);
  }
  // Keep the host that served these posts. Advertised custom_domain can point
  // at a leftover site that is no longer Substack-hosted.
  final rawName = (publication?['name'] as String?)?.trim();
  final name =
      rawName != null && rawName.isNotEmpty && rawName.toLowerCase() != 'www'
      ? rawName
      : subdomain;
  return SubstackPublication(
    subdomain: subdomain,
    baseUrl: fallbackBase.origin,
    name: name,
    description: publication?['hero_text'] as String?,
    logoUrl: publication?['logo_url'] as String?,
  );
}

class SubstackClientException implements Exception {
  final String message;
  SubstackClientException(this.message);
  @override
  String toString() => message;
}

/// The pasted address is a real site, but it is not a Substack publication.
class SubstackNotPublicationException extends SubstackClientException {
  SubstackNotPublicationException()
    : super('This is not a Substack publication');
}
