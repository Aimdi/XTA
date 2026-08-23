import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_login_webview.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/json.dart';

/// The Reddit sign-in, in one place.
///
/// The feed's overflow menu and the settings screen both offer signing in, the
/// client id and the way back out. Two copies of that would be two places for
/// the state check — the only thing standing between the reader's XTA and
/// somebody else's Reddit account — to be got wrong.

/// How much of the OAuth `state` is random. 32 bytes is the size of the hashes
/// everything else here is built on, and there is no cost to it.
const int _stateBytes = 32;

/// A fresh OAuth `state` — the value Reddit echoes back on the redirect, which
/// [RedditAuth.codeFrom] compares before it will accept a code.
///
/// It used to be `DateTime.now().microsecondsSinceEpoch` in base 36, which is
/// not a secret: it is the clock. Anyone able to steer the login webview at a
/// redirect of their own making could also work out what microsecond the login
/// started, and a guessed state passes the check outright — which would bind
/// this reader's XTA to *their* Reddit account without the reader seeing
/// anything unusual. [Random.secure] is the platform CSPRNG, so the value
/// cannot be reconstructed from when it was made.
String redditOauthState({Random? random}) {
  final source = random ?? Random.secure();
  final bytes = List<int>.generate(_stateBytes, (_) => source.nextInt(256));

  // base64url has nothing a query string would have to escape; the padding is
  // dropped because it carries no information and only survives round trips by
  // luck.
  return base64UrlEncode(bytes).replaceAll('=', '');
}

/// Whether a sign-in is stored.
bool redditSignedIn(BasePrefService prefs) =>
    (prefs.get<String>(optionPluginRedditRefreshToken) ?? '').trim().isNotEmpty;

/// The client id the reader registered, or an empty string.
String redditClientId(BasePrefService prefs) =>
    (prefs.get<String>(optionPluginRedditClientId) ?? '').trim();

/// Whether the reader asked to be read the account-free way regardless of the
/// credentials that happen to be stored.
bool redditPrefersPublic(BasePrefService prefs) =>
    prefs.get<String>(optionPluginRedditSource) == redditSourcePublic;

/// Forgets the sign-in.
///
/// The refresh token *is* the session: Reddit hands out hour-long access tokens
/// against it for as long as it exists, and nothing else identifying the reader
/// is kept. So signing out is exactly this write, and it has to happen even if
/// everything after it fails. The cached app-only token goes too — it was
/// fetched on behalf of a reader who has just left.
Future<void> redditForgetSignIn(
  BasePrefService prefs,
  RedditClient client,
) async {
  await prefs.set(optionPluginRedditRefreshToken, '');
  client.forgetToken();
}

/// Signs in, and reports whether anything changed so the caller can redraw.
///
/// Signing in gets the reader their own account's rate limits, which is the
/// most reliable route Reddit offers. It still needs a client id: the login
/// authorises *this app*, and Reddit has to know which app that is.
Future<bool> signInToReddit(BuildContext context) async {
  final prefs = PrefService.of(context, listen: false);
  final clientId = redditClientId(prefs);
  if (clientId.isEmpty) {
    return editRedditClientId(context);
  }

  final code = await Navigator.push<String>(
    context,
    MaterialPageRoute(
      builder: (_) =>
          RedditLoginWebview(clientId: clientId, state: redditOauthState()),
    ),
  );
  if (code == null || !context.mounted) {
    return false;
  }

  return _exchange(context, prefs: prefs, clientId: clientId, code: code);
}

/// The stored token is written through [prefs] rather than through the context:
/// a reader who navigates away while Reddit is answering is still signed in,
/// and the token would otherwise be dropped on the floor.
Future<bool> _exchange(
  BuildContext context, {
  required BasePrefService prefs,
  required String clientId,
  required String code,
}) async {
  try {
    final refreshToken = await context.read<RedditAuth>().exchangeCode(
      clientId: clientId,
      code: code,
    );
    await prefs.set(optionPluginRedditRefreshToken, refreshToken);
    if (!context.mounted) {
      return true;
    }

    // The webview closing is not by itself proof the token was accepted.
    showSnackBar(
      context,
      icon: '✅',
      message: L10n.of(context).plugin_reddit_signed_in,
    );
    await context.read<RedditFeedStore>().refresh();
    return true;
  } on RedditException catch (e) {
    if (context.mounted) {
      showSnackBar(
        context,
        icon: '🔒',
        message:
            '${L10n.of(context).plugin_reddit_sign_in_failed}\n${e.detail}',
      );
    }
    return false;
  }
}

