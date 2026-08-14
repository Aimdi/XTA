import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/utils/json.dart';

/// Why a Mastodon read could not be served, in terms the screen explains it.
enum MastodonErrorKind {
  notConfigured,
  network,
  notFound,
  rateLimited,
  unauthorized,
  badResponse,
}

class MastodonException implements Exception {
  final MastodonErrorKind kind;
  final String message;

  MastodonException(this.kind, this.message);

  @override
  String toString() => 'MastodonException{$kind: $message}';
}

/// Reads public Mastodon / Fediverse data through a home instance — no login.
class MastodonClient {
  final http.Client httpClient;

  MastodonClient({http.Client? httpClient})
    : httpClient = httpClient ?? http.Client();

  static const _timeout = Duration(seconds: 20);
  static const userAgent = 'XTA Mastodon plugin';

  Uri _uri(String instance, String path, [Map<String, String>? query]) {
    final base = normaliseMastodonInstance(instance);
    if (base == null) {
      throw MastodonException(
        MastodonErrorKind.notConfigured,
        'bad instance: $instance',
      );
    }
    final root = Uri.parse(base);
    return Uri(
      scheme: root.scheme,
      host: root.host,
      port: root.hasPort ? root.port : null,
      path: path,
      queryParameters: query,
    );
  }

  Future<Object?> _get(Uri uri) async {
    final http.Response response;
    try {
      response = await httpClient
          .get(
            uri,
            headers: {'User-Agent': userAgent, 'Accept': 'application/json'},
          )
          .timeout(_timeout);
    } catch (e) {
      throw MastodonException(MastodonErrorKind.network, '$uri: $e');
    }

    if (response.statusCode == 404) {
      throw MastodonException(MastodonErrorKind.notFound, '$uri: 404');
    }
    if (response.statusCode == 401 || response.statusCode == 403) {
      throw MastodonException(
        MastodonErrorKind.unauthorized,
        '$uri: ${response.statusCode}',
      );
    }
    if (response.statusCode == 429) {
      throw MastodonException(MastodonErrorKind.rateLimited, '$uri: 429');
    }
    if (response.statusCode != 200) {
      throw MastodonException(
        MastodonErrorKind.badResponse,
        '$uri: ${response.statusCode}',
      );
    }

    try {
      return jsonDecode(utf8.decode(response.bodyBytes));
    } catch (e) {
      throw MastodonException(MastodonErrorKind.badResponse, '$uri: $e');
    }
  }

  /// Runs [read] against each instance in turn until one answers.
  ///
  /// A miss on one instance says nothing about the next: a 404 is also what a
  /// Misskey-family origin answers on the Mastodon API, and an instance that
  /// closed its public timeline still leaves every other candidate worth
  /// asking. When the whole walk fails, the error kept is the most telling
  /// one — a throttle or a refusal explains more than the 404 the least
  /// conclusive instance ended on.
  Future<T> firstInstanceThat<T>(
    List<String> instances,
    Future<T> Function(String instance) read,
  ) async {
    if (instances.isEmpty) {
      throw MastodonException(
        MastodonErrorKind.notConfigured,
        'no instance to ask',
      );
    }

    MastodonException? worst;
    for (final instance in instances) {
      try {
        return await read(instance);
      } on MastodonException catch (e) {
        worst = _moreTelling(worst, e);
      }
    }

    throw worst!;
  }

  static MastodonException? _moreTelling(
    MastodonException? a,
    MastodonException? b,
  ) {
    int rank(MastodonException? e) => switch (e?.kind) {
      MastodonErrorKind.rateLimited => 5,
      MastodonErrorKind.unauthorized => 4,
      MastodonErrorKind.badResponse => 3,
      MastodonErrorKind.network => 2,
      MastodonErrorKind.notFound => 1,
      MastodonErrorKind.notConfigured || null => 0,
    };

    return rank(b) > rank(a) ? b : a;
  }

  /// [lookup] over [instances]: the profile from the first instance that knows
  /// the account.
  Future<MastodonProfile> lookupAnywhere(List<String> instances, String acct) =>
      firstInstanceThat(instances, (instance) => lookup(instance, acct));

