import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_parse.dart';
import 'package:xta/utils/json.dart';

export 'package:xta/plugins/threads/threads_parse.dart';

const _threadsWeb = threadsWebBase;
const _instagramApi = 'https://i.instagram.com';
const _igAppId = '238260118697367';
const _barcelonaUa = 'Barcelona 289.0.0.77.109 Android';
const _safariUa =
    'Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1';

/// Guest profile threads — `BarcelonaProfileThreadsTabQuery`. Rotates; if it
/// 404s or returns empty, [fetchGuestAccount] falls back to SSR `thread_items`.
const threadsGuestProfileThreadsDocId = '6232751443445612';

/// Cookie names a browser Threads session must carry for cookie REST reads.
const _requiredCookieKeys = [
  'sessionid',
  'csrftoken',
  'ds_user_id',
  'mid',
  'ig_did',
];

/// Parses a pasted Cookie header (or `name=value; …`) into a map.
Map<String, String> parseThreadsCookieHeader(String raw) {
  final out = <String, String>{};
  for (final part in raw.split(';')) {
    final i = part.indexOf('=');
    if (i <= 0) continue;
    final name = part.substring(0, i).trim();
    final value = part.substring(i + 1).trim();
    if (name.isNotEmpty && value.isNotEmpty) {
      out[name] = value;
    }
  }
  return out;
}

/// True when the paste includes every cookie the cookie REST path needs.
bool threadsCookiesComplete(Map<String, String> cookies) =>
    _requiredCookieKeys.every((k) => (cookies[k] ?? '').isNotEmpty);

String? normaliseThreadsBearer(String raw) {
  var value = raw.trim();
  if (value.isEmpty) return null;
  if (value.toLowerCase().startsWith('bearer ')) {
    value = value.substring(7).trim();
  }
  if (!value.startsWith('IGT:2:')) return null;
  return value;
}

/// Read-only Meta client: cookies on threads.com, Bearer on i.instagram.com,
/// guest SSR when neither is set.
class ThreadsDirectClient {
  final http.Client httpClient;
  final BasePrefService prefs;
  final Duration minGap;
  DateTime? _lastRequestAt;
  DateTime? _cooldownUntil;

  /// Concurrent profile + posts for the same handle used to GET `/@handle`
  /// twice (two paced round-trips). Share one in-flight HTML body instead —
  /// fewer requests to Meta, not more.
  final Map<String, Future<String>> _profileHtmlInFlight = {};

  /// The guest LSD token off the last profile page, reused across accounts.
  ///
  /// The token is page-scoped, not profile-scoped: one page's token serves
  /// every account's GraphQL call for a while. Without this, each account
  /// cost the profile HTML *and* the GraphQL call — two paced round-trips
  /// where one is enough, which doubled how long the tab took to fill.
  String? _guestLsd;
  DateTime? _guestLsdAt;

  static const _guestLsdTtl = Duration(minutes: 10);

  String? get _freshGuestLsd {
    final memory = _guestLsdFrom(_guestLsd, _guestLsdAt);
    if (memory != null) {
      return memory;
    }
    final stored = prefs.get<String>(optionPluginThreadsGuestLsd);
    final at = DateTime.tryParse(
      prefs.get<String>(optionPluginThreadsGuestLsdAt) ?? '',
    );
    final fromPrefs = _guestLsdFrom(stored, at);
    if (fromPrefs != null) {
      _guestLsd = fromPrefs;
      _guestLsdAt = at;
    }
    return fromPrefs;
  }

  String? _guestLsdFrom(String? lsd, DateTime? at) {
    if (lsd == null || lsd.isEmpty || at == null) {
      return null;
    }
    if (DateTime.now().difference(at) > _guestLsdTtl) {
      return null;
    }
    return lsd;
  }

  void _rememberGuestLsd(String? lsd) {
    if (lsd == null || lsd.isEmpty) {
      return;
    }
    _guestLsd = lsd;
    _guestLsdAt = DateTime.now();
    unawaited(
      Future<void>.sync(() async {
        await prefs.set(optionPluginThreadsGuestLsd, lsd);
        await prefs.set(
          optionPluginThreadsGuestLsdAt,
          _guestLsdAt!.toIso8601String(),
        );
      }),
    );
  }

