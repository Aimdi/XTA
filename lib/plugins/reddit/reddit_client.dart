import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:quax/plugins/reddit/reddit_html.dart';
import 'package:quax/plugins/reddit/reddit_comments.dart';
import 'package:quax/plugins/reddit/reddit_media_urls.dart';
import 'package:quax/plugins/reddit/reddit_search_html.dart';

/// How a Reddit request failed, in terms the user can act on.
enum RedditErrorKind {
  /// No client id stored yet.
  notConfigured,

  /// Reddit rejected the client id (401).
  unauthorized,

  /// Reddit refused the request (403) — commonly its network-level blocking
  /// rather than anything wrong with the app.
  blocked,

  /// Subreddit does not exist, is private, or was banned (404).
  notFound,

  /// Too many requests (429).
  rateLimited,

  /// Answered, but not with the JSON the API documents.
  badResponse,

  /// Could not reach Reddit at all.
  network,
}

class RedditException implements Exception {
  final RedditErrorKind kind;
  final String detail;

  const RedditException(this.kind, this.detail);

  @override
  String toString() => 'RedditException($kind): $detail';
}

/// One post in a listing. Only what a reader needs; every field is parsed
/// defensively because Reddit adds and removes keys without notice.
class RedditPost {
  final String id;
  final String title;
  final String subreddit;
  final String? author;
  final int score;
  final int commentCount;
  final DateTime? createdAt;

  /// Path on reddit.com, e.g. `/r/dartlang/comments/abc123/title/`.
  final String permalink;

  /// What the post points at: the article for a link post, the permalink for a
  /// self post.
  final String? url;

  final bool isSelf;
  final String? selfText;
  final bool over18;
  final bool stickied;
  final String? thumbnail;

  /// The post's own label within its subreddit, e.g. `Elon Criticism`.
  final String? flair;

  /// Where the post points, as Reddit summarises it: `i.redd.it`, `v.redd.it`,
  /// `self.dartlang`, or an article's domain.
  final String? domain;

  const RedditPost({
    required this.id,
    required this.title,
    required this.subreddit,
    required this.permalink,
    this.author,
    this.score = 0,
    this.commentCount = 0,
    this.createdAt,
    this.url,
    this.isSelf = false,
    this.selfText,
    this.over18 = false,
    this.stickied = false,
    this.thumbnail,
    this.flair,
    this.domain,
  });

  /// A thumbnail worth showing; Reddit uses sentinels like `self` and `default`
  /// where there is no image.
  String? get thumbnailUrl {
    final value = thumbnail;
    if (value == null || !value.startsWith('http')) {
      return null;
    }
    return value;
  }

  /// The full-size picture to show edge to edge, or null when the post has none
  /// worth showing at that size.
  ///
  /// Deliberately not [thumbnailUrl]: Reddit's listing thumbnails are 70px
  /// wide, and stretching one across a phone gives a smear where a photo should
  /// be.
  String? get imageUrl => redditImageUrl(url);

  bool get isVideo => isRedditVideoHost(domain ?? (url == null ? null : Uri.tryParse(url!)?.host));

  static RedditPost? fromChild(Map<String, dynamic> child) {
    if (child['kind'] != 't3') {
      return null;
    }
    final data = child['data'];
    if (data is! Map) {
      return null;
    }
    final map = Map<String, dynamic>.from(data);

    final id = map['id'] as String?;
    final title = map['title'] as String?;
    if (id == null || id.isEmpty || title == null) {
      return null;
    }

    final created = map['created_utc'];
    return RedditPost(
      id: id,
      title: title,
      subreddit: map['subreddit'] as String? ?? '',
      permalink: map['permalink'] as String? ?? '/comments/$id',
      author: map['author'] as String?,
      score: (map['score'] as num?)?.toInt() ?? 0,
      commentCount: (map['num_comments'] as num?)?.toInt() ?? 0,
      createdAt: created is num
          ? DateTime.fromMillisecondsSinceEpoch((created * 1000).round(), isUtc: true).toLocal()
          : null,
      url: map['url'] as String?,
      isSelf: map['is_self'] == true,
      selfText: (map['selftext'] as String?)?.trim(),
      over18: map['over_18'] == true,
      stickied: map['stickied'] == true,
      thumbnail: map['thumbnail'] as String?,
      flair: (map['link_flair_text'] as String?)?.trim(),
      domain: map['domain'] as String?,
    );
  }
}

class RedditListing {
  final List<RedditPost> posts;