  /// [fetchAccount] over [instances].
  ///
  /// The lookup and the statuses read stay on whichever instance answered:
  /// account ids are instance-local, so an id resolved on one is meaningless
  /// on the next.
  Future<List<MastodonPost>> fetchAccountAnywhere(
    List<String> instances,
    String acct, {
    int limit = 20,
  }) => firstInstanceThat(
    instances,
    (instance) => fetchAccount(instance, acct, limit: limit),
  );

  /// A profile and its first page of posts from one instance, walked the same
  /// way — both halves must come from the same place for the id to mean
  /// anything.
  Future<
    ({
      MastodonProfile profile,
      List<MastodonPost> posts,
      Set<String> pinnedIds,
      String instance,
    })
  >
  profileAnywhere(List<String> instances, String acct) =>
      firstInstanceThat(instances, (instance) async {
        final profile = await lookup(instance, acct);
        final posts = await getStatuses(instance, profile.id);
        var pinned = const <MastodonPost>[];
        try {
          pinned = await getStatuses(instance, profile.id, pinned: true);
        } on MastodonException catch (e) {
          if (e.kind == MastodonErrorKind.rateLimited) rethrow;
        }
        return (
          profile: profile,
          posts: mergeMastodonPinned(pinned, posts),
          pinnedIds: {for (final post in pinned) post.id},
          instance: instance,
        );
      });

  /// Confirms the instance answers the public instance metadata endpoint.
  Future<void> verify(String instance) async {
    try {
      await _get(_uri(instance, '/api/v2/instance'));
    } on MastodonException catch (e) {
      if (e.kind == MastodonErrorKind.notFound) {
        await _get(_uri(instance, '/api/v1/instance'));
        return;
      }
      rethrow;
    }
  }

  String? _homeDomain(String instance) =>
      mastodonInstanceDomain(normaliseMastodonInstance(instance) ?? instance);

  /// Resolve [acct] (local username or `user@domain`) on the home instance.
  Future<MastodonProfile> lookup(String instance, String acct) async {
    final normalised = normaliseMastodonAcct(acct);
    if (normalised == null) {
      throw MastodonException(
        MastodonErrorKind.notFound,
        'invalid acct: $acct',
      );
    }

    final json = await _get(
      _uri(instance, '/api/v1/accounts/lookup', {'acct': normalised}),
    );
    final profile = MastodonProfile.fromJson(
      json,
      homeDomain: _homeDomain(instance),
    );
    if (profile.id.isEmpty || profile.acct.isEmpty) {
      throw MastodonException(
        MastodonErrorKind.badResponse,
        'empty profile for $normalised',
      );
    }
    return profile;
  }

  Future<MastodonProfile> getAccount(String instance, String id) async {
    final json = await _get(_uri(instance, '/api/v1/accounts/$id'));
    return MastodonProfile.fromJson(json, homeDomain: _homeDomain(instance));
  }

  /// Recent public statuses by local account [id], newest first.
  Future<List<MastodonPost>> getStatuses(
    String instance,
    String id, {
    int limit = 20,
    bool excludeReplies = true,
    bool onlyMedia = false,
    bool pinned = false,
    String? maxId,
  }) async {
    final json = await _get(
      _uri(instance, '/api/v1/accounts/$id/statuses', {
        'limit': '$limit',
        'exclude_replies': '$excludeReplies',
        if (onlyMedia) 'only_media': 'true',
        if (pinned) 'pinned': 'true',
        'max_id': ?maxId,
      }),
    );
    return parseMastodonStatuses(json, homeDomain: _homeDomain(instance));
  }

  /// Lookup then statuses — what the merged feed needs for one followed acct.
  Future<List<MastodonPost>> fetchAccount(
    String instance,
    String acct, {
    int limit = 20,
  }) async {
    final profile = await lookup(instance, acct);
    return getStatuses(instance, profile.id, limit: limit);
  }