  ThreadsDirectClient(
    this.prefs, {
    http.Client? httpClient,
    this.minGap = threadsSessionMinGap,
  }) : httpClient = httpClient ?? http.Client();

  static const _timeout = Duration(seconds: 25);

  Map<String, String> get cookies => parseThreadsCookieHeader(
    prefs.get<String>(optionPluginThreadsDirectCookies) ?? '',
  );

  String? get bearer => normaliseThreadsBearer(
    prefs.get<String>(optionPluginThreadsDirectBearer) ?? '',
  );

  bool get hasCookies => threadsCookiesComplete(cookies);

  bool get hasBearer => bearer != null;

  bool get hasDirectAuth => hasCookies || hasBearer;

  /// Cookie REST / people search — off unless the reader opts in. Guest
  /// GraphQL is how followed accounts are read by default, so a pasted
  /// session is not spent on every refresh.
  bool get useSessionApis =>
      prefs.get<bool>(optionPluginThreadsUseSessionApis) == true;

  /// True while Meta has asked this session to stop (throttle / login_required).
  bool get isSessionParked {
    final stored = DateTime.tryParse(
      prefs.get<String>(optionPluginThreadsDirectCooldownUntil) ?? '',
    );
    final until = stored ?? _cooldownUntil;
    return until != null && DateTime.now().isBefore(until);
  }

  Future<String> _deviceId() async {
    final existing =
        (prefs.get<String>(optionPluginThreadsDirectDeviceId) ?? '').trim();
    if (existing.isNotEmpty) return existing;
    final created = _randomDeviceId();
    await prefs.set(optionPluginThreadsDirectDeviceId, created);
    return created;
  }

  /// Requests leave one at a time, in order.
  ///
  /// [_pace] used to be awaited concurrently: two fetches both read
  /// [_lastRequestAt], both computed the same wait, and both fired at the end
  /// of it — so the gap between requests was never actually kept and Meta saw
  /// bursts. A queue is what makes the gap real, and looking like one person
  /// reading is the whole defence this plugin has.
  Future<void> _queue = Future<void>.value();

  final Random _jitter = Random();

  /// Serialises departures behind every request already waiting, keeping the
  /// gap between them — but releases the queue the moment a request has left.
  ///
  /// The gap is between *departures*: holding the slot until the response
  /// came back meant one slow account stalled every request behind it, and
  /// the whole tab paid that account's timeout. Responses may overlap; only
  /// the starts are paced, which is what the defence actually needs.
  ///
  /// Guest GraphQL/SSR must not honour the cookie/Bearer cooldown: a dead
  /// session parking the plugin for 30 minutes was also blocking the public
  /// path that still returns posts for followed Accounts.
  Future<T> _enqueue<T>(
    Future<T> Function() run, {
    bool respectCooldown = true,
  }) {
    final departed = _queue.then(
      (_) => _pace(respectCooldown: respectCooldown),
    );
    // A refused departure (cooldown) must not poison the queue behind it.
    _queue = departed.then((_) {}, onError: (Object _) {});

    return departed.then((_) => run());
  }

  Future<void> _pace({bool respectCooldown = true}) async {
    if (respectCooldown) {
      if (await _coolingDown() case final until?) {
        throw ThreadsException(
          ThreadsErrorKind.sessionSuspended,
          'cooling down until $until',
        );
      }
    }

    final last = _lastRequestAt;
    if (last != null) {
      // Session traffic keeps the strict floor — Meta bans accounts that look
      // scripted. Guest GraphQL has no session to lose, so it may leave sooner;
      // the queue still serialises departures, just with a shorter gap.
      final floor = respectCooldown ? minGap : threadsGuestMinGap;
      final jitterMs = respectCooldown ? 750 : 350;
      final gap = floor + Duration(milliseconds: _jitter.nextInt(jitterMs));
      final wait = gap - DateTime.now().difference(last);
      if (wait > Duration.zero) {
        await Future<void>.delayed(wait);
      }
    }
    _lastRequestAt = DateTime.now();
  }

