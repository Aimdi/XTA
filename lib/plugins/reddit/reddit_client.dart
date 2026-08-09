import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:xta/plugins/reddit/reddit_html.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_comments_json.dart';
import 'package:xta/plugins/reddit/reddit_media_urls.dart';
import 'package:xta/plugins/reddit/reddit_search_html.dart';
import 'package:xta/utils/json.dart';

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
  final bool spoiler;
  final bool stickied;
  final String? thumbnail;

  /// The post's own label within its subreddit, e.g. `Elon Criticism`.
  final String? flair;

  /// Where the post points, as Reddit summarises it: `i.redd.it`, `v.redd.it`,
  /// `self.dartlang`, or an article's domain.
  final String? domain;

  /// The pictures of a gallery post, in the order the author arranged them.
  /// Empty for everything that is not a gallery.
  final List<String> galleryImages;

  /// Reddit's own full-size preview of what the post points at — the poster
  /// frame of a video, the lead image of an article. Null when Reddit made
  /// none.
  final String? previewImage;

  /// A v.redd.it post's streams: the DASH manifest libmpv can play whole, and
  /// the progressive fallback (video-only) kept as the download target.
  final String? videoDashUrl;
  final String? videoFallbackUrl;

  /// The video's own shape, from the payload; null reads as 16:9.
  final double? videoAspectRatio;

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
    this.spoiler = false,
    this.stickied = false,
    this.thumbnail,
    this.flair,
    this.domain,
    this.galleryImages = const [],
    this.previewImage,
    this.videoDashUrl,
    this.videoFallbackUrl,
    this.videoAspectRatio,
  });

  RedditPost copyWith({
    String? url,
    bool? isSelf,
    String? selfText,
    List<String>? galleryImages,
    String? previewImage,
    String? permalink,
  }) {
    return RedditPost(
      id: id,
      title: title,
      subreddit: subreddit,
      permalink: permalink ?? this.permalink,
      author: author,
      score: score,
      commentCount: commentCount,
      createdAt: createdAt,
      url: url ?? this.url,
      isSelf: isSelf ?? this.isSelf,
      selfText: selfText ?? this.selfText,
      over18: over18,
      spoiler: spoiler,
      stickied: stickied,
      thumbnail: thumbnail,
      flair: flair,
      domain: domain,
      galleryImages: galleryImages ?? this.galleryImages,
      previewImage: previewImage ?? this.previewImage,
      videoDashUrl: videoDashUrl,
      videoFallbackUrl: videoFallbackUrl,
      videoAspectRatio: videoAspectRatio,
    );
  }

  bool get hasPlayableVideo => videoDashUrl != null || videoFallbackUrl != null;

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'subreddit': subreddit,
    'permalink': permalink,
    'author': author,
    'score': score,
    'commentCount': commentCount,
    'createdAt': createdAt?.toIso8601String(),
    'url': url,
    'isSelf': isSelf,
    'selfText': selfText,
    'over18': over18,
    'spoiler': spoiler,
    'stickied': stickied,
    'thumbnail': thumbnail,
    'flair': flair,
    'domain': domain,
    'galleryImages': galleryImages,
    'previewImage': previewImage,
    'videoDashUrl': videoDashUrl,
    'videoFallbackUrl': videoFallbackUrl,
    'videoAspectRatio': videoAspectRatio,
  };

  static RedditPost? fromSnapshot(Object? raw) {
    if (raw is! Map) {
      return null;
    }
    final map = Map<String, dynamic>.from(raw);
    final id = map['id'] as String?;
    final title = map['title'] as String?;
    final subreddit = map['subreddit'] as String?;
    final permalink = map['permalink'] as String?;
    if (id == null || title == null || subreddit == null || permalink == null) {
      return null;
    }

    return RedditPost(
      id: id,
      title: title,
      subreddit: subreddit,
      permalink: permalink,
      author: map['author'] as String?,
      score: (map['score'] as num?)?.toInt() ?? 0,
      commentCount: (map['commentCount'] as num?)?.toInt() ?? 0,
      createdAt: DateTime.tryParse(map['createdAt'] as String? ?? ''),
      url: map['url'] as String?,
      isSelf: map['isSelf'] == true,
      selfText: map['selfText'] as String?,
      over18: map['over18'] == true,
      spoiler: map['spoiler'] == true,
      stickied: map['stickied'] == true,
      thumbnail: map['thumbnail'] as String?,
      flair: map['flair'] as String?,
      domain: map['domain'] as String?,
      galleryImages:
          (map['galleryImages'] as List?)?.whereType<String>().toList(
            growable: false,
          ) ??
          const [],
      previewImage: map['previewImage'] as String?,
      videoDashUrl: map['videoDashUrl'] as String?,
      videoFallbackUrl: map['videoFallbackUrl'] as String?,
      videoAspectRatio: (map['videoAspectRatio'] as num?)?.toDouble(),
    );
  }

  static List<RedditPost> listFromPrefs(String? raw) {
    if (raw == null || raw.isEmpty) {
      return const [];
    }
    try {
      final decoded = jsonDecode(raw);
      return decoded is List
          ? decoded.map(fromSnapshot).nonNulls.toList(growable: false)
          : const [];
    } catch (_) {
      return const [];
    }
  }

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
  String? get imageUrl =>
      redditImageUrl(url) ??
      (galleryImages.isEmpty ? null : galleryImages.first);

  bool get isVideo => isRedditVideoHost(
    domain ?? (url == null ? null : Uri.tryParse(url!)?.host),
  );

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
          ? DateTime.fromMillisecondsSinceEpoch(
              (created * 1000).round(),
              isUtc: true,
            ).toLocal()
          : null,
      url: map['url'] as String?,
      isSelf: map['is_self'] == true,
      selfText: (map['selftext'] as String?)?.trim(),
      over18: map['over_18'] == true,
      spoiler: map['spoiler'] == true,
      stickied: map['stickied'] == true,
      thumbnail: map['thumbnail'] as String?,
      flair: (map['link_flair_text'] as String?)?.trim(),
      domain: map['domain'] as String?,
      galleryImages: _galleryImagesOf(Json(map)),
      previewImage: _previewImageOf(Json(map)),
      videoDashUrl: _unescapeRedditUrl(
        _redditVideo(Json(map))['dash_url'].string,
      ),
      videoFallbackUrl: _unescapeRedditUrl(
        _redditVideo(Json(map))['fallback_url'].string,
      ),
      videoAspectRatio: _videoAspectOf(_redditVideo(Json(map))),
    );
  }

  /// `secure_media` on the post itself; a crosspost carries it on the original.
  static Json _redditVideo(Json data) {
    final own = data['secure_media']['reddit_video'];
    if (own.exists) {
      return own;
    }
    final media = data['media']['reddit_video'];
    if (media.exists) {
      return media;
    }
    return data['crosspost_parent_list'][0]['secure_media']['reddit_video'];
  }

  static double? _videoAspectOf(Json video) {
    final width = video['width'].number;
    final height = video['height'].number;
    if (width == null || height == null || height <= 0) {
      return null;
    }
    return width / height;
  }

  /// A gallery's files, in the author's order.
  ///
  /// `media_metadata` holds the files keyed by id and `gallery_data` holds the
  /// order; a crosspost sometimes arrives with the files and no order, in which
  /// case the files are still worth showing in whatever order the map has.
  static List<String> _galleryImagesOf(Json data) {
    final metadata = data['media_metadata'];
    if (!metadata.exists) {
      return const [];
    }

    final ordered = [
      for (final item in data['gallery_data']['items'].list)
        item['media_id'].string,
    ];
    final raw = metadata.raw;
    final ids = ordered.nonNulls.isEmpty && raw is Map
        ? raw.keys.map((k) => '$k').toList()
        : ordered.nonNulls.toList();

    return [for (final id in ids) ...?_pick(_galleryFileOf(metadata[id]))];
  }

  /// One gallery picture, at a size a phone can use.
  ///
  /// `s` is the source — the file as it was uploaded, routinely 4000px wide for
  /// a picture the card draws at screen width. `p` holds Reddit's own scaled
  /// copies, so the largest that still fits a screen is the one worth
  /// downloading. An animation has no scaled copies worth having: `p` is a
  /// still frame of it, so a GIF keeps its source and keeps moving.
  static String? _galleryFileOf(Json item) {
    final gif = item['s']['gif'].string;
    if (gif != null || item['e'].string == 'AnimatedImage') {
      return _unescapeRedditUrl(gif ?? item['s']['u'].string);
    }

    return _unescapeRedditUrl(
      _scaledVariant(item['p']) ?? item['s']['u'].string,
    );
  }

  /// Reddit's full-size preview, or the scaled copy nearest the screen.
  ///
  /// The preview is only ever drawn as a banner or a video's poster frame, so
  /// the source — which Reddit keeps at whatever size it was uploaded — is
  /// bytes nobody sees.
  static String? _previewImageOf(Json data) {
    final image = data['preview']['images'][0];

    return _unescapeRedditUrl(
      _scaledVariant(image['resolutions']) ?? image['source']['url'].string,
    );
  }

  /// The widest of [variants] that still fits [kRedditDisplayWidth], or null
  /// when Reddit offered none that do.
  ///
  /// Both spellings are read because Reddit ships both: a preview's
  /// `resolutions` are `url`/`width`, a gallery's `p` are `u`/`x`.
  static String? _scaledVariant(Json variants) {
    String? best;
    var width = 0;

    for (final variant in variants.list) {
      final candidate = variant['width'].integer ?? variant['x'].integer;
      final url = variant['url'].string ?? variant['u'].string;
      if (candidate == null ||
          url == null ||
          candidate > kRedditDisplayWidth ||
          candidate <= width) {
        continue;
      }
      best = url;
      width = candidate;
    }

    return best;
  }

  static List<String>? _pick(String? url) => url == null ? null : [url];

  /// Reddit serves media URLs HTML-escaped inside its own JSON (`&amp;`), and
  /// the CDN rejects them in that form.
  static String? _unescapeRedditUrl(String? url) =>
      url?.replaceAll('&amp;', '&');
}