  /// Reddit's pagination cursor; null when there is no further page.
  final String? after;

  const RedditListing({required this.posts, this.after});
}

/// Sort orders a subreddit listing supports.
enum RedditSort { hot, newest, top, rising, controversial }

String redditSortPath(RedditSort sort) => switch (sort) {
      RedditSort.hot => 'hot',
      RedditSort.newest => 'new',
      RedditSort.top => 'top',
      RedditSort.rising => 'rising',
      RedditSort.controversial => 'controversial',
    };

/// The stored sort, or hot when nothing is stored or the name is unknown.
RedditSort redditSortFromName(String? name) =>
    RedditSort.values.firstWhere((e) => e.name == name, orElse: () => RedditSort.hot);

/// Read-only Reddit client. Nobody has to log in, and by default nobody has to
/// configure anything either.
///
/// Without a client id it reads the public `.json` endpoints, which take no
/// credentials at all. Reddit throttles those harder and sometimes refuses them
/// outright, so a client id is still worth setting: it switches the reader to
/// the `installed_client` grant, which authenticates the *app* — not a user —
/// with a device id that deliberately says "do not track", and gets the higher,
/// documented rate limits.
///
/// Requiring the client id up front was the wrong default: an unconfigured
/// reader failed every request rather than trying the endpoint that needs
/// nothing.
class RedditClient {
  final http.Client httpClient;

  RedditClient({http.Client? httpClient, DateTime Function()? clock})
      : httpClient = httpClient ?? http.Client(),
        _now = clock ?? DateTime.now;

  final DateTime Function() _now;

  static const _tokenEndpoint = 'https://www.reddit.com/api/v1/access_token';
  static const _apiBase = 'https://oauth.reddit.com';

  /// Serves the same listings as [_apiBase] without any credentials.
  static const _publicBase = 'https://www.reddit.com';

  /// Served and throttled separately from [_publicBase], so it is worth a
  /// second try when that one refuses.
  static const _publicFallbackBase = 'https://old.reddit.com';
  static const _timeout = Duration(seconds: 20);

  /// For [_apiBase] and the token endpoint. Reddit's API rules ask for exactly
  /// this shape and throttle generic agents harder.
  static const userAgent = 'android:com.teskann.quax:1.0 (read-only, account-free)';

  /// For [_publicBase] and [_publicFallbackBase], which are the *website*
  /// rather than the API.
  ///
  /// That side sits behind an edge that turns away anything announcing itself
  /// as a bot, and [userAgent] does exactly that — which is why the public
  /// route could refuse a reader who had done nothing wrong and had no client
  /// id to fall back on. A scraper has to look like a browser, which is what
  /// Stealth sends too.
  static const publicUserAgent =
      'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

  /// Reddit's documented value for "do not associate this with a device".
  static const deviceId = 'DO_NOT_TRACK_THIS_DEVICE';

  String? _token;
  DateTime? _tokenExpiry;

  /// Cookies the public hosts have set, kept for the life of the client.
  ///
  /// A client that never carries a cookie looks like a fresh stranger on every
  /// request, which is one of the cheapest bot tells there is. This also holds
  /// the `over18` consent once it has been given, so the gate is answered once
  /// rather than on every subreddit.
  final Map<String, String> _cookies = {};

  void _rememberCookies(http.Response response, Map<String, String>? sent) {
    if (sent != null) {
      _cookies.addAll(sent);
    }

    final header = response.headers['set-cookie'];
    if (header == null) {
      return;
    }

    // Several cookies arrive comma-joined; only the name=value head of each
    // matters, the attributes after the first `;` do not.
    for (final piece in header.split(RegExp(r',(?=[^;]+=)'))) {
      final pair = piece.split(';').first.trim();
      final equals = pair.indexOf('=');
      if (equals > 0) {
        _cookies[pair.substring(0, equals).trim()] = pair.substring(equals + 1).trim();
      }
    }
  }

  /// Whether a usable token is already cached.
  bool get hasToken => _token != null && (_tokenExpiry?.isAfter(_now()) ?? false);

  void forgetToken() {
    _token = null;
    _tokenExpiry = null;
  }

