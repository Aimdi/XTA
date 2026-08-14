import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:html/parser.dart' as html_parser;
import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/utils/json.dart';

const _threadsWeb = 'https://www.threads.com';
const _instagramApi = 'https://i.instagram.com';
const _igAppId = '238260118697367';
const _barcelonaUa = 'Barcelona 289.0.0.77.109 Android';
const _safariUa =
    'Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1';

/// Guest profile threads — `BarcelonaProfileThreadsTabQuery`. Rotates; if it
/// 404s or returns empty, [fetchGuestAccount] falls back to SSR `thread_items`.
const threadsGuestProfileThreadsDocId = '6232751443445612';

final _lsdTokenPattern = RegExp(r'"LSD",\[\],\{"token":"([^"]+)"\}');

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

/// Pure parsers for Meta JSON / SSR — kept free of I/O for unit tests.
List<ThreadsPost> parseThreadsApiFeed(Object? json) {
  final root = Json(json);
  final buckets = root['threads'].list.isNotEmpty
      ? root['threads'].list
      : root['items'].list;
  final posts = <ThreadsPost>[];
  for (final bucket in buckets) {
    final items = bucket['thread_items'].list;
    final postJson = items.isNotEmpty ? items.first['post'] : bucket['post'];
    final post = threadsPostFromApi(postJson);
    if (post != null) posts.add(post);
  }
  return posts;
}

/// Guest GraphQL profile tab: `data.mediaData.threads` → same post shape as REST.
List<ThreadsPost> parseThreadsGraphqlFeed(Object? json) {
  final root = Json(json);
  final mediaThreads = root['data']['mediaData']['threads'].list;
  if (mediaThreads.isEmpty) {
    return parseThreadsApiFeed(json);
  }
  return parseThreadsApiFeed({
    'threads': [for (final thread in mediaThreads) thread.raw],
  });
}

/// LSD token embedded in Threads HTML for guest GraphQL.
String? extractThreadsLsd(String html) =>
    _lsdTokenPattern.firstMatch(html)?.group(1);

/// Numeric Threads user id for [handle] from a profile page HTML blob.
String? extractThreadsUserIdFromHtml(String html, String handle) {
  final key = handle.trim().toLowerCase();
  if (key.isEmpty) return null;

  // Logged-out profile pages currently embed the owner on
  // BarcelonaProfileThreadsRoot as `props.user_id` — before any pk/username
  // blob. Without this, guest GraphQL never starts.
  final propsId = RegExp(r'"user_id"\s*:\s*"(\d+)"').firstMatch(html)?.group(1);
  if (propsId != null && propsId != '0') {
    return propsId;
  }

  final escaped = RegExp.escape(key);
  final nearUsername = RegExp(
    '"username"\\s*:\\s*"$escaped".{0,480}?"pk"\\s*:\\s*"(\\d+)"',
    caseSensitive: false,
    dotAll: true,
  ).firstMatch(html);
  if (nearUsername != null) return nearUsername.group(1);

  final nearPk = RegExp(
    '"pk"\\s*:\\s*"(\\d+)".{0,480}?"username"\\s*:\\s*"$escaped"',
    caseSensitive: false,
    dotAll: true,
  ).firstMatch(html);
  if (nearPk != null) return nearPk.group(1);

  // Modal `userID` — ignore the logged-out stub `0`.
  final userIds = RegExp(
    r'"userID"\s*:\s*"(\d+)"',
  ).allMatches(html).map((m) => m.group(1)!).where((id) => id != '0').toList();
  if (userIds.isEmpty) return null;
  final counts = <String, int>{};
  for (final id in userIds) {
    counts[id] = (counts[id] ?? 0) + 1;
  }
  final ranked = counts.entries.toList()
    ..sort((a, b) => b.value.compareTo(a.value));
  return ranked.first.key;
}

String? _metaContent(String html, String name) {
  final property = RegExp(
    '<meta[^>]+(?:property|name)="$name"[^>]+content="([^"]*)"',
    caseSensitive: false,
  ).firstMatch(html)?.group(1);
  if (property != null) {
    return property;
  }
  return RegExp(
    '<meta[^>]+content="([^"]*)"[^>]+(?:property|name)="$name"',
    caseSensitive: false,
  ).firstMatch(html)?.group(1);
}

String _decodeHtmlEntities(String value) =>
    html_parser.parseFragment(value).text ?? value;

/// `5.7M` / `1.4K` / `380` → an int the profile card can show.
int? parseThreadsCompactCount(String? raw) {
  if (raw == null) {
    return null;
  }
  final match = RegExp(
    r'^([\d.,]+)\s*([KMB])?$',
    caseSensitive: false,
  ).firstMatch(raw.trim());
  if (match == null) {
    return null;
  }
  final number = double.tryParse(match.group(1)!.replaceAll(',', ''));
  if (number == null) {
    return null;
  }
  final scale = switch ((match.group(2) ?? '').toUpperCase()) {
    'K' => 1000,
    'M' => 1000000,
    'B' => 1000000000,
    _ => 1,
  };
  return (number * scale).round();
}

