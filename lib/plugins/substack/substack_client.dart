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

  Future<SubstackPublication> fetchPublication(Uri base) async {
    try {
      final result = await _fetchPostMaps(base, limit: 1, offset: 0);
      if (result.posts.isNotEmpty) {
        return publicationFromPostJson(
          result.posts.first,
          fallbackBase: result.base,
        );
      }
    } catch (e) {
      log.info('JSON publication probe failed for $base: $e');
    }

    final rss = await _fetchRss(base);
    if (rss != null) {
      return SubstackPublication(
        subdomain: subdomainOf(base),
        baseUrl: base.origin,
        name: (rss.title?.trim().isNotEmpty == true)
            ? rss.title!.trim()
            : subdomainOf(base),
        description: rss.description,
        logoUrl: rss.imageUrl,
      );
    }

    return SubstackPublication(
      subdomain: subdomainOf(base),
      baseUrl: base.origin,
      name: subdomainOf(base),
    );
  }

  Future<List<SubstackPost>> fetchPosts(
    SubstackPublication publication, {
    int limit = 12,
    int offset = 0,
  }) async {
    final base = Uri.parse(publication.baseUrl);
    try {
      final result = await _fetchPostMaps(base, limit: limit, offset: offset);
      final posts = result.posts
          .map(
            (e) => SubstackPost.fromJson(
              e,
              publicationBaseUrl: publication.baseUrl,
              publicationName: publication.name,
              includeBody: false,
            ),
          )
          .where((e) => e.title.isNotEmpty && e.slug.isNotEmpty)
          .toList();
      if (posts.isNotEmpty || offset > 0) {
        return posts;
      }
    } catch (e) {
      log.info('JSON posts failed for ${publication.baseUrl}: $e — trying RSS');
    }

    // RSS has no offset pagination worth relying on; only use it for the first page.
    if (offset > 0) return const [];
    final rss = await _fetchRss(base, publication: publication);
    return (rss?.posts ?? const []).take(limit).toList(growable: false);
  }

  Future<SubstackPost> fetchPost(
    SubstackPublication publication,
    String slug,
  ) async {
    final base = Uri.parse(publication.baseUrl);
    final uri = base.replace(path: '/api/v1/posts/$slug');
    try {
      final response = await _get(uri);
      final decoded = jsonDecode(response.body);
      if (decoded is Map) {
        final post = SubstackPost.fromJson(
          Map<String, dynamic>.from(decoded),
          publicationBaseUrl: publication.baseUrl,
          publicationName: publication.name,
          includeBody: true,
        );
        if (post.title.isNotEmpty && post.slug.isNotEmpty) {
          return post;
        }
      }
    } catch (e) {
      log.info('JSON post failed for $slug: $e');
    }

    final rss = await _fetchRss(base, publication: publication);
    final match = rss?.posts.where((p) => p.slug == slug).firstOrNull;
    if (match != null) return match;
    throw SubstackClientException('Post not found: $slug');
  }

  Future<_PostsResult> _fetchPostMaps(
    Uri base, {
    required int limit,
    required int offset,
  }) async {
    final uri = base.replace(
      path: '/api/v1/posts',
      queryParameters: {'limit': '$limit', 'offset': '$offset'},
    );
    final response = await _get(uri);
    final effectiveBase = _effectiveBase(response, base);
    final decoded = jsonDecode(response.body);
    if (decoded is! List) {
      return _PostsResult(base: effectiveBase, posts: const []);
    }
    final posts = decoded
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    return _PostsResult(base: effectiveBase, posts: posts);
  }

  Future<SubstackRssChannel?> _fetchRss(
    Uri base, {
    SubstackPublication? publication,
  }) async {
    final uri = base.replace(path: '/feed', queryParameters: {});
    try {
      final response = await httpClient.get(
        uri,
        headers: {
          'Accept': 'application/rss+xml, application/xml, text/xml, */*',
          'User-Agent': _ua,
        },
      );
      if (response.statusCode != 200) return null;
      final body = utf8.decode(response.bodyBytes);
      if (!body.contains('<rss') && !body.contains('<feed')) return null;
      return parseSubstackRss(
        body,
        publicationBaseUrl: publication?.baseUrl ?? base.origin,
        publicationName: publication?.name ?? subdomainOf(base),
      );
    } catch (e) {
      log.info('RSS failed for $base: $e');
      return null;
    }
  }

  /// The discussion under a post, in reading order. An unreadable payload is
  /// an empty discussion rather than an error — the article is already shown.
  Future<List<SubstackComment>> fetchComments(
    SubstackPublication publication,
    String postId,
  ) async {
    final base = Uri.parse(publication.baseUrl);
    final uri = base.replace(
      path: '/api/v1/post/$postId/comments',
      queryParameters: {'all_comments': 'true', 'sort': 'best_first'},
    );
    try {
      final response = await _get(uri);
      return flattenSubstackComments(jsonDecode(response.body));
    } on SubstackClientException {
      rethrow;
    } catch (_) {
      return const [];
    }
  }

  /// Posts matching [query] in the publication's archive, newest first.
  Future<List<SubstackPost>> searchPosts(
    SubstackPublication publication,
    String query, {
    int limit = 25,
  }) async {
    final base = Uri.parse(publication.baseUrl);
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
            publicationBaseUrl: publication.baseUrl,
            publicationName: publication.name,
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
    final base = Uri.parse(publication.baseUrl);
    final uri = Uri(scheme: 'https', host: base.host, path: '/recommendations');
    final response = await httpClient.get(
      uri,
      headers: {'Accept': 'text/html,application/xhtml+xml', 'User-Agent': _ua},
    );
    if (response.statusCode != 200) {
      throw SubstackClientException('HTTP ${response.statusCode} loading $uri');
    }
    return parseSubstackRecommendationsHtml(utf8.decode(response.bodyBytes));
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

    final base = resolveSubstackBase(trimmed);
    if (base == null) return const [];

    // Multi-word queries are unlikely to be a subdomain; skip the probe.
    if (trimmed.contains(' ') &&
        !trimmed.contains('.') &&
        !trimmed.contains('/')) {
      return const [];
    }

    try {
      return [await fetchPublication(base)];
    } catch (e) {
      log.info('Slug probe failed for $base: $e');
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

  final subdomain =
      publication?['subdomain'] as String? ?? subdomainOf(fallbackBase);
  final custom = publication?['custom_domain'] as String?;
  final baseUrl = custom != null && custom.isNotEmpty
      ? Uri(scheme: 'https', host: custom).origin
      : (publication != null
            ? 'https://$subdomain.substack.com'
            : fallbackBase.origin);

  return SubstackPublication(
    subdomain: subdomain,
    baseUrl: baseUrl,
    name: publication?['name'] as String? ?? subdomain,
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
