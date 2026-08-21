import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/utils/json.dart';

/// Why a Bluesky read could not be served, in terms the screen explains it.
enum BlueskyErrorKind { network, notFound, rateLimited, badResponse }

class BlueskyException implements Exception {
  final BlueskyErrorKind kind;
  final String message;

  BlueskyException(this.kind, this.message);

  @override
  String toString() => 'BlueskyException{$kind: $message}';
}

/// One page of an author feed from the public AppView.
class BlueskyFeedPage {
  final List<BlueskyPost> posts;
  final String? cursor;

  const BlueskyFeedPage({required this.posts, this.cursor});
}

/// Reads Bluesky through an AppView — no account, no write actions.
///
/// [resolveBaseUrl] is consulted per request so Settings can change the AppView
/// without rebuilding the client. Empty / invalid values fall back to
/// [kBlueskyDefaultAppView].
class BlueskyClient {
  final http.Client httpClient;
  final String Function() resolveBaseUrl;

  BlueskyClient({
    http.Client? httpClient,
    String? baseUrl,
    String Function()? resolveBaseUrl,
  }) : httpClient = httpClient ?? http.Client(),
       resolveBaseUrl =
           resolveBaseUrl ?? (() => baseUrl ?? kBlueskyDefaultAppView);

  static const _timeout = Duration(seconds: 20);
  static const userAgent = 'XTA Bluesky plugin';

  /// Effective AppView root for the next request.
  String get baseUrl => blueskyAppViewFromPrefs(resolveBaseUrl());

  Uri _uri(String path, [Map<String, String>? query]) {
    final base = baseUrl.replaceAll(RegExp(r'/+$'), '');
    return Uri.parse('$base$path').replace(queryParameters: query);
  }

  Future<Json> _get(Uri uri) async {
    final http.Response response;
    try {
      response = await httpClient
          .get(
            uri,
            headers: {'User-Agent': userAgent, 'Accept': 'application/json'},
          )
          .timeout(_timeout);
    } catch (e) {
      throw BlueskyException(BlueskyErrorKind.network, '$uri: $e');
    }

    if (response.statusCode == 404) {
      throw BlueskyException(BlueskyErrorKind.notFound, '$uri: 404');
    }
    if (response.statusCode == 429) {
      throw BlueskyException(BlueskyErrorKind.rateLimited, '$uri: 429');
    }
    if (response.statusCode != 200) {
      throw BlueskyException(
        BlueskyErrorKind.badResponse,
        '$uri: ${response.statusCode}',
      );
    }

    try {
      return Json(jsonDecode(utf8.decode(response.bodyBytes)));
    } catch (e) {
      throw BlueskyException(BlueskyErrorKind.badResponse, '$uri: $e');
    }
  }

  /// Confirms the AppView answers a known public profile.
  Future<void> verify() async {
    await getProfile('bsky.app');
  }

  /// Profile for [actor] (handle or DID).
  Future<BlueskyProfile> getProfile(String actor) async {
    final json = await _get(
      _uri('/xrpc/app.bsky.actor.getProfile', {'actor': actor}),
    );
    final profile = BlueskyProfile.fromJson(json.raw);
    if (profile.did.isEmpty && profile.handle.isEmpty) {
      throw BlueskyException(
        BlueskyErrorKind.badResponse,
        'empty profile for $actor',
      );
    }
    return profile;
  }

  /// Recent posts by [actor], newest first within the page.
  ///
  /// [filter] is an official AppView value: `posts_and_author_threads`,
  /// `posts_with_replies`, `posts_with_media`.
  Future<BlueskyFeedPage> getAuthorFeed(
    String actor, {
    int limit = 20,
    String? cursor,
    String? filter,
  }) async {
    final query = <String, String>{'actor': actor, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      query['cursor'] = cursor;
    }
    if (filter != null && filter.isNotEmpty) {
      query['filter'] = filter;
    }

    final json = await _get(_uri('/xrpc/app.bsky.feed.getAuthorFeed', query));
    return BlueskyFeedPage(
      posts: parseBlueskyFeed(json.raw),
      cursor: json['cursor'].string,
    );
  }

  /// A custom feed generator (`app.bsky.feed.getFeed`).
  Future<BlueskyFeedPage> getFeed(
    String feed, {
    int limit = 30,
    String? cursor,
  }) async {
    final query = <String, String>{'feed': feed, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      query['cursor'] = cursor;
    }
    final json = await _get(_uri('/xrpc/app.bsky.feed.getFeed', query));
    return BlueskyFeedPage(
      posts: parseBlueskyFeed(json.raw),
      cursor: json['cursor'].string,
    );
  }

  /// Posts from a public list (`app.bsky.feed.getListFeed`).
  Future<BlueskyFeedPage> getListFeed(
    String list, {
    int limit = 30,
    String? cursor,
  }) async {
    final query = <String, String>{'list': list, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      query['cursor'] = cursor;
    }
    final json = await _get(_uri('/xrpc/app.bsky.feed.getListFeed', query));
    return BlueskyFeedPage(
      posts: parseBlueskyFeed(json.raw),
      cursor: json['cursor'].string,
    );
  }