  /// When the session is parked, or null when it may talk to Meta.
  Future<DateTime?> _coolingDown() async {
    final stored = DateTime.tryParse(
      prefs.get<String>(optionPluginThreadsDirectCooldownUntil) ?? '',
    );
    final until = stored ?? _cooldownUntil;
    if (until == null) {
      return null;
    }
    if (DateTime.now().isBefore(until)) {
      return until;
    }

    _cooldownUntil = null;
    if (stored != null) {
      await prefs.set(optionPluginThreadsDirectCooldownUntil, '');
    }

    return null;
  }

  /// Backs off after Meta says to.
  ///
  /// Written to preferences as well as held here: a cooldown that only lives in
  /// memory ends the moment the reader force-quits, and coming straight back
  /// for more is exactly what turns a throttle into a ban.
  void _armCooldown([Duration length = const Duration(minutes: 30)]) {
    final until = DateTime.now().add(length);
    _cooldownUntil = until;
    // `set` is a FutureOr, and this is called from a synchronous throw path.
    unawaited(
      Future<void>.sync(
        () => prefs.set(
          optionPluginThreadsDirectCooldownUntil,
          until.toIso8601String(),
        ),
      ),
    );
  }

  Future<http.Response> _get(
    Uri uri,
    Map<String, String> headers, {
    bool respectCooldown = true,
  }) {
    return _enqueue(() async {
      try {
        return await httpClient.get(uri, headers: headers).timeout(_timeout);
      } catch (e) {
        throw ThreadsException(ThreadsErrorKind.unreachable, '$uri: $e');
      }
    }, respectCooldown: respectCooldown);
  }

  Future<http.Response> _post(
    Uri uri,
    Map<String, String> headers,
    String body, {
    bool respectCooldown = true,
  }) {
    return _enqueue(() async {
      try {
        return await httpClient
            .post(uri, headers: headers, body: body)
            .timeout(_timeout);
      } catch (e) {
        throw ThreadsException(ThreadsErrorKind.unreachable, '$uri: $e');
      }
    }, respectCooldown: respectCooldown);
  }

  void _throwForStatus(http.Response response, Uri uri) {
    final body = utf8.decode(response.bodyBytes);
    final loginRequired =
        body.contains('login_required') || body.contains('logout_reason');
    if (response.statusCode == 429 ||
        body.contains('Please wait a few minutes')) {
      _armCooldown();
      throw ThreadsException(ThreadsErrorKind.throttled, '$uri: rate limited');
    }
    if (response.statusCode == 401 ||
        response.statusCode == 403 ||
        loginRequired) {
      if (loginRequired) _armCooldown();
      throw ThreadsException(
        loginRequired
            ? ThreadsErrorKind.sessionSuspended
            : ThreadsErrorKind.unauthorized,
        '$uri: ${response.statusCode}',
      );
    }
    if (response.statusCode == 404) {
      throw ThreadsException(ThreadsErrorKind.noSuchFeed, '$uri: 404');
    }
    if (response.statusCode != 200) {
      throw ThreadsException(
        ThreadsErrorKind.unreachable,
        '$uri: ${response.statusCode}',
      );
    }
  }

  Object? _decodeJson(http.Response response, Uri uri) {
    try {
      return jsonDecode(utf8.decode(response.bodyBytes));
    } catch (e) {
      throw ThreadsException(ThreadsErrorKind.unreachable, '$uri: $e');
    }
  }

  Map<String, String> _cookieHeaders() {
    final c = cookies;
    final cookieHeader = _requiredCookieKeys
        .map((k) => '$k=${c[k]}')
        .join('; ');
    return {
      'User-Agent': _safariUa,
      'Accept': 'application/json, text/plain, */*',
      'Accept-Language': 'en-US,en;q=0.9',
      'X-IG-App-ID': _igAppId,
      // Typed, not asserted: `cookies` re-reads prefs on every access, so the
      // reader clearing the pasted header mid-flight used to turn this into a
      // raw null-check crash that no ThreadsException handler caught.
      'X-CSRFToken':
          c['csrftoken'] ??
          (throw ThreadsException(
            ThreadsErrorKind.unauthorized,
            'session cleared',
          )),
      'X-ASBD-ID': '129477',
      'X-IG-WWW-Claim': '0',
      'Referer': '$_threadsWeb/',
      'Cookie': cookieHeader,
    };
  }