  /// One public status by local id on [instance].
  Future<MastodonPost> getStatus(String instance, String id) async {
    final json = await _get(_uri(instance, '/api/v1/statuses/$id'));
    final post = mastodonPostFromStatus(
      json,
      homeDomain: _homeDomain(instance),
    );
    if (post == null) {
      throw MastodonException(
        MastodonErrorKind.badResponse,
        'empty status $id',
      );
    }
    return post;
  }

  /// Ancestors and replies for a status on [instance].
  Future<({List<MastodonPost> ancestors, List<MastodonPost> descendants})>
  getContext(String instance, String id) async {
    final root = Json(
      await _get(_uri(instance, '/api/v1/statuses/$id/context')),
    );
    final home = _homeDomain(instance);
    return (
      ancestors: parseMastodonStatuses(root['ancestors'].raw, homeDomain: home),
      descendants: parseMastodonStatuses(
        root['descendants'].raw,
        homeDomain: home,
      ),
    );
  }

  /// Locate [seed] on [instance] and load its public reply context.
  ///
  /// Unauthenticated `/api/v2/search?resolve=true` is refused on most instances
  /// (401). Origin hosts often still serve `/statuses/:id` and `/context` for
  /// the snowflake in the public URL; other hosts can rediscover a federated
  /// copy by looking up the author and matching [MastodonPost.url] in their
  /// recent statuses — then `/context` uses that host's local id.
  Future<MastodonThread> fetchThread(String instance, MastodonPost seed) async {
    final home = _homeDomain(instance);
    final status = await _locateStatus(instance, seed);
    final context = await getContext(instance, status.id);
    return MastodonThread(
      status: status,
      ancestors: context.ancestors,
      descendants: context.descendants,
      homeDomain: home,
    );
  }

  /// [fetchThread] over [instances], same walk as profile lookups.
  Future<MastodonThread> fetchThreadAnywhere(
    List<String> instances,
    MastodonPost seed,
  ) => firstInstanceThat(instances, (instance) => fetchThread(instance, seed));

  Future<MastodonPost> _locateStatus(String instance, MastodonPost seed) async {
    final fromDirect = await _statusByKnownIds(instance, seed);
    if (fromDirect != null) {
      return fromDirect;
    }

    final fromSearch = await _resolveStatusSoft(instance, seed.url);
    if (fromSearch != null) {
      return fromSearch;
    }

    final fromAccount = await _findStatusInAccount(instance, seed);
    if (fromAccount != null) {
      return fromAccount;
    }

    throw MastodonException(
      MastodonErrorKind.notFound,
      'status not on $instance: ${seed.url}',
    );
  }

  /// Try [GET /statuses/:id] with the URL snowflake and the card's local id.
  Future<MastodonPost?> _statusByKnownIds(
    String instance,
    MastodonPost seed,
  ) async {
    final ids = <String>{
      if (mastodonStatusIdFromUrl(seed.url) case final fromUrl?) fromUrl,
      if (seed.id.trim().isNotEmpty) seed.id.trim(),
    };
    for (final id in ids) {
      try {
        final post = await getStatus(instance, id);
        // On a proxy, a coincidental numeric hit must still be the same post.
        if (sameMastodonStatusUrl(post.url, seed.url) || post.id == seed.id) {
          return post;
        }
      } on MastodonException catch (e) {
        if (e.kind == MastodonErrorKind.rateLimited) {
          rethrow;
        }
      }
    }
    return null;
  }

  /// Search resolve when the instance still allows it; 401/404 are not fatal.
  Future<MastodonPost?> _resolveStatusSoft(String instance, String url) async {
    final trimmed = url.trim();
    if (trimmed.isEmpty) {
      return null;
    }

    try {
      final json = Json(
        await _get(
          _uri(instance, '/api/v2/search', {
            'q': trimmed,
            'resolve': 'true',
            'type': 'statuses',
            'limit': '1',
          }),
        ),
      );
      final posts = parseMastodonStatuses(
        json['statuses'].raw,
        homeDomain: _homeDomain(instance),
      );
      return posts.isEmpty ? null : posts.first;
    } on MastodonException catch (e) {
      if (e.kind == MastodonErrorKind.rateLimited) {
        rethrow;
      }
      return null;
    }
  }

