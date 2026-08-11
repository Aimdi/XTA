import 'dart:async';

import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_cache.dart';
import 'package:xta/plugins/reddit/reddit_sort_sheet.dart';

/// Reddit's access tokens live an hour. Minting one is a request of its own, so
/// it is kept for slightly less than that rather than paid for on every read.
const Duration kRedditUserTokenTtl = Duration(minutes: 50);

/// Where every Reddit surface gets its posts.
///
/// Fetching used to be written out three times — the Reddit tab, the home
/// timeline and For you each had their own copy — so the same subreddits were
/// downloaded again on every swipe between them, several requests each on the
/// account-free route. This is that one implementation: it reads through a
/// shared cache, signs in once, isolates a failing subreddit, and returns posts
/// newest first.
class RedditPostSource {
  static final log = Logger('RedditPostSource');

  final RedditClient client;
  final BasePrefService prefs;
  final RedditAuth auth;
  final RedditListingCache cache;

  final DateTime Function() _now;

  RedditPostSource(
    this.client,
    this.prefs, {
    RedditAuth? auth,
    RedditListingCache? cache,
    DateTime Function()? clock,
  }) : auth = auth ?? RedditAuth(),
       cache = cache ?? RedditListingCache(),
       _now = clock ?? DateTime.now;

  String? _userToken;
  String? _userTokenFor;
  DateTime? _userTokenExpiry;
  Future<String?>? _pendingUserToken;
  String? _credentials;

  /// Posts from [subreddits], newest first, at most [limit] of each.
  ///
  /// One unreachable subreddit must not empty the feed of the others, so each
  /// is caught on its own and contributes nothing rather than throwing out of
  /// here. An empty result is a real answer: a subreddit that is gone has to
  /// take its posts with it.
  Future<List<RedditPost>> posts(
    Iterable<String> subreddits, {
    RedditSort? sort,
    int limit = kRedditListingPageSize,
    bool forceRefresh = false,
  }) async {
    final names = subreddits.toSet().toList(growable: false);
    if (names.isEmpty) {
      return const [];
    }

    _forgetOnCredentialChange();

    final order = sort ?? storedRedditSort(prefs);
    final timeFilter = storedRedditTimeFilter(prefs);
    final effectiveTimeFilter = redditTimeFilterForSort(order, timeFilter);
    final nsfwMode = storedRedditNsfwMode(prefs);
    final clientId = prefs.get<String>(optionPluginRedditClientId) ?? '';
    final preferPublic =
        prefs.get<String>(optionPluginRedditSource) == redditSourcePublic;
    final userToken = await _userAccessToken(preferPublic: preferPublic);

    final listings = await Future.wait(
      names.map((name) async {
        try {
          final listing = await cache.listing(
            (subreddit: name, sort: order, timeFilter: effectiveTimeFilter),
            forceRefresh: forceRefresh,
            fetch: () => client.fetchSubreddit(
              name,
              clientId: clientId,
              sort: order,
              timeFilter: timeFilter,
              limit: kRedditListingPageSize,
              userToken: userToken,
              preferPublic: preferPublic,
            ),
          );

          return filterRedditPosts(
            listing.posts.where((p) => !p.stickied),
            nsfwMode: nsfwMode,
          ).take(limit).toList(growable: false);
        } catch (e) {
          log.warning('Unable to load r/$name: $e');

          return const <RedditPost>[];
        }
      }),
    );

    return listings.expand((e) => e).toList()..sort(_newestFirst);
  }

  /// Newest first. A pair where either post has no date has no chronological
  /// answer, so it falls back to score rather than inventing one.
  static int _newestFirst(RedditPost a, RedditPost b) {
    final left = a.createdAt;
    final right = b.createdAt;
    if (left == null || right == null) {
      return b.score.compareTo(a.score);
    }

    return right.compareTo(left);
  }

  String _credentialFingerprint() => [
    prefs.get<String>(optionPluginRedditClientId) ?? '',
    prefs.get<String>(optionPluginRedditSource) ?? '',
    prefs.get<String>(optionPluginRedditRefreshToken) ?? '',
  ].join('\u001f');

  /// Signing in, signing out, changing the client id and asking for the
  /// account-free route all change which Reddit is answering, so what was
  /// cached under the old one is not an answer to the new question.
  ///
  /// Noticed here rather than announced by the screens that change it: they
  /// all already ask for a refresh, and one of them forgetting to say the
  /// credentials moved is how a reader signs in and sees nothing happen.
  void _forgetOnCredentialChange() {
    final current = _credentialFingerprint();
    if (_credentials == current) {
      return;
    }

    _credentials = current;
    cache.clear();
    _userToken = null;
    _userTokenFor = null;
    _userTokenExpiry = null;
  }

  /// One access token across surfaces and refreshes, rather than one per read.
  ///
  /// Null is the public route, which is what a reader who asked for it gets
  /// even with a credential sitting there.
  Future<String?> _userAccessToken({required bool preferPublic}) {
    final refreshToken =
        prefs.get<String>(optionPluginRedditRefreshToken) ?? '';
    if (preferPublic || refreshToken.isEmpty) {
      return Future.value(null);
    }

    final token = _userToken;
    final expiry = _userTokenExpiry;
    if (token != null &&
        _userTokenFor == refreshToken &&
        expiry != null &&
        _now().isBefore(expiry)) {
      return Future.value(token);
    }

    // Two surfaces loading at once must not mint two tokens.
    return _pendingUserToken ??= _mintUserToken(
      refreshToken,
    ).whenComplete(() => _pendingUserToken = null);
  }

  /// The token mint, reachable by a test without a live listing around it.
  @visibleForTesting
  Future<String?> userAccessTokenForTest() =>
      _userAccessToken(preferPublic: false);

  Future<String?> _mintUserToken(String refreshToken) async {
    final clientId = prefs.get<String>(optionPluginRedditClientId) ?? '';

    try {
      final token = await auth.accessToken(
        clientId: clientId,
        refreshToken: refreshToken,
      );
      _userToken = token;
      _userTokenFor = refreshToken;
      _userTokenExpiry = _now().add(kRedditUserTokenTtl);

      return token;
    } on RedditException catch (e) {
      // Only Reddit refusing the grant means the session is over. Every other
      // failure — offline, a 500, a rate limit, a block page — used to land
      // here too and wipe the stored refresh token, so being offline at the
      // wrong moment signed the reader out for good, with nothing to say why.
      if (e.kind == RedditErrorKind.unauthorized) {
        await prefs.set(optionPluginRedditRefreshToken, '');
      }

      return null;
    }
  }
}