  Future<Map<String, String>> _bearerHeaders() async => {
    'User-Agent': _barcelonaUa,
    'Authorization': 'Bearer ${bearer!}',
    'Accept': '*/*',
    'Accept-Language': 'en-US,en;q=0.9',
    'X-IG-App-ID': _igAppId,
    'X-IG-Capabilities': '3brTvx0=',
    'X-IG-Connection-Type': 'WIFI',
    'X-IG-Device-ID': await _deviceId(),
  };

  /// Confirms cookies via current_user and/or Bearer via a tiny timeline fetch.
  Future<String> verify() async {
    if (!hasDirectAuth) {
      throw ThreadsException(
        ThreadsErrorKind.notConfigured,
        'no direct session',
      );
    }
    if (hasCookies) {
      final me = await currentUser();
      return me.username;
    }
    final posts = await fetchFollowingTimeline(limit: 1);
    return posts.isEmpty ? 'ok' : posts.first.handle;
  }

  Future<ThreadsProfile> currentUser() async {
    _requireCookies();
    final uri = Uri.parse(
      '$_threadsWeb/api/v1/accounts/current_user/',
    ).replace(queryParameters: {'edit': 'true'});
    final response = await _get(uri, _cookieHeaders());
    _throwForStatus(response, uri);
    final user = Json(_decodeJson(response, uri))['user'];
    final profile = threadsProfileFromUserJson(user);
    if (profile == null) {
      throw ThreadsException(
        ThreadsErrorKind.unreachable,
        'current_user missing user',
      );
    }
    return profile;
  }

  /// The numeric id behind [handle], remembered once it is known.
  ///
  /// Resolving it costs a call to Meta's *search* endpoint, so asking the same
  /// question about the same followed accounts on every refresh both doubles
  /// what a read costs and looks precisely like a script. An account's id does
  /// not change, so it is worth keeping.
  Future<String> resolveUserId(String handle) async {
    final key = handle.toLowerCase();
    final known = _storedUserIds();
    if (known[key] case final id? when id.isNotEmpty) {
      return id;
    }

    _requireCookies();
    final id = await _searchUserId(key);
    await _rememberUserId(key, id);
    return id;
  }

  Map<String, String> _storedUserIds() {
    try {
      final decoded = jsonDecode(
        prefs.get<String>(optionPluginThreadsUserIds) ?? '{}',
      );
      return decoded is Map
          ? {for (final e in decoded.entries) '${e.key}': '${e.value}'}
          : {};
    } catch (_) {
      return {};
    }
  }

  Future<void> _rememberUserId(String handle, String id) async {
    final key = handle.toLowerCase();
    if (key.isEmpty || id.isEmpty) return;
    final known = _storedUserIds();
    if (known[key] == id) return;
    await prefs.set(
      optionPluginThreadsUserIds,
      jsonEncode({...known, key: id}),
    );
  }

  Future<String> _searchUserId(String handle) async {
    final users = await searchUsers(handle);
    for (final user in users) {
      if (user.username.toLowerCase() == handle.toLowerCase()) {
        if (user.pk.isNotEmpty) return user.pk;
        if (user.id.isNotEmpty) return user.id;
      }
    }
    throw ThreadsException(
      ThreadsErrorKind.noSuchFeed,
      'user not found: $handle',
    );
  }

  /// Multi-result people search — Meta's cookie `users/search` endpoint.
  ///
  /// Guest sessions have no public search; callers should fall back to an
  /// exact `@handle` profile open when [hasCookies] is false.
  Future<List<ThreadsProfile>> searchUsers(
    String query, {
    int count = 10,
  }) async {
    _requireCookies();
    final q = query.trim().replaceFirst(RegExp(r'^@'), '');
    if (q.isEmpty) return const [];

    final uri = Uri.parse(
      '$_threadsWeb/api/v1/users/search/',
    ).replace(queryParameters: {'q': q, 'count': '$count'});
    final response = await _get(uri, _cookieHeaders());
    _throwForStatus(response, uri);
    final users = <ThreadsProfile>[];
    for (final user in Json(_decodeJson(response, uri))['users'].list) {
      final profile = threadsProfileFromUserJson(user);
      if (profile != null) {
        users.add(profile);
        if (profile.pk.isNotEmpty) {
          await _rememberUserId(profile.username.toLowerCase(), profile.pk);
        }
      }
    }
    return users;
  }