  /// Federated rediscovery: lookup the author, match [seed.url] in recent posts.
  Future<MastodonPost?> _findStatusInAccount(
    String instance,
    MastodonPost seed,
  ) async {
    final profile = await lookup(instance, seed.acct);
    String? maxId;
    for (var page = 0; page < 3; page++) {
      final posts = await _accountStatusesPage(
        instance,
        profile.id,
        maxId: maxId,
      );
      if (posts.isEmpty) {
        return null;
      }
      for (final post in posts) {
        if (sameMastodonStatusUrl(post.url, seed.url)) {
          return post;
        }
      }
      maxId = posts.last.id;
    }
    return null;
  }

  Future<List<MastodonPost>> _accountStatusesPage(
    String instance,
    String accountId, {
    String? maxId,
  }) async {
    final json = await _get(
      _uri(instance, '/api/v1/accounts/$accountId/statuses', {
        'limit': '40',
        'exclude_replies': 'false',
        if (maxId != null) 'max_id': maxId,
      }),
    );
    return parseMastodonStatuses(json, homeDomain: _homeDomain(instance));
  }

  /// Trending hashtags on [instance] (public, no login).
  Future<List<MastodonTrendingTag>> getTrendingTags(
    String instance, {
    int limit = 20,
  }) async {
    final json = await _get(
      _uri(instance, '/api/v1/trends/tags', {'limit': '$limit'}),
    );
    return parseMastodonTrendingTags(json);
  }

  /// [getTrendingTags] over [instances].
  Future<List<MastodonTrendingTag>> getTrendingTagsAnywhere(
    List<String> instances, {
    int limit = 20,
  }) => firstInstanceThat(
    instances,
    (instance) => getTrendingTags(instance, limit: limit),
  );

  /// Guest search: accounts, statuses, and hashtags in one call.
  Future<MastodonSearchPage> search(
    String instance,
    String q, {
    int limit = 20,
  }) async {
    final query = q.trim();
    if (query.isEmpty) {
      return const MastodonSearchPage();
    }
    final json = await _get(
      _uri(instance, '/api/v2/search', {'q': query, 'limit': '$limit'}),
    );
    return parseMastodonSearch(json, homeDomain: _homeDomain(instance));
  }

  /// [search] over [instances].
  Future<MastodonSearchPage> searchAnywhere(
    List<String> instances,
    String q, {
    int limit = 20,
  }) => firstInstanceThat(
    instances,
    (instance) => search(instance, q, limit: limit),
  );

  /// Account-only search — used when a caller only wants profiles.
  Future<List<MastodonProfile>> searchAccounts(
    String instance,
    String q, {
    int limit = 20,
  }) async {
    final page = await search(instance, q, limit: limit);
    return page.accounts;
  }

  /// [searchAccounts] over [instances].
  Future<List<MastodonProfile>> searchAccountsAnywhere(
    List<String> instances,
    String q, {
    int limit = 20,
  }) async {
    final page = await searchAnywhere(instances, q, limit: limit);
    return page.accounts;
  }

  /// Public hashtag timeline on [instance].
  Future<List<MastodonPost>> getTagTimeline(
    String instance,
    String tag, {
    int limit = 30,
    String? maxId,
  }) async {
    final name = tag.replaceFirst(RegExp(r'^#'), '').trim();
    if (name.isEmpty) {
      return const [];
    }
    final json = await _get(
      _uri(instance, '/api/v1/timelines/tag/$name', {
        'limit': '$limit',
        'max_id': ?maxId,
      }),
    );
    return parseMastodonStatuses(json, homeDomain: _homeDomain(instance));
  }

  /// [getTagTimeline] over [instances].
  Future<List<MastodonPost>> getTagTimelineAnywhere(
    List<String> instances,
    String tag, {
    int limit = 30,
  }) => firstInstanceThat(
    instances,
    (instance) => getTagTimeline(instance, tag, limit: limit),
  );