/// The widest a picture is ever drawn: full bleed on the largest phone screen
/// this app runs on. Reddit offers scaled copies of everything it previews, and
/// anything wider than this is bytes the reader pays for and never sees.
const int kRedditDisplayWidth = 1080;

/// How long a public route that refused is tried last for.
///
/// Long enough that a refresh does not re-discover the same refusal for every
/// subreddit in turn, short enough that a passing throttle is forgotten by the
/// time the reader looks again.
const Duration kRedditRouteCooldown = Duration(minutes: 5);

class RedditListing {
  final List<RedditPost> posts;

  /// Reddit's pagination cursor; null when there is no further page.
  final String? after;

  const RedditListing({required this.posts, this.after});
}

/// The ways to a listing that need no credentials, in the order they are tried
/// before anything is known about them.
///
/// old.reddit's HTML leads because it is the one Reddit still serves to
/// everybody; the `.json` endpoints follow because the deprecation has never
/// been total, and www and old are throttled separately.
enum _PublicRoute { html, wwwJson, oldJson }

/// What one route made of the request: a listing, a verdict no other route will
/// contradict ([terminal]), or a failure worth trying the next route after.
typedef _RouteAttempt = ({
  RedditListing? listing,
  RedditException? failure,
  bool terminal,
});

_RouteAttempt _hit(RedditListing listing) =>
    (listing: listing, failure: null, terminal: false);