/// Signs out and reloads the feed through whatever route is left.
Future<void> signOutOfReddit(BuildContext context) async {
  await redditForgetSignIn(
    PrefService.of(context, listen: false),
    context.read<RedditClient>(),
  );
  if (context.mounted) {
    await context.read<RedditFeedStore>().refresh();
  }
}

/// Asks for the client id, and reports whether it was stored.
Future<bool> editRedditClientId(BuildContext context) async {
  final prefs = PrefService.of(context, listen: false);
  final saved = await showDialog<String>(
    context: context,
    builder: (_) => _ClientIdDialog(
      initial: prefs.get<String>(optionPluginRedditClientId) ?? '',
    ),
  );

  if (saved == null || !context.mounted) {
    return false;
  }

  await prefs.set(optionPluginRedditClientId, saved);
  if (!context.mounted) {
    return true;
  }

  // The cached app-only token was minted by the old id and would outlive it.
  context.read<RedditClient>().forgetToken();
  await context.read<RedditFeedStore>().refresh();
  return true;
}

class _ClientIdDialog extends StatefulWidget {
  final String initial;

  const _ClientIdDialog({required this.initial});

  @override
  State<_ClientIdDialog> createState() => _ClientIdDialogState();
}

class _ClientIdDialogState extends State<_ClientIdDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initial);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final style = Theme.of(context).textTheme.bodySmall;

    return AlertDialog(
      title: Text(l10n.plugin_reddit_client_id),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.plugin_reddit_client_id_help, style: style),
          const SizedBox(height: 8),
          // Reddit rejects the login unless the registered app carries this
          // exact redirect, and it is not guessable — so it is stated here
          // rather than left to be discovered.
          Text(
            l10n.plugin_reddit_redirect_uri_help(RedditAuth.redirectUri),
            style: style,
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _controller,
            autofocus: true,
            autocorrect: false,
            decoration: InputDecoration(hintText: l10n.plugin_reddit_client_id),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context, _controller.text.trim()),
          child: Text(l10n.save),
        ),
      ],
    );
  }
}

/// Which Reddit account the stored sign-in belongs to.
///
/// "Signed in" on its own is not much use on a device that has been signed in
/// to two accounts over its life, and it is the one question the `identity`
/// scope is asked for. Nothing is stored: the name is read when the settings
/// screen is open and forgotten with the screen, so signing out leaves nothing
/// to clear.
class RedditIdentityStore extends Store<String?> {
  static const _meEndpoint = 'https://oauth.reddit.com/api/v1/me';

  final RedditAuth auth;

  /// Shared with [auth] so the sign-in and this ask over one connection.
  http.Client get httpClient => auth.httpClient;

  RedditIdentityStore(this.auth) : super(null);

  /// Reads the account name, or leaves it null when there is nothing to ask
  /// with.
  ///
  /// A reader who chose the account-free route is not asked about: the whole
  /// point of that choice is that Reddit is not told who is reading, and a
  /// request naming them on the settings screen would go behind it.
  Future<void> load(BasePrefService prefs) async {
    final refreshToken =
        prefs.get<String>(optionPluginRedditRefreshToken) ?? '';
    final clientId = redditClientId(prefs);

    if (refreshToken.isEmpty ||
        clientId.isEmpty ||
        redditPrefersPublic(prefs)) {
      update(null);
      return;
    }

    await execute(() => _name(clientId: clientId, refreshToken: refreshToken));
  }

  Future<String?> _name({
    required String clientId,
    required String refreshToken,
  }) async {
    try {
      final token = await auth.accessToken(
        clientId: clientId,
        refreshToken: refreshToken,
      );
      final response = await httpClient.get(
        Uri.parse(_meEndpoint),
        headers: {
          'Authorization': 'Bearer $token',
          'User-Agent': RedditClient.userAgent,
        },
      );

      return response.statusCode == 200
          ? Json(jsonDecode(response.body))['name'].string
          : null;
    } catch (_) {
      // The name is decoration. A sign-in that works for reading must not look
      // broken because this one request did not answer.
      return null;
    }
  }
}