  Future<ThreadsProfile> fetchProfile(String handle) async {
    try {
      return await fetchGuestProfile(handle);
    } on ThreadsException {
      if (!useSessionApis || !hasCookies) rethrow;
    }

    final id = await resolveUserId(handle);
    final uri = Uri.parse('$_threadsWeb/api/v1/users/$id/info/');
    final response = await _get(uri, _cookieHeaders());
    _throwForStatus(response, uri);
    final profile = threadsProfileFromUserJson(
      Json(_decodeJson(response, uri))['user'],
    );
    if (profile != null) {
      return profile;
    }
    throw ThreadsException(
      ThreadsErrorKind.noSuchFeed,
      'profile missing: @$handle',
    );
  }

  /// Profile card from the public `threads.com/@handle` page — no login.
  Future<ThreadsProfile> fetchGuestProfile(String handle) async {
    final key = handle.trim().toLowerCase();
    final htmlBody = await _fetchProfileHtml(key);
    final profile = threadsProfileFromGuestHtml(htmlBody, key);
    if (profile == null) {
      throw ThreadsException(
        ThreadsErrorKind.noSuchFeed,
        'guest profile missing: @$key',
      );
    }
    if (profile.pk.isNotEmpty) {
      await _rememberUserId(key, profile.pk);
    }
    return profile;
  }

  /// Per-account posts. Guest GraphQL is the default; cookie REST only when
  /// [useSessionApis] is on, and even then guest is the fallback so a dead
  /// session does not empty the tab.
  Future<List<ThreadsPost>> fetchUserThreads(
    String handle, {
    int count = threadsPostsPerAccount,
  }) async {
    if (useSessionApis && hasCookies) {
      try {
        final id = await resolveUserId(handle);
        final uri = Uri.parse(
          '$_threadsWeb/api/v1/text_feed/$id/profile/',
        ).replace(queryParameters: {'count': '$count'});
        final response = await _get(uri, _cookieHeaders());
        _throwForStatus(response, uri);
        final posts = parseThreadsApiFeed(_decodeJson(response, uri));
        if (posts.isNotEmpty) {
          return posts;
        }
      } on ThreadsException {
        // Guest path below.
      }
    }
    return fetchGuestAccount(handle);
  }

  Future<List<ThreadsPost>> fetchFollowingTimeline({int limit = 40}) async {
    if (!hasBearer) {
      throw ThreadsException(ThreadsErrorKind.notConfigured, 'no bearer');
    }
    // Params aligned with threads-go HomeTimeline — the old
    // `pagination_source=text_post_feed_following` alone now 404s as HTML.
    final deviceId = await _deviceId();
    final uri = Uri.parse('$_instagramApi/api/v1/feed/text_post_app_timeline/')
        .replace(
          queryParameters: {
            'feed_type': 'for_you',
            'feed_view_info': '[]',
            'reason': 'cold_start_fetch',
            'client_session_id': deviceId,
            'pagination_source_module': 'feed_unit',
          },
        );
    final response = await _get(uri, await _bearerHeaders());
    _throwForStatus(response, uri);
    final posts = parseThreadsApiFeed(_decodeJson(response, uri));
    return posts.take(limit).toList(growable: false);
  }

