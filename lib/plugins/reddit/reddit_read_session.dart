import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';

/// Credentials every Reddit listing and thread read should share.
///
/// Resolving a refresh token once and threading [userToken] / [preferPublic]
/// through feed, home interleave, subreddit screens, and comment threads stops
/// the Reddit tab from being the only place a signed-in session helps.
class RedditReadSession {
  final String clientId;
  final bool preferPublic;
  final String? userToken;

  const RedditReadSession({
    required this.clientId,
    required this.preferPublic,
    this.userToken,
  });

  /// Builds a session from prefs: public when asked, else a user access token
  /// when a refresh token exists, else app-only / scrape via [clientId].
  static Future<RedditReadSession> resolve({
    required BasePrefService prefs,
    RedditAuth? auth,
  }) async {
    final clientId = prefs.get<String>(optionPluginRedditClientId) ?? '';
    final preferPublic =
        prefs.get<String>(optionPluginRedditSource) == redditSourcePublic;
    if (preferPublic) {
      return RedditReadSession(clientId: clientId, preferPublic: true);
    }

    final refreshToken =
        prefs.get<String>(optionPluginRedditRefreshToken) ?? '';
    if (refreshToken.isEmpty) {
      return RedditReadSession(clientId: clientId, preferPublic: false);
    }

    try {
      final token = await (auth ?? RedditAuth()).accessToken(
        clientId: clientId,
        refreshToken: refreshToken,
      );
      return RedditReadSession(
        clientId: clientId,
        preferPublic: false,
        userToken: token,
      );
    } on RedditException {
      // Reddit no longer accepts this refresh token — drop it so every path
      // falls back the same way instead of retrying a dead session.
      await prefs.set(optionPluginRedditRefreshToken, '');
      return RedditReadSession(clientId: clientId, preferPublic: false);
    }
  }

  Future<RedditListing> fetchSubreddit(
    RedditClient client,
    String name, {
    RedditSort sort = RedditSort.hot,
    RedditTimeFilter timeFilter = RedditTimeFilter.day,
    int limit = kRedditListingPageSize,
    String? after,
  }) => client.fetchSubreddit(
    name,
    clientId: clientId,
    sort: sort,
    timeFilter: timeFilter,
    limit: limit,
    after: after,
    userToken: userToken,
    preferPublic: preferPublic,
  );

  Future<RedditSubredditAbout> fetchSubredditAbout(
    RedditClient client,
    String name,
  ) => client.fetchSubredditAbout(
    name,
    clientId: clientId,
    userToken: userToken,
    preferPublic: preferPublic,
  );

  Future<
    ({
      List<RedditComment> comments,
      String? selfText,
      String? postUrl,
      List<String> postImages,
    })
  >
  fetchComments(RedditClient client, String permalink, {String? sort}) =>
      client.fetchComments(
        permalink,
        sort: sort,
        clientId: clientId,
        userToken: userToken,
        preferPublic: preferPublic,
      );
}