/// Public profile card from OG / meta tags on `threads.com/@handle` (no login).
ThreadsProfile? threadsProfileFromGuestHtml(String html, String handle) {
  final key = (normaliseThreadsHandle(handle) ?? handle).trim().toLowerCase();
  if (key.isEmpty) {
    return null;
  }

  final titleRaw =
      _metaContent(html, 'og:title') ?? _metaContent(html, 'twitter:title');
  final descRaw =
      _metaContent(html, 'og:description') ?? _metaContent(html, 'description');
  final imageRaw =
      _metaContent(html, 'og:image') ?? _metaContent(html, 'twitter:image');
  if (titleRaw == null && descRaw == null && imageRaw == null) {
    return null;
  }

  final title = titleRaw == null ? '' : _decodeHtmlEntities(titleRaw);
  final desc = descRaw == null ? '' : _decodeHtmlEntities(descRaw);
  final image = imageRaw == null
      ? ''
      : _decodeHtmlEntities(imageRaw).replaceAll('&amp;', '&');

  final nameMatch = RegExp(
    r'^(.*?)\s*\(@',
    caseSensitive: false,
  ).firstMatch(title);
  final displayName = (nameMatch?.group(1)?.trim().isNotEmpty ?? false)
      ? nameMatch!.group(1)!.trim()
      : key;

  var followers = 0;
  var mediaCount = 0;
  var biography = '';
  final parts = desc.split(RegExp(r'\s*[•·]\s*'));
  for (final part in parts) {
    final followersMatch = RegExp(
      r'^([\d.,]+[KMB]?)\s+Followers?$',
      caseSensitive: false,
    ).firstMatch(part.trim());
    if (followersMatch != null) {
      followers =
          parseThreadsCompactCount(followersMatch.group(1)) ?? followers;
      continue;
    }
    final threadsMatch = RegExp(
      r'^([\d.,]+[KMB]?)\s+Threads?$',
      caseSensitive: false,
    ).firstMatch(part.trim());
    if (threadsMatch != null) {
      mediaCount =
          parseThreadsCompactCount(threadsMatch.group(1)) ?? mediaCount;
      continue;
    }
    if (part.trim().isNotEmpty && biography.isEmpty) {
      biography = part.trim();
    }
  }

  final pk = extractThreadsUserIdFromHtml(html, key) ?? '';
  return ThreadsProfile(
    pk: pk,
    id: pk,
    username: key,
    fullName: displayName,
    isVerified: false,
    isPrivate: false,
    profilePicUrl: image,
    biography: biography,
    followerCount: followers,
    followingCount: 0,
    mediaCount: mediaCount,
    externalUrl: '$_threadsWeb/@$key',
  );
}

/// One Meta post object → [ThreadsPost], including pure reposts.
///
/// A repost often has an empty outer caption; the original lives under
/// `text_post_app_info.share_info.reposted_post`. Skipping those empty shells
/// is why followed accounts' reposts never showed up.
ThreadsPost? threadsPostFromApi(Json post) {
  if (!post.exists) return null;

  final reposted = post['text_post_app_info']['share_info']['reposted_post'];
  if (reposted.exists) {
    return _threadsRepostFromApi(outer: post, inner: reposted);
  }
  return _threadsOriginalFromApi(post);
}

ThreadsPost? _threadsRepostFromApi({required Json outer, required Json inner}) {
  final original = _threadsOriginalFromApi(inner);
  if (original == null) {
    return null;
  }

  final user = outer['user'];
  final reposter = (user['username'].string ?? '').trim().toLowerCase();
  if (reposter.isEmpty) {
    return null;
  }
  final reposterName = (user['full_name'].string ?? '').trim();
  final outerPk = outer['pk'].string ?? outer['id'].string ?? original.id;
  final taken = outer['taken_at'].integer;

  return ThreadsPost(
    id: outerPk,
    handle: original.handle,
    authorName: original.authorName,
    avatarUrl: original.avatarUrl,
    text: original.text,
    images: original.images,
    imageAspects: original.imageAspects,
    publishedAt: taken == null
        ? original.publishedAt
        : DateTime.fromMillisecondsSinceEpoch(
            taken * 1000,
            isUtc: true,
          ).toLocal(),
    url: original.url,
    likeCount: original.likeCount,
    replyCount: original.replyCount,
    repostCount: original.repostCount,
    linkCard: original.linkCard,
    repostedByHandle: reposter,
    repostedByName: reposterName.isEmpty ? reposter : reposterName,
    isVerified: original.isVerified,
    replyToHandle: original.replyToHandle,
    isReply: original.isReply,
  );
}