  /// Public posts for [handle] without a session.
  ///
  /// Prefers guest GraphQL (`BarcelonaProfileThreadsTabQuery`) — SSR often
  /// embeds zero `thread_items` for many profiles. HTML is still fetched once
  /// for the LSD token + user id, and used as a fallback scrape.
  Future<List<ThreadsPost>> fetchGuestAccount(String handle) async {
    final key = handle.trim().toLowerCase();

    // A known id plus a fresh LSD skips the profile page — one paced request
    // instead of two. Anything wrong with the shortcut falls through to the
    // full path below, which fetches the page and tries again properly.
    final knownId = _storedUserIds()[key];
    if (knownId != null && knownId.isNotEmpty) {
      if (_freshGuestLsd case final lsd?) {
        try {
          final posts = await _fetchGuestGraphqlThreads(
            handle: key,
            userId: knownId,
            lsd: lsd,
          );
          if (posts.isNotEmpty) {
            return posts;
          }
        } on ThreadsException {
          // The token may have aged out server-side; the full path refreshes it.
        }
      }
    }

    final htmlBody = await _fetchProfileHtml(key);
    final lsd = extractThreadsLsd(htmlBody);
    _rememberGuestLsd(lsd);
    final userId = (knownId != null && knownId.isNotEmpty)
        ? knownId
        : extractThreadsUserIdFromHtml(htmlBody, key);

    if (lsd != null && userId != null && userId.isNotEmpty) {
      await _rememberUserId(key, userId);
      try {
        final posts = await _fetchGuestGraphqlThreads(
          handle: key,
          userId: userId,
          lsd: lsd,
        );
        if (posts.isNotEmpty) {
          return posts;
        }
      } on ThreadsException {
        // Fall through to SSR — doc_id rotation / transient GraphQL failures.
      }
    }

    final posts = parseThreadsSsrHtml(htmlBody, key);
    if (posts.isEmpty) {
      throw ThreadsException(
        ThreadsErrorKind.noSuchFeed,
        'no posts for @$key (GraphQL+SSR empty)',
      );
    }
    return posts;
  }

  /// Public conversation for a Threads post URL (guest HTML scrape).
  ///
  /// Returns root + replies when the page embeds them. Empty when Meta sent
  /// nothing parseable — the caller still has the seed card from the feed.
  Future<List<ThreadsPost>> fetchGuestPostThread(String postUrl) async =>
      (await fetchGuestPostChains(postUrl)).expand((chain) => chain).toList(growable: false);

  /// The post page's chains — the conversation leading to the post, then each
  /// reply thread — as [threadsSsrChains] reads them.
  ///
  /// Remembered for [threadsConversationTtl] and shared while in flight:
  /// going back and opening the same post again, or a link to a post the feed
  /// already opened, should not ask Meta twice. [force] is the reader pulling
  /// to refresh, the one time it is worth asking again.
  Future<List<List<ThreadsPost>>> fetchGuestPostChains(String postUrl, {bool force = false}) {
    final uri = canonicalThreadsPostUri(postUrl);
    if (uri == null) {
      return Future.error(ThreadsException(ThreadsErrorKind.unreachable, 'not a threads url: $postUrl'));
    }
    final key = threadsShortcodeOf(uri.toString()) ?? uri.toString();
    final cached = _conversations[key];
    if (!force && cached != null && DateTime.now().difference(cached.at) < threadsConversationTtl) {
      return Future.value(cached.chains);
    }
    return _conversationsInFlight.putIfAbsent(key, () async {
      try {
        final chains = threadsSsrChains(await _guestHtml(uri));
        _rememberConversation(key, chains);
        return chains;
      } finally {
        _conversationsInFlight.remove(key);
      }
    });
  }

  final Map<String, ({DateTime at, List<List<ThreadsPost>> chains})> _conversations = {};
  final Map<String, Future<List<List<ThreadsPost>>>> _conversationsInFlight = {};

  void _rememberConversation(String key, List<List<ThreadsPost>> chains) {
    if (chains.isEmpty) {
      return;
    }
    _conversations.remove(key);
    _conversations[key] = (at: DateTime.now(), chains: chains);
    while (_conversations.length > threadsConversationCacheSize) {
      _conversations.remove(_conversations.keys.first);
    }
  }

  /// [handle]'s replies, from the public `/@handle/replies` page.
  ///
  /// Asked for only when the reader opens the Replies tab — never on a feed
  /// refresh — and empty when Meta did not embed them for a guest.
  Future<List<ThreadsPost>> fetchGuestReplies(String handle) async {
    final key = handle.trim().toLowerCase();
    final body = await _guestHtml(Uri.parse('$_threadsWeb/@$key/replies'));
    return parseThreadsSsrReplies(body, key);
  }