  /// Public local or federated timeline (Tusky / Ivory / Phanpy).
  Future<List<MastodonPost>> getPublicTimeline(
    String instance, {
    bool local = false,
    int limit = 30,
    String? maxId,
  }) async {
    try {
      final json = await _get(
        _uri(instance, '/api/v1/timelines/public', {
          'limit': '$limit',
          if (local) 'local': 'true',
          'max_id': ?maxId,
        }),
      );
      return parseMastodonStatuses(json, homeDomain: _homeDomain(instance));
    } on MastodonException catch (e) {
      if (e.kind == MastodonErrorKind.rateLimited) rethrow;
    }
    return _misskeyPublic(instance, local: local, limit: limit, untilId: maxId);
  }

  Future<List<MastodonPost>> getPublicTimelineAnywhere(
    List<String> instances, {
    bool local = false,
    int limit = 30,
  }) => firstInstanceThat(
    instances,
    (instance) => getPublicTimeline(instance, local: local, limit: limit),
  );

  /// Trending statuses on [instance] (public on most Mastodon hosts).
  Future<List<MastodonPost>> getTrendingStatuses(
    String instance, {
    int limit = 20,
  }) async {
    try {
      final json = await _get(
        _uri(instance, '/api/v1/trends/statuses', {'limit': '$limit'}),
      );
      return parseMastodonStatuses(json, homeDomain: _homeDomain(instance));
    } on MastodonException catch (e) {
      if (e.kind == MastodonErrorKind.rateLimited) rethrow;
    }
    return _misskeyPublic(instance, local: false, limit: limit);
  }

  Future<List<MastodonPost>> getTrendingStatusesAnywhere(
    List<String> instances, {
    int limit = 20,
  }) => firstInstanceThat(
    instances,
    (instance) => getTrendingStatuses(instance, limit: limit),
  );

  /// Misskey-family public notes when the Mastodon API is missing or empty.
  Future<List<MastodonPost>> _misskeyPublic(
    String instance, {
    required bool local,
    int limit = 20,
    String? untilId,
  }) async {
    if (local) {
      try {
        final notes = await _misskeyNotes(
          instance,
          '/api/notes/local-timeline',
          {'limit': limit, 'untilId': ?untilId},
        );
        if (notes.isNotEmpty) return notes;
      } on MastodonException catch (e) {
        if (e.kind == MastodonErrorKind.rateLimited) rethrow;
      }
    }
    if (untilId != null) {
      return const [];
    }
    return _misskeyNotes(instance, '/api/notes/featured', {'limit': limit});
  }

  Future<List<MastodonPost>> _misskeyNotes(
    String instance,
    String path,
    Map<String, Object?> body,
  ) async {
    final json = await _post(_uri(instance, path), body);
    return parseMisskeyNotes(json, instance: instance);
  }

  Future<Object?> _post(Uri uri, Map<String, Object?> body) async {
    final http.Response response;
    try {
      response = await httpClient
          .post(
            uri,
            headers: {
              'User-Agent': userAgent,
              'Accept': 'application/json',
              'Content-Type': 'application/json',
            },
            body: jsonEncode(body),
          )
          .timeout(_timeout);
    } catch (e) {
      throw MastodonException(MastodonErrorKind.network, '$uri: $e');
    }
    if (response.statusCode == 404) {
      throw MastodonException(MastodonErrorKind.notFound, '$uri: 404');
    }
    if (response.statusCode == 401 || response.statusCode == 403) {
      throw MastodonException(
        MastodonErrorKind.unauthorized,
        '$uri: ${response.statusCode}',
      );
    }
    if (response.statusCode == 429) {
      throw MastodonException(MastodonErrorKind.rateLimited, '$uri: 429');
    }
    if (response.statusCode != 200) {
      throw MastodonException(
        MastodonErrorKind.badResponse,
        '$uri: ${response.statusCode}',
      );
    }
    try {
      return jsonDecode(utf8.decode(response.bodyBytes));
    } catch (e) {
      throw MastodonException(MastodonErrorKind.badResponse, '$uri: $e');
    }
  }
}