  /// Actors matching [q], as the AppView's search returns them.
  Future<List<BlueskyProfile>> searchActors(String q, {int limit = 10}) async {
    final json = await _get(
      _uri('/xrpc/app.bsky.actor.searchActors', {'q': q, 'limit': '$limit'}),
    );
    return [
      for (final actor in json['actors'].list)
        BlueskyProfile.fromJson(actor.raw),
    ];
  }

  /// Suggested accounts from the public AppView (guest Discover).
  Future<List<BlueskyProfile>> getSuggestions({int limit = 20}) async {
    final json = await _get(
      _uri('/xrpc/app.bsky.actor.getSuggestions', {'limit': '$limit'}),
    );
    return [
      for (final actor in json['actors'].list)
        BlueskyProfile.fromJson(actor.raw),
    ];
  }

  /// Posts matching [q] via the public AppView search index.
  Future<BlueskyFeedPage> searchPosts(
    String q, {
    int limit = 20,
    String? cursor,
  }) async {
    final query = <String, String>{'q': q, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      query['cursor'] = cursor;
    }
    final json = await _get(_uri('/xrpc/app.bsky.feed.searchPosts', query));
    return BlueskyFeedPage(
      posts: parseBlueskySearchPosts(json.raw),
      cursor: json['cursor'].string,
    );
  }

  /// One post and its surrounding conversation via the public AppView.
  Future<BlueskyThread> getPostThread(
    String uri, {
    int depth = 10,
    int parentHeight = 80,
  }) async {
    final json = await _get(
      _uri('/xrpc/app.bsky.feed.getPostThread', {
        'uri': uri,
        'depth': '$depth',
        'parentHeight': '$parentHeight',
      }),
    );
    final thread = parseBlueskyThread(json.raw);
    if (thread == null) {
      throw BlueskyException(
        BlueskyErrorKind.notFound,
        'thread missing for $uri',
      );
    }
    return thread;
  }

  /// Public accounts [actor] follows (profile lists and local import).
  Future<BlueskyFollowsPage> getFollows(
    String actor, {
    int limit = 100,
    String? cursor,
  }) async {
    final query = <String, String>{'actor': actor, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      query['cursor'] = cursor;
    }
    final json = await _get(_uri('/xrpc/app.bsky.graph.getFollows', query));
    return parseBlueskyFollowsPage(json.raw);
  }

  /// Public accounts that follow [actor] (read-only AppView).
  Future<BlueskyFollowersPage> getFollowers(
    String actor, {
    int limit = 100,
    String? cursor,
  }) async {
    final query = <String, String>{'actor': actor, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      query['cursor'] = cursor;
    }
    final json = await _get(_uri('/xrpc/app.bsky.graph.getFollowers', query));
    return parseBlueskyFollowersPage(json.raw);
  }

  /// Lists created by [actor] (public metadata only).
  Future<BlueskyListsPage> getLists(
    String actor, {
    int limit = 50,
    String? cursor,
  }) async {
    final query = <String, String>{'actor': actor, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      query['cursor'] = cursor;
    }
    final json = await _get(_uri('/xrpc/app.bsky.graph.getLists', query));
    return parseBlueskyListsPage(json.raw);
  }

  /// Members of a public list identified by its AT-URI.
  Future<BlueskyListMembersPage> getList(
    String listUri, {
    int limit = 100,
    String? cursor,
  }) async {
    final query = <String, String>{'list': listUri, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      query['cursor'] = cursor;
    }
    final json = await _get(_uri('/xrpc/app.bsky.graph.getList', query));
    return parseBlueskyListMembersPage(json.raw);
  }

  /// Resolves a web list URL or AT-URI into an `at://…/app.bsky.graph.list/…`.
  Future<String> resolveListUri(BlueskyListRef ref) async {
    final atUri = ref.atUri?.trim();
    if (atUri != null && atUri.isNotEmpty) {
      return atUri;
    }

    final actor = ref.actor?.trim();
    final rkey = ref.rkey?.trim();
    if (actor == null || actor.isEmpty || rkey == null || rkey.isEmpty) {
      throw BlueskyException(
        BlueskyErrorKind.badResponse,
        'incomplete list reference',
      );
    }

    final profile = await getProfile(actor);
    if (profile.did.isEmpty) {
      throw BlueskyException(
        BlueskyErrorKind.notFound,
        'list owner missing did: $actor',
      );
    }
    return 'at://${profile.did}/app.bsky.graph.list/$rkey';
  }

  /// One public starter pack (`app.bsky.graph.getStarterPack`).
  Future<Object?> getStarterPack(String starterPack) async {
    final json = await _get(
      _uri('/xrpc/app.bsky.graph.getStarterPack', {'starterPack': starterPack}),
    );
    return json.raw;
  }

  /// Posts from a public custom feed generator (`app.bsky.feed.getFeed`).
  Future<BlueskyFeedPage> getFeed(
    String feed, {
    int limit = 30,
    String? cursor,
  }) async {
    final query = <String, String>{'feed': feed, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      query['cursor'] = cursor;
    }
    final json = await _get(_uri('/xrpc/app.bsky.feed.getFeed', query));
    return BlueskyFeedPage(
      posts: parseBlueskyFeed(json.raw),
      cursor: json['cursor'].string,
    );
  }