ThreadsPost? _threadsOriginalFromApi(Json post) {
  if (!post.exists) return null;
  final user = post['user'];
  final handle = (user['username'].string ?? '').trim().toLowerCase();
  final text = (post['caption']['text'].string ?? '').trim();
  final media = _threadsMediaOf(post);
  final images = [for (final item in media) item.url];
  final linkCard = threadsLinkCardOf(post);
  final replyTo = _threadsReplyToHandleOf(post);
  if (handle.isEmpty || (text.isEmpty && images.isEmpty && linkCard == null)) {
    return null;
  }

  final code = post['code'].string;
  final pk = post['pk'].string ?? post['id'].string ?? code;
  if (pk == null || pk.isEmpty) return null;

  final tpi = post['text_post_app_info'];
  final taken = post['taken_at'].integer;
  return ThreadsPost(
    id: pk,
    handle: handle,
    authorName: (user['full_name'].string ?? '').trim().isEmpty
        ? handle
        : user['full_name'].string!.trim(),
    avatarUrl:
        user['profile_pic_url'].string ??
        user['hd_profile_pic_url_info']['url'].string,
    text: text,
    images: images,
    imageAspects: [for (final item in media) item.aspectRatio],
    publishedAt: taken == null
        ? null
        : DateTime.fromMillisecondsSinceEpoch(
            taken * 1000,
            isUtc: true,
          ).toLocal(),
    url: code == null ? null : '$_threadsWeb/@$handle/post/$code',
    likeCount: post['like_count'].integer,
    replyCount: tpi['direct_reply_count'].integer,
    repostCount: tpi['repost_count'].integer,
    linkCard: linkCard,
    isVerified: user['is_verified'].boolean ?? false,
    replyToHandle: replyTo,
    isReply: replyTo != null || _threadsIsReply(post),
  );
}

String? _threadsReplyToHandleOf(Json post) {
  final handle = post['text_post_app_info']['reply_to_author']['username']
      .string
      ?.trim();
  if (handle == null || handle.isEmpty) {
    return null;
  }
  return handle.toLowerCase();
}

bool _threadsIsReply(Json post) {
  final tpi = post['text_post_app_info'];
  return tpi['is_reply'].boolean == true || tpi['reply_to_author'].exists;
}

({String? url, double? aspect}) _bestCandidate(Json versions) {
  String? best;
  var bestArea = -1;
  double? aspect;
  for (final candidate in versions['candidates'].list) {
    final url = candidate['url'].string;
    if (url == null || url.isEmpty) {
      continue;
    }
    final w = candidate['width'].integer ?? 0;
    final h = candidate['height'].integer ?? 0;
    final area = w * h;
    if (area >= bestArea) {
      bestArea = area;
      best = url;
      if (w > 0 && h > 0) {
        aspect = w / h;
      }
    }
  }
  return (url: best, aspect: aspect);
}

List<PluginMediaItem> _threadsMediaOf(Json post) {
  final items = <PluginMediaItem>[];
  final seen = <String>{};

  void add(Json media) {
    final picked = _bestCandidate(media['image_versions2']);
    final url = picked.url;
    if (url == null || url.isEmpty || seen.contains(url)) {
      return;
    }
    seen.add(url);
    final fallback = pluginMediaAspectFrom({
      'width':
          media['original_width'].integer ?? post['original_width'].integer,
      'height':
          media['original_height'].integer ?? post['original_height'].integer,
    });
    items.add(
      PluginMediaItem(url: url, aspectRatio: picked.aspect ?? fallback),
    );
  }

  if (post['carousel_media'].list.isNotEmpty) {
    for (final media in post['carousel_media'].list) {
      add(media);
    }
    return items;
  }
  add(post);
  return items;
}

ThreadsProfile? threadsProfileFromUserJson(Json user) {
  if (!user.exists) return null;
  final username = (user['username'].string ?? '').trim();
  if (username.isEmpty) return null;
  final pk =
      user['pk'].string ?? user['id'].string ?? user['pk_id'].string ?? '';
  final url = user['external_url'].string?.trim();
  return ThreadsProfile(
    pk: pk,
    id: user['id'].string ?? pk,
    username: username,
    fullName: user['full_name'].string ?? '',
    isVerified: user['is_verified'].boolean ?? false,
    isPrivate: user['is_private'].boolean ?? false,
    profilePicUrl:
        user['profile_pic_url'].string ??
        user['hd_profile_pic_url_info']['url'].string ??
        '',
    biography: user['biography'].string ?? '',
    followerCount: user['follower_count'].integer ?? 0,
    followingCount: user['following_count'].integer ?? 0,
    mediaCount: user['media_count'].integer ?? 0,
    externalUrl: url == null || url.isEmpty ? null : url,
  );
}