_RouteAttempt _terminal(RedditErrorKind kind, String detail) =>
    (listing: null, failure: RedditException(kind, detail), terminal: true);

_RouteAttempt _miss(RedditException failure) =>
    (listing: null, failure: failure, terminal: false);

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
RedditSort redditSortFromName(String? name) => RedditSort.values.firstWhere(
  (e) => e.name == name,
  orElse: () => RedditSort.hot,
);

/// The time window Reddit applies to top and controversial listings.
enum RedditTimeFilter {
  hour('hour'),
  day('day'),
  week('week'),
  month('month'),
  year('year'),
  all('all');

  final String queryValue;

  const RedditTimeFilter(this.queryValue);
}

bool redditSortUsesTimeFilter(RedditSort sort) =>
    sort == RedditSort.top || sort == RedditSort.controversial;

RedditTimeFilter redditTimeFilterFromName(String? name) => RedditTimeFilter
    .values
    .firstWhere((e) => e.name == name, orElse: () => RedditTimeFilter.day);

RedditTimeFilter? redditTimeFilterForSort(
  RedditSort sort,
  RedditTimeFilter timeFilter,
) => redditSortUsesTimeFilter(sort) ? timeFilter : null;

/// Which Reddit feeds should do with posts marked over-18.
enum RedditNsfwMode { hide, tap, show }

RedditNsfwMode redditNsfwModeFromName(String? name) => RedditNsfwMode.values
    .firstWhere((e) => e.name == name, orElse: () => RedditNsfwMode.tap);

List<RedditPost> filterRedditPosts(
  Iterable<RedditPost> posts, {
  required RedditNsfwMode nsfwMode,
}) => nsfwMode == RedditNsfwMode.hide
    ? posts.where((post) => !post.over18).toList(growable: false)
    : posts.toList();

/// Which discovery rail is open in the Reddit tab.
enum RedditFeedMode { following, popular, all }

