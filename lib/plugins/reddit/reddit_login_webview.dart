import 'package:flutter/material.dart';
import 'package:logging/logging.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:webview_flutter/webview_flutter.dart';

final _log = Logger('RedditLoginWebview');

/// Reddit's login page, watched for the redirect that carries the code back.
///
/// Pops the authorization code, or null when the reader backs out or declines.
/// Nothing is stored here — the caller trades the code for a refresh token, so
/// this screen never holds a credential.
class RedditLoginWebview extends StatefulWidget {
  final String clientId;

  /// Echoed back by Reddit and checked on return, so a code from anywhere else
  /// is ignored. It has to be unguessable for that check to mean anything —
  /// `redditOauthState()` in reddit_account.dart is where it comes from.
  final String state;

  const RedditLoginWebview({super.key, required this.clientId, required this.state});

  @override
  State<RedditLoginWebview> createState() => _RedditLoginWebviewState();
}

class _RedditLoginWebviewState extends State<RedditLoginWebview> {
  late final WebViewController _controller;
  bool _finished = false;

  @override
  void initState() {
    super.initState();

    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          // The redirect never resolves to a real page, so it has to be caught
          // as navigation rather than waited on as a load.
          onNavigationRequest: (request) => _handle(Uri.tryParse(request.url)),
          onUrlChange: (change) {
            final url = change.url;
            if (url != null) {
              _handle(Uri.tryParse(url));
            }
          },
        ),
      )
      ..loadRequest(RedditAuth.authorizeUrl(clientId: widget.clientId, state: widget.state));
  }

  /// Every return from Reddit ends the screen, including the ones that carry
  /// nothing usable.
  ///
  /// A redirect whose state does not match used to be waved through as ordinary
  /// navigation — to a scheme that resolves to no page — so the reader was left
  /// on a blank screen with sign-in neither done nor cancelled.
  NavigationDecision _handle(Uri? uri) {
    if (uri == null || _finished || !RedditAuth.isRedirect(uri)) {
      return NavigationDecision.navigate;
    }

    final code = RedditAuth.codeFrom(uri, expectedState: widget.state);
    if (code == null) {
      _log.warning(
        'Reddit came back without a usable code: '
        '${uri.queryParameters['error'] ?? 'no code, or a state that was not ours'}',
      );
    }

    _close(code);
    return NavigationDecision.prevent;
  }

  void _close(String? code) {
    _finished = true;
    if (mounted) {
      Navigator.of(context).pop(code);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(L10n.of(context).plugin_reddit_sign_in)),
      body: WebViewWidget(controller: _controller),
    );
  }
}