/// Walks decoded `data-sjs` blobs for `thread_items` posts.
///
/// Profile pages: one card per thread (the root item). Post pages should use
/// [parseThreadsSsrThread], which keeps every reply in the chain.
List<ThreadsPost> parseThreadsSsrHtml(String body, String handle) {
  final document = html_parser.parse(body);
  final posts = <ThreadsPost>[];
  final seen = <String>{};

  for (final script in document.querySelectorAll('script[data-sjs]')) {
    final text = script.text.trim();
    if (text.isEmpty || !text.contains('thread_items')) continue;
    Object? decoded;
    try {
      decoded = jsonDecode(text);
    } catch (_) {
      continue;
    }
    _collectSsrPosts(decoded, handle, posts, seen, rootsOnly: true);
  }
  return posts;
}

/// Every post embedded in a Threads post page (root + replies).
List<ThreadsPost> parseThreadsSsrThread(String body) {
  final document = html_parser.parse(body);
  final posts = <ThreadsPost>[];
  final seen = <String>{};

  for (final script in document.querySelectorAll('script[data-sjs]')) {
    final text = script.text.trim();
    if (text.isEmpty || !text.contains('thread_items')) continue;
    Object? decoded;
    try {
      decoded = jsonDecode(text);
    } catch (_) {
      continue;
    }
    _collectSsrPosts(decoded, '', posts, seen, rootsOnly: false);
  }
  return posts;
}

void _collectSsrPosts(
  Object? node,
  String handle,
  List<ThreadsPost> out,
  Set<String> seen, {
  required bool rootsOnly,
}) {
  if (node is Map) {
    final items = node['thread_items'];
    if (items is List && items.isNotEmpty) {
      final slice = rootsOnly ? items.take(1) : items;
      for (final item in slice) {
        final post = threadsPostFromApi(
          Json(item is Map ? item['post'] : null),
        );
        // Reposts keep the original author on [ThreadsPost.handle]; the profile
        // owner is [repostedByHandle]. Match either so SSR profile scrapes keep them.
        final matches =
            handle.isEmpty ||
            post?.handle == handle ||
            post?.repostedByHandle == handle;
        if (post != null && matches && seen.add(post.id)) {
          out.add(post);
        }
      }
    }
    for (final value in node.values) {
      _collectSsrPosts(value, handle, out, seen, rootsOnly: rootsOnly);
    }
  } else if (node is List) {
    for (final value in node) {
      _collectSsrPosts(value, handle, out, seen, rootsOnly: rootsOnly);
    }
  }
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
  Future<List<ThreadsPost>> fetchGuestPostThread(String postUrl) async {
    final uri = Uri.tryParse(postUrl.trim());
    if (uri == null || !uri.host.contains('threads.')) {
      throw ThreadsException(
        ThreadsErrorKind.unreachable,
        'not a threads url: $postUrl',
      );
    }

    final response = await _get(uri, {
      'User-Agent': _safariUa,
      'Accept':
          'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'en-US,en;q=0.9',
    }, respectCooldown: false);
    if (response.statusCode == 404) {
      throw ThreadsException(ThreadsErrorKind.noSuchFeed, '$uri: 404');
    }
    if (response.statusCode == 429) {
      throw ThreadsException(ThreadsErrorKind.throttled, '$uri: 429');
    }
    if (response.statusCode != 200) {
      throw ThreadsException(
        ThreadsErrorKind.unreachable,
        '$uri: ${response.statusCode}',
      );
    }

    return parseThreadsSsrThread(utf8.decode(response.bodyBytes));
  }

  Future<String> _fetchProfileHtml(String handle) {
    final key = handle.trim().toLowerCase();
    return _profileHtmlInFlight.putIfAbsent(key, () async {
      try {
        return await _downloadProfileHtml(key);
      } finally {
        _profileHtmlInFlight.remove(key);
      }
    });
  }

  Future<String> _downloadProfileHtml(String handle) async {
    final uri = Uri.parse('$_threadsWeb/@$handle');
    final response = await _get(uri, {
      'User-Agent': _safariUa,
      'Accept':
          'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'en-US,en;q=0.9',
    }, respectCooldown: false);
    if (response.statusCode == 404) {
      throw ThreadsException(ThreadsErrorKind.noSuchFeed, '$uri: 404');
    }
    if (response.statusCode == 429) {
      throw ThreadsException(ThreadsErrorKind.throttled, '$uri: 429');
    }
    if (response.statusCode != 200) {
      throw ThreadsException(
        ThreadsErrorKind.unreachable,
        '$uri: ${response.statusCode}',
      );
    }
    return utf8.decode(response.bodyBytes);
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