RedditFeedMode redditFeedModeFromName(String? name) => RedditFeedMode.values
    .firstWhere((e) => e.name == name, orElse: () => RedditFeedMode.following);

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
  static const userAgent =
      'android:com.aimdi.xta:1.0 (read-only, account-free)';

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

  /// The account-free route that last answered, and when each route that
  /// refused is worth trying first again. Deliberately memory only: which host
  /// Reddit is serving today is worth nothing tomorrow, and a stored answer
  /// would outlive the throttle that produced it.
  _PublicRoute? _lastGoodRoute;
  final Map<_PublicRoute, DateTime> _refusedUntil = {};

  /// Subreddit pictures noticed on pages fetched for another reason. A listing
  /// page carries the community's header image, so the feed usually pays for
  /// the icons it shows without asking for them.
  final Map<String, String> _icons = {};

  void _rememberIcon(String subreddit, String? icon) {
    if (icon != null) {
      _icons[subreddit.toLowerCase()] = icon;
    }
  }

  /// Cookies the public hosts have set, kept for the life of the client.
  ///
  /// A client that never carries a cookie looks like a fresh stranger on every
  /// request, which is one of the cheapest bot tells there is. This also holds
  /// the `over18` consent once it has been given, so the gate is answered once
  /// rather than on every subreddit.
  final Map<String, String> _cookies = {};

  void _rememberCookies(http.Response response, Map<String, String>? sent) {
    // Only a cookie that was accepted is worth keeping: consent Reddit refused
    // would otherwise ride along on every later request as a fact.
    if (sent != null && response.statusCode == 200) {
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
        _cookies[pair.substring(0, equals).trim()] = pair
            .substring(equals + 1)
            .trim();
      }
    }
  }

  /// Whether a usable token is already cached.
  bool get hasToken =>
      _token != null && (_tokenExpiry?.isAfter(_now()) ?? false);

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
      throw const RedditException(
        RedditErrorKind.notConfigured,
        'Missing client id',
      );
    }

    final response = await _send(
      () => httpClient.post(
        Uri.parse(_tokenEndpoint),
        headers: {
          'Authorization':
              'Basic ${base64Encode(utf8.encode('${clientId.trim()}:'))}',
          'User-Agent': userAgent,
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body:
            'grant_type=https://oauth.reddit.com/grants/installed_client&device_id=$deviceId',
      ),
    );

    if (response.statusCode != 200) {
      throw _errorFor(response);
    }

    final decoded = _decode(response);
    final token = decoded['access_token'] as String?;
    if (token == null || token.isEmpty) {
      throw const RedditException(
        RedditErrorKind.badResponse,
        'No access_token in the token response',
      );
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
    RedditTimeFilter timeFilter = RedditTimeFilter.day,
    int limit = 25,
    String? after,
    String? userToken,
    bool preferPublic = false,
  }) async {
    final name = normaliseSubreddit(subreddit);
    if (name == null) {
      throw RedditException(
        RedditErrorKind.notFound,
        'Not a subreddit name: $subreddit',
      );
    }

    // A signed-in reader gets their own account's rate limits, which is the
    // most reliable route Reddit offers; otherwise app-only auth when a client
    // id is set, and failing that the public endpoints, which need nothing.
    //
    // [preferPublic] overrides all of that: a reader who asked for the
    // account-free route gets it even when a credential is sitting there.
    final anonymous =
        preferPublic || (userToken == null && clientId.trim().isEmpty);
    final token = userToken ?? (anonymous ? null : await _authorize(clientId));

    final query = {
      'limit': '$limit',
      // Gives real characters instead of HTML entities in titles and text.
      'raw_json': '1',
      if (redditTimeFilterForSort(sort, timeFilter) case final filter?)
        't': filter.queryValue,
      if (after != null && after.isNotEmpty) 'after': after,
    };

    if (!anonymous) {
      final uri = Uri.parse(
        '$_apiBase/r/$name/${redditSortPath(sort)}',
      ).replace(queryParameters: query);
      return _listingFrom(_decode(await _read(uri, token)));
    }

    return _fetchPublicListing(name, sort, query);
  }

  /// A listing over whichever account-free route answers.
  ///
  /// Routes are tried in the order [_publicRoutes] puts them and the first one
  /// that answers ends the search, so the common case costs one request. A
  /// verdict — private, banned, quarantined, missing, behind a login — ends it
  /// too: no second host is going to disagree, and asking anyway was most of
  /// what the anonymous path used to spend.
  Future<RedditListing> _fetchPublicListing(
    String name,
    RedditSort sort,
    Map<String, String> query,
  ) async {
    RedditException? worst;

    for (final route in _publicRoutes()) {
      final attempt = await _attempt(route, name, sort, query);
      final listing = attempt.listing;

      if (listing != null || attempt.terminal) {
        // Either way the route is alive and worth asking first next time.
        _rememberRoute(route);
        return listing ?? (throw attempt.failure!);
      }

      _refuseRoute(route);
      worst = _moreTelling(worst, attempt.failure);
    }

    throw _publicFailure(worst, name);
  }

  /// The routes to try, most likely to work first.
  ///
  /// A route that refused recently is tried last rather than dropped — a
  /// refusal is a wait, not a verdict. When every route is in that state the
  /// request is still made, but only against one of them: asking all three,
  /// once per subreddit, is how one refresh turns into forty refusals.
  List<_PublicRoute> _publicRoutes() {
    final good = _lastGoodRoute;
    final ordered = [
      ?good,
      ..._PublicRoute.values.where((route) => route != good),
    ];

    final fresh = ordered.where((route) => !_isRefused(route)).toList();
    if (fresh.isEmpty) {
      return [
        ordered.reduce(
          (a, b) => _refusedUntil[b]!.isBefore(_refusedUntil[a]!) ? b : a,
        ),
      ];
    }

    return [...fresh, ...ordered.where(_isRefused)];
  }

  bool _isRefused(_PublicRoute route) =>
      _refusedUntil[route]?.isAfter(_now()) ?? false;

  void _refuseRoute(_PublicRoute route) =>
      _refusedUntil[route] = _now().add(kRedditRouteCooldown);

  void _rememberRoute(_PublicRoute route) {
    _lastGoodRoute = route;
    _refusedUntil.remove(route);
  }

  /// One route's turn. Only the network can throw out of here; everything a
  /// response can say is an [_RouteAttempt].
  Future<_RouteAttempt> _attempt(
    _PublicRoute route,
    String name,
    RedditSort sort,
    Map<String, String> query,
  ) async {
    try {
      return route == _PublicRoute.html
          ? await _scrapeListing(name, sort, query)
          : await _readPublicJson(route, name, sort, query);
    } on RedditException catch (e) {
      return _miss(e);
    }
  }

  /// Reads a listing off old.reddit's HTML — the route that still works without
  /// an account, and the one Stealth takes for the same reason.
  ///
  /// The over-18 gate is answered with a cookie rather than a login: Reddit
  /// wants consent recorded, not an account, and `over18=1` is what its own
  /// form sets. The cookie is kept, so the gate costs one extra request per
  /// session rather than one per subreddit.
  Future<_RouteAttempt> _scrapeListing(
    String name,
    RedditSort sort,
    Map<String, String> query,
  ) async {
    final uri = Uri.parse(
      '$_publicFallbackBase/r/$name/${redditSortPath(sort)}',
    ).replace(queryParameters: {...query}..remove('raw_json'));

    var response = await _read(uri);
    final early = _statusVerdict(response, name, uri);
    if (early != null) {
      return early;
    }

    var page = readListingPage(response.body);
    if (page.kind == RedditPageKind.over18Gate) {
      response = await _read(uri, null, {'over18': '1'});
      page = readListingPage(response.body);
    }
    _rememberIcon(name, page.icon);

    final verdict = _pageVerdict(page.kind, name);
    if (verdict != null) {
      return verdict;
    }

    if (response.statusCode == 200 && page.kind == RedditPageKind.listing) {
      return _hit(RedditListing(posts: page.posts, after: page.after));
    }

    return _miss(_errorFor(response, uri));
  }

  /// The `.json` endpoints, in case the deprecation is not total for this
  /// reader or Reddit walks part of it back.
  Future<_RouteAttempt> _readPublicJson(
    _PublicRoute route,
    String name,
    RedditSort sort,
    Map<String, String> query,
  ) async {
    final base = route == _PublicRoute.wwwJson
        ? _publicBase
        : _publicFallbackBase;
    final uri = Uri.parse(
      _publicJsonPath(base, name, sort),
    ).replace(queryParameters: query);

    final response = await _read(uri);
    final early = _statusVerdict(response, name, uri);
    if (early != null) {
      return early;
    }
    if (response.statusCode != 200) {
      return _miss(_errorFor(response, uri));
    }

    try {
      return _hit(_listingFrom(_decode(response)));
    } on RedditException catch (e) {
      // The path said `.json` and the answer was not JSON — a block page, most
      // likely. Another route may still be served.
      return _miss(e);
    }
  }

  /// What a status alone already settles, or null when the body has to be read.
  ///
  /// A redirect to the login page is Reddit refusing an anonymous reader rather
  /// than an answer about the subreddit, and it is reported as the refusal it
  /// is instead of as an empty feed.
  _RouteAttempt? _statusVerdict(http.Response response, String name, Uri uri) {
    if (response.statusCode == 404) {
      return _terminal(
        RedditErrorKind.notFound,
        'HTTP 404 from ${uri.host}: no r/$name',
      );
    }

    final location = _loginRedirectOf(response);
    if (location != null) {
      return _terminal(
        RedditErrorKind.blocked,
        'HTTP ${response.statusCode} to a login page ($location)',
      );
    }

    // 403 carries the private and quarantined interstitials, so its body is
    // worth reading; every other refusal is taken at face value.
    return const [200, 403].contains(response.statusCode)
        ? null
        : _miss(_errorFor(response, uri));
  }

  /// The verdict a page carries, or null when it is a listing to be used.
  _RouteAttempt? _pageVerdict(RedditPageKind kind, String name) =>
      switch (kind) {
        RedditPageKind.private => _terminal(
          RedditErrorKind.notFound,
          'r/$name is private',
        ),
        RedditPageKind.banned => _terminal(
          RedditErrorKind.notFound,
          'r/$name has been banned',
        ),
        RedditPageKind.quarantined => _terminal(
          RedditErrorKind.blocked,
          'r/$name is quarantined',
        ),
        RedditPageKind.loginWall => _terminal(
          RedditErrorKind.blocked,
          'Reddit answered with a login page',
        ),
        RedditPageKind.listing ||
        RedditPageKind.over18Gate ||
        RedditPageKind.unreadable => null,
      };

  /// Where a redirect to Reddit's login page points, or null for anything else.
  ///
  /// The HTTP client follows redirects itself, so this only fires when it was
  /// told not to or could not — but a login wall reached either way is the same
  /// refusal.
  static String? _loginRedirectOf(http.Response response) {
    if (response.statusCode < 300 || response.statusCode >= 400) {
      return null;
    }

    final location = response.headers['location'];
    return location != null && location.contains('/login') ? location : null;
  }

  /// Which of two failures is worth reporting. A throttle and a refusal say
  /// something a reshaped page does not.
  static RedditException? _moreTelling(RedditException? a, RedditException? b) {
    int rank(RedditException? e) => switch (e?.kind) {
      RedditErrorKind.rateLimited => 4,
      RedditErrorKind.blocked => 3,
      RedditErrorKind.network => 2,
      null => 0,
      _ => 1,
    };

    return rank(b) > rank(a) ? b : a;
  }

  /// What to report when no route served a listing.
  ///
  /// A refusal used to be reported as "add a client id", which was true once
  /// and is not now: Reddit turns away almost every new app registration, so
  /// that advice sent readers somewhere they cannot get to. What actually
  /// happened is more useful — a refusal usually passes, and signing in is the
  /// real way around a persistent one.
  static RedditException _publicFailure(RedditException? worst, String name) {
    if (worst == null) {
      return RedditException(
        RedditErrorKind.badResponse,
        'No public route served r/$name',
      );
    }

    final status = switch (worst.kind) {
      RedditErrorKind.blocked => 403,
      RedditErrorKind.rateLimited => 429,
      _ => null,
    };

    return status != null && worst.detail.startsWith('HTTP $status')
        ? RedditException(worst.kind, 'HTTP $status from both public hosts')
        : worst;
  }

  /// Posts matching [query], across Reddit or within one subreddit.
  Future<List<RedditPost>> searchPosts(
    String query, {
    String? subreddit,
    RedditSort sort = RedditSort.hot,
  }) async {
    final name = subreddit == null ? null : normaliseSubreddit(subreddit);
    final path = name == null ? '/search' : '/r/$name/search';

    final body = await _scrape(
      Uri.parse('$_publicFallbackBase$path').replace(
        queryParameters: {
          'q': query,
          'sort': sort == RedditSort.newest ? 'new' : 'relevance',
          't': 'all',
          if (name != null) 'restrict_sr': 'on',
        },
      ),
    );

    return body == null ? const [] : parseSearchPosts(body);
  }

  Future<List<RedditSubredditResult>> searchSubreddits(String query) async {
    final body = await _scrape(
      Uri.parse(
        '$_publicFallbackBase/subreddits/search',
      ).replace(queryParameters: {'q': query}),
    );

    return body == null ? const [] : parseSubredditResults(body);
  }

  Future<List<RedditUserResult>> searchUsers(String query) async {
    final body = await _scrape(
      Uri.parse(
        '$_publicFallbackBase/search',
      ).replace(queryParameters: {'q': query, 'type': 'user'}),
    );

    return body == null ? const [] : parseUserResults(body);
  }

  /// One account's posts. Comments on the same page have no title and are
  /// skipped by the listing parser, which is the behaviour we want here.
  Future<RedditListing> fetchUserPosts(String user, {String? after}) async {
    final name = user
        .replaceFirst(RegExp(r'^/?u(?:ser)?/', caseSensitive: false), '')
        .trim();
    if (name.isEmpty) {
      throw RedditException(RedditErrorKind.notFound, 'Not a username: $user');
    }

    final body = await _scrape(
      Uri.parse('$_publicFallbackBase/user/$name/submitted').replace(
        queryParameters: {
          'limit': '25',
          if (after != null && after.isNotEmpty) 'after': after,
        },
      ),
    );

    if (body == null) {
      throw RedditException(RedditErrorKind.notFound, 'No such account: $name');
    }

    final listing = parseListing(body);
    return RedditListing(posts: listing.posts, after: listing.after);
  }

  /// A subreddit's own picture, or null when it has none to find.
  ///
  /// Free when the feed has already read a page of that subreddit this session,
  /// because the picture is on the page it read. Otherwise it costs one page —
  /// asked for with `limit=1`, since the header is what is wanted and
  /// twenty-five posts of markup underneath it are not.
  ///
  /// Never throws: a timeline that failed to load because a picture could not
  /// be fetched would be a poor trade.
  Future<String?> fetchSubredditIcon(String subreddit) async {
    final name = normaliseSubreddit(subreddit);
    if (name == null) {
      return null;
    }

    final known = _icons[name.toLowerCase()];
    if (known != null) {
      return known;
    }

    try {
      final body = await _scrape(
        Uri.parse(
          '$_publicFallbackBase/r/$name/',
        ).replace(queryParameters: {'limit': '1'}),
      );
      final icon = body == null ? null : parseSubredditIcon(body);
      _rememberIcon(name, icon);

      return icon;
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

  /// The comment thread of a post.
  ///
  /// Same credential choice as [fetchSubreddit]: OAuth JSON when a user token
  /// or client id is available, old-site HTML scrape when the reader is
  /// anonymous. Returns the post's own text and media alongside, because a self
  /// post's body is not always in the listing that led here.
  Future<
    ({
      List<RedditComment> comments,
      String? selfText,
      String? postUrl,
      List<String> postImages,
    })
  >
  fetchComments(
    String permalink, {
    String? sort,
    required String clientId,
    String? userToken,
    bool preferPublic = false,
  }) async {
    final anonymous =
        preferPublic || (userToken == null && clientId.trim().isEmpty);
    if (anonymous) {
      return _commentsFromScrape(permalink, sort: sort);
    }

    // Thread UX should still work when OAuth is flaky — scrape already does.
    try {
      final token = userToken ?? await _authorize(clientId);
      return await _commentsFromOauth(permalink, sort: sort, token: token);
    } on RedditException {
      return _commentsFromScrape(permalink, sort: sort);
    }
  }

  /// The pictures of a gallery post, read from the post's own public JSON.
  ///
  /// old.reddit's listing HTML carries a gallery as a link and a 70px
  /// thumbnail — `media_metadata` is simply not in that page — so a reader with
  /// no client id saw a link card where the pictures should be. This is the
  /// same public route the thread already reads, needing no account and no
  /// knowledge of Reddit's markup.
  ///
  /// Never throws: this runs for one card in a feed, and a refusal there must
  /// cost that card its pictures, not the whole feed its posts.
  Future<List<String>> fetchGalleryImages(String permalink) async {
    try {
      final uri = Uri.parse(
        '$_publicBase${_commentsJsonPath(permalink)}',
      ).replace(queryParameters: {'raw_json': '1'});

      final response = await _read(uri);
      if (response.statusCode != 200) {
        return const [];
      }

      return _threadFromJson(_decodeList(response)).postImages;
    } catch (_) {
      return const [];
    }
  }

  Future<
    ({
      List<RedditComment> comments,
      String? selfText,
      String? postUrl,
      List<String> postImages,
    })
  >
  _commentsFromOauth(
    String permalink, {
    String? sort,
    required String token,
  }) async {
    final query = {
      'raw_json': '1',
      if (sort != null && sort.isNotEmpty) 'sort': sort,
    };
    final uri = Uri.parse(
      '$_apiBase${_commentsJsonPath(permalink)}',
    ).replace(queryParameters: query);
    final decoded = _decodeList(await _read(uri, token));
    return _threadFromJson(decoded);
  }

  Future<
    ({
      List<RedditComment> comments,
      String? selfText,
      String? postUrl,
      List<String> postImages,
    })
  >
  _commentsFromScrape(String permalink, {String? sort}) async {
    final path = permalink.startsWith('/') ? permalink : '/$permalink';
    var uri = Uri.parse('$_publicFallbackBase$path');
    if (sort != null && sort.isNotEmpty) {
      uri = uri.replace(queryParameters: {'sort': sort});
    }

    var response = await _read(uri);

    if (response.statusCode == 200 && isOver18Gate(response.body)) {
      response = await _read(uri, null, {'over18': '1'});
    }

    if (response.statusCode == 404) {
      throw RedditException(
        RedditErrorKind.notFound,
        'No such post: $permalink',
      );
    }
    if (response.statusCode != 200) {
      throw _errorFor(response, uri);
    }

    final media = parsePostMedia(response.body);
    return (
      comments: parseComments(response.body),
      selfText: parseSelfText(response.body),
      postUrl: media.url,
      postImages: media.images,
    );
  }

  /// Reddit's comments endpoint is `[postListing, commentsListing]`.
  ({
    List<RedditComment> comments,
    String? selfText,
    String? postUrl,
    List<String> postImages,
  })
  _threadFromJson(List<dynamic> decoded) {
    final root = Json(decoded);
    final postRaw = root[0]['data']['children'][0].raw;
    final post = postRaw is Map
        ? RedditPost.fromChild(Map<String, dynamic>.from(postRaw))
        : null;

    return (
      comments: commentsFromListing(root[1]),
      selfText: post?.selfText,
      postUrl: post?.url,
      postImages: post?.galleryImages ?? const [],
    );
  }

  static String _commentsJsonPath(String permalink) {
    var path = permalink.split('?').first.trim();
    if (!path.startsWith('/')) {
      path = '/$path';
    }
    if (path.endsWith('/')) {
      path = path.substring(0, path.length - 1);
    }
    return path.endsWith('.json') ? path : '$path.json';
  }

  static String _publicJsonPath(String base, String name, RedditSort sort) =>
      '$base/r/$name/${redditSortPath(sort)}.json';

  /// Whether [uri] is the website rather than the API, which decides how the
  /// request has to introduce itself.
  static bool isPublicHost(Uri uri) => uri.host != Uri.parse(_apiBase).host;

  /// One GET, with the token when there is one. A 401 drops the cached token so
  /// the next attempt re-authorises.
  Future<http.Response> _read(
    Uri uri, [
    String? token,
    Map<String, String>? cookies,
  ]) async {
    final public = isPublicHost(uri);
    final jar = {..._cookies, ...?cookies};

    final response = await _send(
      () => httpClient.get(
        uri,
        headers: {
          if (token != null) 'Authorization': 'Bearer $token',
          'User-Agent': public ? publicUserAgent : userAgent,
          // The website weighs these too; their absence is another bot tell.
          if (public)
            'Accept':
                'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8',
          if (public) 'Accept-Language': 'en-US,en;q=0.9',
          if (public && jar.isNotEmpty)
            'Cookie': jar.entries.map((e) => '${e.key}=${e.value}').join('; '),
        },
      ),
    );

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
      throw const RedditException(
        RedditErrorKind.badResponse,
        'Listing has no data',
      );
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
    return RedditListing(
      posts: posts,
      after: after is String && after.isNotEmpty ? after : null,
    );
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
    throw const RedditException(
      RedditErrorKind.badResponse,
      'Response was not a JSON object',
    );
  }

  List<dynamic> _decodeList(http.Response response) {
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is List) {
        return decoded;
      }
    } catch (_) {
      // Fall through to the shared error below.
    }
    throw const RedditException(
      RedditErrorKind.badResponse,
      'Response was not a JSON array',
    );
  }

  RedditException _errorFor(http.Response response, [Uri? uri]) {
    // The host tells the anonymous read apart from the authenticated one, which
    // is the first thing worth knowing when a reader reports a failure.
    final detail = uri == null
        ? 'HTTP ${response.statusCode}'
        : 'HTTP ${response.statusCode} from ${uri.host}';
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
