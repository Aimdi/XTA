import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_auth.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// Pixiv's login page, watched for the redirect that carries the code back.
///
/// Pops the authorization code, or null when the reader backs out. The caller
/// trades the code for tokens — this screen never holds a credential.
class PixivLoginWebview extends StatefulWidget {
  final String codeChallenge;

  const PixivLoginWebview({super.key, required this.codeChallenge});

  @override
  State<PixivLoginWebview> createState() => _PixivLoginWebviewState();
}

class _PixivLoginWebviewState extends State<PixivLoginWebview> {
  late final WebViewController _controller;
  bool _finished = false;

  @override
  void initState() {
    super.initState();

    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(NavigationDelegate(
        onNavigationRequest: (request) => _handle(Uri.tryParse(request.url)),
        onUrlChange: (change) {
          final url = change.url;
          if (url != null) {
            _handle(Uri.tryParse(url));
          }
        },
      ))
      ..loadRequest(PixivAuth.loginUri(codeChallenge: widget.codeChallenge));
  }

  NavigationDecision _handle(Uri? uri) {
    if (uri == null || _finished) {
      return NavigationDecision.navigate;
    }

    if (PixivAuth.deniedIn(uri)) {
      _close(null);
      return NavigationDecision.prevent;
    }

    if (uri.scheme == 'pixiv') {
      _close(PixivAuth.codeFrom(uri));
      return NavigationDecision.prevent;
    }

    final code = PixivAuth.codeFrom(uri);
    if (code != null) {
      _close(code);
      return NavigationDecision.prevent;
    }

    return NavigationDecision.navigate;
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
      appBar: AppBar(title: Text(L10n.of(context).plugin_pixiv_sign_in)),
      body: WebViewWidget(controller: _controller),
    );
  }
}