  /// Fetches (and caches) an app-only token.
  Future<String> _authorize(String clientId) async {
    if (hasToken) {
      return _token!;
    }
    if (clientId.trim().isEmpty) {
      throw const RedditException(RedditErrorKind.notConfigured, 'Missing client id');
    }

    final response = await _send(() => httpClient.post(
          Uri.parse(_tokenEndpoint),
          headers: {
            'Authorization': 'Basic ${base64Encode(utf8.encode('${clientId.trim()}:'))}',
            'User-Agent': userAgent,
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body: 'grant_type=https://oauth.reddit.com/grants/installed_client&device_id=$deviceId',
        ));

    if (response.statusCode != 200) {
      throw _errorFor(response);
    }

    final decoded = _decode(response);
    final token = decoded['access_token'] as String?;
    if (token == null || token.isEmpty) {
      throw const RedditException(RedditErrorKind.badResponse, 'No access_token in the token response');
    }

    final expiresIn = (decoded['expires_in'] as num?)?.toInt() ?? 3600;
    _token = token;
    // Expire a minute early so a request in flight cannot land on a dead token.
    _tokenExpiry = _now().add(Duration(seconds: expiresIn - 60));
    return token;
  }

  /// Posts from one subreddit.
  Future<RedditListing> fetchSubreddit(
    String subreddit, {
    required String clientId,
    RedditSort sort = RedditSort.hot,
    int limit = 25,
    String? after,
    String? userToken,
    bool preferPublic = false,
  }) async {
    final name = normaliseSubreddit(subreddit);
    if (name == null) {
      throw RedditException(RedditErrorKind.notFound, 'Not a subreddit name: $subreddit');
    }

    // A signed-in reader gets their own account's rate limits, which is the
    // most reliable route Reddit offers; otherwise app-only auth when a client
    // id is set, and failing that the public endpoints, which need nothing.
    // [preferPublic] overrides all of that: a reader who asked for the
    // account-free route gets it even when a credential is sitting there.
    final anonymous = preferPublic || (userToken == null && clientId.trim().isEmpty);
    final token = userToken ?? (anonymous ? null : await _authorize(clientId));

    final query = {
      'limit': '$limit',
      // Gives real characters instead of HTML entities in titles and text.
      'raw_json': '1',
      if (after != null && after.isNotEmpty) 'after': after,
    };

    if (!anonymous) {
      final uri = Uri.parse('$_apiBase/r/$name/${redditSortPath(sort)}').replace(queryParameters: query);
      return _listingFrom(_decode(await _read(uri, token)));
    }

    // The old site's HTML first, which is the route that still works without an
    // account: Reddit shut unauthenticated `.json` down, so asking for JSON now
    // gets refused however the request is dressed. Stealth scrapes this page
    // for the same reason.
    final scraped = await _scrapeListing(name, sort, query);
    if (scraped != null) {
      return scraped;
    }

    // JSON second, in case the deprecation is not yet total for this reader or
    // Reddit walks part of it back. www and old are served and throttled
    // separately, so both are worth a try.
    var response = await _read(Uri.parse(_publicJsonPath(_publicBase, name, sort)).replace(queryParameters: query));

    if (const [403, 429].contains(response.statusCode)) {
      response =
          await _read(Uri.parse(_publicJsonPath(_publicFallbackBase, name, sort)).replace(queryParameters: query));
    }

    if (response.statusCode != 200) {
      // Both public hosts refused. Previously reported as "add a client id",
      // but Reddit now rejects most new app registrations. Report the actual error
      // instead: refusals usually pass, and signing in is the real solution.
      if (response.statusCode == 403) {
        throw RedditException(RedditErrorKind.blocked, 'HTTP 403 from both public hosts');
      }
      if (response.statusCode == 429) {
        throw RedditException(RedditErrorKind.rateLimited, 'HTTP 429 from both public hosts');
      }
      throw _errorFor(response, Uri.parse(_publicBase));
    }

    return _listingFrom(_decode(response));
  }

  /// Reads a listing off old.reddit's HTML, or null if that page could not be
  /// used — the caller then falls back to JSON rather than giving up.
  ///
  /// The over-18 gate is answered with a cookie rather than a login: Reddit
  /// wants consent recorded, not an account, and `over18=1` is what its own
  /// form sets.
  Future<RedditListing?> _scrapeListing(String name, RedditSort sort, Map<String, String> query) async {
    final uri = Uri.parse('$_publicFallbackBase/r/$name/${redditSortPath(sort)}')
        .replace(queryParameters: {...query}..remove('raw_json'));

    late http.Response response;
    try {
      response = await _read(uri);
    } on RedditException {
      return null;
    }

    if (response.statusCode != 200) {
      return null;
    }

    if (isOver18Gate(response.body)) {
      // Consent is a cookie; ask again carrying it.
      try {
        response = await _read(uri, null, {'over18': '1'});
      } on RedditException {
        return null;
      }
      if (response.statusCode != 200 || isOver18Gate(response.body)) {
        return null;
      }
    }

    final listing = parseListing(response.body);
    // An empty page is indistinguishable from markup this parser no longer
    // understands, so it is treated as a failure and JSON gets its turn.
    if (listing.posts.isEmpty) {
      return null;
    }

    return RedditListing(posts: listing.posts, after: listing.after);
  }

  /// Posts matching [query], across Reddit or within one subreddit.
  Future<List<RedditPost>> searchPosts(String query, {String? subreddit, RedditSort sort = RedditSort.hot}) async {
    final name = subreddit == null ? null : normaliseSubreddit(subreddit);
    final path = name == null ? '/search' : '/r/$name/search';

    final body = await _scrape(Uri.parse('$_publicFallbackBase$path').replace(queryParameters: {
      'q': query,
      'sort': sort == RedditSort.newest ? 'new' : 'relevance',
      't': 'all',
      if (name != null) 'restrict_sr': 'on',
    }));

    return body == null ? const [] : parseSearchPosts(body);
  }

  Future<List<RedditSubredditResult>> searchSubreddits(String query) async {
    final body = await _scrape(
        Uri.parse('$_publicFallbackBase/subreddits/search').replace(queryParameters: {'q': query}));

    return body == null ? const [] : parseSubredditResults(body);
  }

  Future<List<RedditUserResult>> searchUsers(String query) async {
    final body = await _scrape(
        Uri.parse('$_publicFallbackBase/search').replace(queryParameters: {'q': query, 'type': 'user'}));

    return body == null ? const [] : parseUserResults(body);
  }

  /// One account's posts. Comments on the same page have no title and are
  /// skipped by the listing parser, which is the behaviour we want here.
  Future<RedditListing> fetchUserPosts(String user, {String? after}) async {
    final name = user.replaceFirst(RegExp(r'^/?u(?:ser)?/', caseSensitive: false), '').trim();
    if (name.isEmpty) {
      throw RedditException(RedditErrorKind.notFound, 'Not a username: $user');
    }

    final body = await _scrape(Uri.parse('$_publicFallbackBase/user/$name/submitted').replace(queryParameters: {
      'limit': '25',
      if (after != null && after.isNotEmpty) 'after': after,
    }));

    if (body == null) {
      throw RedditException(RedditErrorKind.notFound, 'No such account: $name');
    }

    final listing = parseListing(body);
    return RedditListing(posts: listing.posts, after: listing.after);
  }

  /// A subreddit's own picture, or null when it has none to find.
  ///
  /// Never throws: a timeline that failed to load because a picture could not
  /// be fetched would be a poor trade.
  Future<String?> fetchSubredditIcon(String subreddit) async {
    final name = normaliseSubreddit(subreddit);
    if (name == null) {
      return null;
    }

    try {
      final body = await _scrape(Uri.parse('$_publicFallbackBase/r/$name/'));
      return body == null ? null : parseSubredditIcon(body);
    } catch (_) {
      return null;
    }
  }

  /// One page of old.reddit, with the over-18 gate answered, or null when the
  /// page could not be read at all.
  Future<String?> _scrape(Uri uri) async {
    var response = await _read(uri);

    if (response.statusCode == 200 && isOver18Gate(response.body)) {
      response = await _read(uri, null, {'over18': '1'});
    }

    if (response.statusCode != 200) {
      throw _errorFor(response, uri);
    }

    return isOver18Gate(response.body) ? null : response.body;
  }

  /// The comment thread of a post, scraped from the old site.
  ///
  /// Returns the post's own text alongside, because a self post's body is not
  /// always in the listing that led here.
  Future<({List<RedditComment> comments, String? selfText})> fetchComments(String permalink) async {
    final path = permalink.startsWith('/') ? permalink : '/$permalink';
    final uri = Uri.parse('$_publicFallbackBase$path');

    var response = await _read(uri);

    if (response.statusCode == 200 && isOver18Gate(response.body)) {
      response = await _read(uri, null, {'over18': '1'});
    }

    if (response.statusCode == 404) {
      throw RedditException(RedditErrorKind.notFound, 'No such post: $permalink');
    }
    if (response.statusCode != 200) {
      throw _errorFor(response, uri);
    }

    return (comments: parseComments(response.body), selfText: parseSelfText(response.body));
  }

  static String _publicJsonPath(String base, String name, RedditSort sort) =>
      '$base/r/$name/${redditSortPath(sort)}.json';

  /// Whether [uri] is the website rather than the API, which decides how the
  /// request has to introduce itself.
  static bool isPublicHost(Uri uri) => uri.host != Uri.parse(_apiBase).host;

  /// One GET, with the token when there is one. A 401 drops the cached token so
  /// the next attempt re-authorises.
  Future<http.Response> _read(Uri uri, [String? token, Map<String, String>? cookies]) async {
    final public = isPublicHost(uri);
    final jar = {..._cookies, ...?cookies};

    final response = await _send(() => httpClient.get(uri, headers: {
          if (token != null) 'Authorization': 'Bearer $token',
          'User-Agent': public ? publicUserAgent : userAgent,
          // The website weighs these too; their absence is another bot tell.
          if (public) 'Accept': 'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8',
          if (public) 'Accept-Language': 'en-US,en;q=0.9',
          if (public && jar.isNotEmpty)
            'Cookie': jar.entries.map((e) => '${e.key}=${e.value}').join('; '),
        }));

    _rememberCookies(response, cookies);

    if (response.statusCode == 401) {
      forgetToken();
    }
    if (token != null && response.statusCode != 200) {
      throw _errorFor(response, uri);
    }

    return response;
  }

  /// Confirms a client id works, used by the settings screen.
  Future<bool> verify({required String clientId}) async {
    forgetToken();
    await _authorize(clientId);
    return true;
  }

  RedditListing _listingFrom(Map<String, dynamic> decoded) {
    final data = decoded['data'];
    if (data is! Map) {
      throw const RedditException(RedditErrorKind.badResponse, 'Listing has no data');
    }

    final children = data['children'];
    final posts = <RedditPost>[];
    if (children is List) {
      for (final child in children) {
        if (child is Map) {
          final post = RedditPost.fromChild(Map<String, dynamic>.from(child));
          if (post != null) {
            posts.add(post);
          }
        }
      }
    }

    final after = data['after'];
    return RedditListing(posts: posts, after: after is String && after.isNotEmpty ? after : null);
  }

  Future<http.Response> _send(Future<http.Response> Function() request) async {
    try {
      return await request().timeout(_timeout);
    } on RedditException {
      rethrow;
    } catch (e) {
      throw RedditException(RedditErrorKind.network, '$e');
    }
  }

  Map<String, dynamic> _decode(http.Response response) {
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map) {
        return Map<String, dynamic>.from(decoded);
      }
    } catch (_) {
      // Fall through to the shared error below.
    }
    throw const RedditException(RedditErrorKind.badResponse, 'Response was not a JSON object');
  }

  RedditException _errorFor(http.Response response, [Uri? uri]) {
    // The host tells the anonymous read apart from the authenticated one, which
    // is the first thing worth knowing when a reader reports a failure.
    final detail = uri == null ? 'HTTP ${response.statusCode}' : 'HTTP ${response.statusCode} from ${uri.host}';
    return switch (response.statusCode) {
      401 => RedditException(RedditErrorKind.unauthorized, detail),
      403 => RedditException(RedditErrorKind.blocked, detail),
      404 => RedditException(RedditErrorKind.notFound, detail),
      429 => RedditException(RedditErrorKind.rateLimited, detail),
      _ => RedditException(RedditErrorKind.badResponse, detail),
    };
  }
}

final RegExp _subredditPattern = RegExp(r'^[A-Za-z0-9_]{2,21}$');

/// Pulls a subreddit name out of whatever the user pasted: `dartlang`,
/// `r/dartlang`, `/r/dartlang/`, or a full reddit.com URL.
String? normaliseSubreddit(String raw) {
  var text = raw.trim();
  if (text.isEmpty) {
    return null;
  }

  final uri = Uri.tryParse(text);
  if (uri != null && uri.host.isNotEmpty) {
    final segments = uri.pathSegments.where((s) => s.isNotEmpty).toList();
    final index = segments.indexOf('r');
    if (index == -1 || index + 1 >= segments.length) {
      return null;
    }
    text = segments[index + 1];
  }

  text = text.replaceFirst(RegExp(r'^/?r/', caseSensitive: false), '');
  text = text.replaceAll(RegExp(r'^/+|/+$'), '');

  return _subredditPattern.hasMatch(text) ? text : null;
}