  /// Posts from a public list (`app.bsky.feed.getListFeed`).
  Future<BlueskyFeedPage> getListFeed(
    String list, {
    int limit = 30,
    String? cursor,
  }) async {
    final query = <String, String>{'list': list, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      query['cursor'] = cursor;
    }
    final json = await _get(_uri('/xrpc/app.bsky.feed.getListFeed', query));
    return BlueskyFeedPage(
      posts: parseBlueskyFeed(json.raw),
      cursor: json['cursor'].string,
    );
  }

  /// Metadata for one feed generator.
  Future<BlueskyFeedGenerator> getFeedGenerator(String feed) async {
    final json = await _get(
      _uri('/xrpc/app.bsky.feed.getFeedGenerator', {'feed': feed}),
    );
    final generator = BlueskyFeedGenerator.fromJson(
      json['view'].exists ? json['view'].raw : json.raw,
    );
    if (generator.uri.isEmpty) {
      throw BlueskyException(
        BlueskyErrorKind.badResponse,
        'empty feed generator for $feed',
      );
    }
    return generator;
  }

  /// Metadata for several feed generators (pinned / known Discover URIs).
  Future<List<BlueskyFeedGenerator>> getFeedGenerators(
    List<String> feeds,
  ) async {
    final uris = [
      for (final feed in feeds)
        if (feed.trim().isNotEmpty) feed.trim(),
    ].take(25).toList(growable: false);
    if (uris.isEmpty) {
      return const [];
    }
    final query = uris
        .map((u) => 'feeds=${Uri.encodeQueryComponent(u)}')
        .join('&');
    final base = _uri('/xrpc/app.bsky.feed.getFeedGenerators');
    final json = await _get(
      Uri.parse('$base${base.hasQuery ? '&' : '?'}$query'),
    );
    return parseBlueskyFeedGenerators(json.raw);
  }

  /// Guest-visible popular / trending custom feeds (the same catalog bsky.app
  /// uses for Discover).
  Future<BlueskyFeedGeneratorsPage> getPopularFeedGenerators({
    int limit = 20,
    String? cursor,
    String? query,
  }) async {
    final params = <String, String>{'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      params['cursor'] = cursor;
    }
    if (query != null && query.isNotEmpty) {
      params['query'] = query;
    }
    final json = await _get(
      _uri('/xrpc/app.bsky.unspecced.getPopularFeedGenerators', params),
    );
    return parseBlueskyFeedGeneratorsPage(json.raw);
  }

  /// Feed generators [actor] created (public, not their saved/pinned prefs).
  Future<BlueskyFeedGeneratorsPage> getActorFeeds(
    String actor, {
    int limit = 50,
    String? cursor,
  }) async {
    final params = <String, String>{'actor': actor, 'limit': '$limit'};
    if (cursor != null && cursor.isNotEmpty) {
      params['cursor'] = cursor;
    }
    final json = await _get(_uri('/xrpc/app.bsky.feed.getActorFeeds', params));
    return parseBlueskyFeedGeneratorsPage(json.raw);
  }

  /// Resolves a web feed URL or AT-URI into
  /// `at://…/app.bsky.feed.generator/…`.
  Future<String> resolveFeedUri(BlueskyFeedRef ref) async {
    final atUri = ref.atUri?.trim();
    if (atUri != null && atUri.isNotEmpty) {
      return atUri;
    }

    final actor = ref.actor?.trim();
    final rkey = ref.rkey?.trim();
    if (actor == null || actor.isEmpty || rkey == null || rkey.isEmpty) {
      throw BlueskyException(
        BlueskyErrorKind.badResponse,
        'incomplete feed reference',
      );
    }

    final profile = await getProfile(actor);
    if (profile.did.isEmpty) {
      throw BlueskyException(
        BlueskyErrorKind.notFound,
        'feed owner missing did: $actor',
      );
    }
    return 'at://${profile.did}/app.bsky.feed.generator/$rkey';
  }

  /// Resolves a web starter-pack URL or AT-URI into
  /// `at://…/app.bsky.graph.starterpack/…`.
  Future<String> resolveStarterPackUri(BlueskyStarterPackRef ref) async {
    final atUri = ref.atUri?.trim();
    if (atUri != null && atUri.isNotEmpty) {
      return atUri;
    }

    final actor = ref.actor?.trim();
    final rkey = ref.rkey?.trim();
    if (actor == null || actor.isEmpty || rkey == null || rkey.isEmpty) {
      throw BlueskyException(
        BlueskyErrorKind.badResponse,
        'incomplete starter pack reference',
      );
    }

    final profile = await getProfile(actor);
    if (profile.did.isEmpty) {
      throw BlueskyException(
        BlueskyErrorKind.notFound,
        'starter pack owner missing did: $actor',
      );
    }
    return 'at://${profile.did}/app.bsky.graph.starterpack/$rkey';
  }
}
