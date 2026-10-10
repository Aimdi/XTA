import 'dart:convert';
import 'dart:io' show Cookie;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/subscriptions/_import.dart' show SubscriptionImportScreen;
import 'package:webview_cookie_manager_plus/webview_cookie_manager_plus.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';

class TwitterLoginWebview extends StatefulWidget {
  const TwitterLoginWebview({super.key});

  @override
  State<TwitterLoginWebview> createState() => _TwitterLoginWebviewState();
}

class _TwitterLoginWebviewState extends State<TwitterLoginWebview> {
  static const _channel = MethodChannel('browser_resolver');

  // Built once: a controller made in build() was replaced, and the login page
  // reloaded from scratch, whenever this page rebuilt — mid-login when
  // animations are off (QuaX issue #106).
  final _webviewCookieManager = WebviewCookieManager();
  final _webviewController = WebViewController();

  // X can report reaching its home page twice in a row. Handling both would
  // save the account twice and close this page twice, the second time closing
  // the screen under it too.
  bool _loggingIn = false;

  @override
  void initState() {
    super.initState();
    _setUpWebview();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(L10n.of(context).logging_in_xta),
          content: Text(L10n.of(context).logging_in_xta_information),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
              },
              child: Text(L10n.of(context).ok),
            ),
          ],
        ),
      );
    });
  }

  void _setUpWebview() {
    _webviewController.setJavaScriptMode(JavaScriptMode.unrestricted);
    _webviewController.loadRequest(Uri.https("x.com", "i/flow/login"));
    _webviewController.setUserAgent(userAgentHeader.toString());
    _webviewController.setNavigationDelegate(
      NavigationDelegate(onPageStarted: (_) => _enablePopups(), onUrlChange: _onUrlChange),
    );
  }

  /// "Sign in with Google" opens a popup that must keep `window.opener` to hand
  /// its token back; webview_flutter drops such popups, so the Android side
  /// shows them for this WebView (and only this one).
  Future<void> _enablePopups() async {
    final platform = _webviewController.platform;
    if (platform is! AndroidWebViewController) return;
    try {
      await _channel.invokeMethod<void>('enableWebViewPopups', {'webViewId': platform.webViewIdentifier});
    } on PlatformException catch (_) {
      // Without popups the username/password sign-in still works.
    } on MissingPluginException catch (_) {}
  }

  Future<void> _onUrlChange(UrlChange change) async {
    if (change.url != "https://x.com/home" || _loggingIn) return;
    _loggingIn = true;
    final cookies = await _webviewCookieManager.getCookies("https://x.com/i/flow/login");
    final screenName = await _readScreenName();
    if (screenName == "") {
      _loggingIn = false;
      return;
    }

    try {
      await _saveAccount(cookies, screenName);
      if (mounted) {
        await _closeAndOfferImport(screenName);
      }
    } catch (e) {
      throw Exception(e);
    }
  }

  /// X's home page fills in the screen name after the URL changed, and no other
  /// URL change follows, so wait for it.
  Future<String> _readScreenName() async {
    for (var attempt = 0; attempt < 20; attempt++) {
      final result = await _webviewController.runJavaScriptReturningResult(
        "document.documentElement.outerHTML.match(/\"screen_name\":\"([^\"]+)\"/)?.[1] ?? '';",
      );
      final screenName = result.toString().replaceAll('"', '');
      if (screenName != "") {
        return screenName;
      }
      await Future.delayed(const Duration(milliseconds: 500));
    }
    return "";
  }

  Future<void> _saveAccount(List<Cookie> cookies, String screenName) async {
    final expCt0 = RegExp(r'(ct0=(.+?));');
    final RegExpMatch? matchCt0 = expCt0.firstMatch(cookies.toString());
    final csrfToken = matchCt0?.group(2);
    if (csrfToken == null) return;

    final Map<String, String> authHeader = {
      "Cookie": cookies
          .where(
            (cookie) =>
                cookie.name == "guest_id" ||
                cookie.name == "gt" ||
                cookie.name == "att" ||
                cookie.name == "auth_token" ||
                cookie.name == "ct0",
          )
          .map((cookie) => '${cookie.name}=${cookie.value}')
          .join(";"),
      "authorization": bearerToken,
      "x-csrf-token": csrfToken,
    };

    final database = await Repository.writable();
    // Awaited, and the handle left open: this is sqflite's shared
    // instance for the whole app, so closing it here tore down
    // every other query in flight — racing the very insert that
    // stores the account the reader just signed in with.
    await database.insert(
      tableAccounts,
      Account(id: csrfToken, screenName: screenName, authHeader: json.encode(authHeader)).toMap(),
    );
  }

  /// Leaves the login page, then asks about importing. The navigator is taken
  /// before popping: without a page transition the popped page is gone at once,
  /// and its context with it.
  Future<void> _closeAndOfferImport(String screenName) async {
    final navigator = Navigator.of(context);
    navigator.pop();
    await showDialog(
      context: navigator.context,
      builder: (context) => AlertDialog(
        title: Text(L10n.of(context).import_subscriptions),
        content: Text(L10n.of(context).import_subscriptions_text(screenName)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text(L10n.of(context).no)),
          TextButton(
            onPressed: () {
              final dialogNavigator = Navigator.of(context);
              dialogNavigator.pop();
              dialogNavigator.push(MaterialPageRoute(builder: (_) => const SubscriptionImportScreen()));
            },
            child: Text(L10n.of(context).yes),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(toolbarHeight: 50),
      body: SafeArea(top: false, child: WebViewWidget(controller: _webviewController)),
    );
  }
}
