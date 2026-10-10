import 'dart:async';

import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';
import 'package:xta/links/link_browser_store.dart';

/// Decides whether a link the page navigates to is opened by an XTA screen
/// instead; true means it was, and the page stays where it is.
typedef LinkNavigationGuard = Future<bool> Function(String url);

/// The page the browser screen shows: a webview in the app, a stand-in in
/// tests, where no platform webview exists.
abstract class LinkBrowserPage {
  Widget build(BuildContext context);

  Future<void> load(String url);

  Future<void> reload();

  /// Steps back in the page's own history; false when there is nothing to
  /// go back to and the screen should close instead.
  Future<bool> back();
}

typedef LinkBrowserPageFactory = LinkBrowserPage Function(LinkBrowserStore store, LinkNavigationGuard guard);

LinkBrowserPage webViewLinkBrowserPage(LinkBrowserStore store, LinkNavigationGuard guard) =>
    _WebViewLinkBrowserPage(store, guard);

class _WebViewLinkBrowserPage implements LinkBrowserPage {
  final WebViewController _controller = WebViewController();

  _WebViewLinkBrowserPage(LinkBrowserStore store, LinkNavigationGuard guard) {
    _controller
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: store.started,
          onProgress: store.progressed,
          onPageFinished: store.finished,
          onWebResourceError: (error) {
            if (error.isForMainFrame ?? true) store.stopped();
          },
          onNavigationRequest: (request) => _decide(request.url, guard),
        ),
      );
    unawaited(_refuseThirdPartyCookies());
  }

  /// Android's default already, but stated: an article does not get to set
  /// cookies for the ad networks it embeds.
  Future<void> _refuseThirdPartyCookies() async {
    final cookies = WebViewCookieManager().platform;
    final platform = _controller.platform;
    if (cookies is AndroidWebViewCookieManager && platform is AndroidWebViewController) {
      try {
        await cookies.setAcceptThirdPartyCookies(platform, false);
      } catch (_) {
        // The page still loads; the platform default applies.
      }
    }
  }

  @override
  Widget build(BuildContext context) => WebViewWidget(controller: _controller);

  @override
  Future<void> load(String url) => _controller.loadRequest(Uri.parse(url));

  @override
  Future<void> reload() => _controller.reload();

  @override
  Future<bool> back() async {
    if (!await _controller.canGoBack()) return false;
    await _controller.goBack();
    return true;
  }
}

Future<NavigationDecision> _decide(String url, LinkNavigationGuard guard) async {
  final scheme = Uri.tryParse(url)?.scheme;
  if (scheme == 'http' || scheme == 'https') {
    return await guard(url) ? NavigationDecision.prevent : NavigationDecision.navigate;
  }
  return switch (scheme) {
    'about' || 'data' || 'blob' => NavigationDecision.navigate,
    _ => NavigationDecision.prevent,
  };
}