  Future<String> _fetchProfileHtml(String handle) {
    final key = handle.trim().toLowerCase();
    return _profileHtmlInFlight.putIfAbsent(key, () async {
      try {
        return await _guestHtml(Uri.parse('$_threadsWeb/@$key'));
      } finally {
        _profileHtmlInFlight.remove(key);
      }
    });
  }

  /// A public page as a logged-out browser sees it. Guest pages ignore the
  /// session cooldown: they spend no session.
  Future<String> _guestHtml(Uri uri) async {
    final response = await _get(uri, {
      'User-Agent': _safariUa,
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'en-US,en;q=0.9',
    }, respectCooldown: false);
    if (response.statusCode == 404) {
      throw ThreadsException(ThreadsErrorKind.noSuchFeed, '$uri: 404');
    }
    if (response.statusCode == 429) {
      throw ThreadsException(ThreadsErrorKind.throttled, '$uri: 429');
    }
    if (response.statusCode != 200) {
      throw ThreadsException(ThreadsErrorKind.unreachable, '$uri: ${response.statusCode}');
    }
    return utf8.decode(response.bodyBytes, allowMalformed: true);
  }

  Future<List<ThreadsPost>> _fetchGuestGraphqlThreads({
    required String handle,
    required String userId,
    required String lsd,
  }) async {
    final uri = Uri.parse('$_threadsWeb/api/graphql');
    final body = {
      'lsd': lsd,
      'doc_id': threadsGuestProfileThreadsDocId,
      'variables': jsonEncode({'userID': userId}),
    };
    final response = await _post(
      uri,
      {
        'User-Agent': _safariUa,
        'Content-Type': 'application/x-www-form-urlencoded',
        'Accept': '*/*',
        'Accept-Language': 'en-US,en;q=0.9',
        'X-IG-App-ID': _igAppId,
        'X-ASBD-ID': '129477',
        'X-FB-LSD': lsd,
        'X-FB-Friendly-Name': 'BarcelonaProfileThreadsTabQuery',
        'Origin': _threadsWeb,
        'Referer': '$_threadsWeb/@$handle',
      },
      body.entries
          .map(
            (e) =>
                '${Uri.encodeQueryComponent(e.key)}=${Uri.encodeQueryComponent(e.value)}',
          )
          .join('&'),
      respectCooldown: false,
    );

    if (response.statusCode == 429) {
      throw ThreadsException(ThreadsErrorKind.throttled, '$uri: 429');
    }
    if (response.statusCode != 200) {
      throw ThreadsException(
        ThreadsErrorKind.unreachable,
        '$uri: ${response.statusCode}',
      );
    }

    final decoded = _decodeJson(response, uri);
    final text = utf8.decode(response.bodyBytes);
    // Guest GraphQL sometimes returns the HTML shell when headers are wrong.
    if (text.trimLeft().startsWith('<!') || text.contains('<html')) {
      throw ThreadsException(
        ThreadsErrorKind.unreachable,
        '$uri: HTML instead of JSON',
      );
    }
    return parseThreadsGraphqlFeed(decoded);
  }

  void _requireCookies() {
    if (!hasCookies) {
      throw ThreadsException(
        ThreadsErrorKind.notConfigured,
        'incomplete cookies',
      );
    }
  }
}

String _randomDeviceId() {
  final r = Random.secure();
  String hex(int n) => List.generate(
    n,
    (_) => r.nextInt(256).toRadixString(16).padLeft(2, '0'),
  ).join();
  return '${hex(4)}-${hex(2)}-${hex(2)}-${hex(2)}-${hex(6)}';
}

/// A post link as `www.threads.com` serves it: `threads.net`, `m.` hosts and
/// share-sheet query strings each cost a redirect, or a cache miss, otherwise.
Uri? canonicalThreadsPostUri(String postUrl) {
  final uri = Uri.tryParse(postUrl.trim());
  if (uri == null || !uri.host.contains('threads.') || uri.pathSegments.isEmpty) {
    return null;
  }
  return Uri.parse('$_threadsWeb${uri.path}');
}
