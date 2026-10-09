import 'dart:convert';
import 'dart:io' show Cookie;

import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/subscriptions/_import.dart' show SubscriptionImportScreen;
import 'package:webview_cookie_manager_plus/webview_cookie_manager_plus.dart';
import 'package:webview_flutter/webview_flutter.dart';

class TwitterLoginWebview extends StatefulWidget {
  const TwitterLoginWebview({super.key});

  @override
  State<TwitterLoginWebview> createState() => _TwitterLoginWebviewState();
}

class _TwitterLoginWebviewState extends State<TwitterLoginWebview> {
  // Built once: a controller made in build() was replaced, and the login page
  // reloaded from scratch, whenever this page rebuilt — mid-login when
  // animations are off (QuaX issue #106).
  final _webviewCookieManager = WebviewCookieManager();
  final _webviewController = WebViewController();

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
    _webviewController.setNavigationDelegate(NavigationDelegate(onUrlChange: _onUrlChange));
  }

  Future<void> _onUrlChange(UrlChange change) async {
    if (change.url != "https://x.com/home") return;
    final cookies = await _webviewCookieManager.getCookies("https://x.com/i/flow/login");
    final screenName = (await _webviewController.runJavaScriptReturningResult(
      "document.documentElement.outerHTML.match(/\"screen_name\":\"([^\"]+)\"/)?.[1] ?? '';",
    )).toString().replaceAll('"', '');
    if (screenName == "") return;

    try {
      await _saveAccount(cookies, screenName);
      if (mounted) {
        await _closeAndOfferImport(screenName);
      }
    } catch (e) {
      throw Exception(e);
    }
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
      body: WebViewWidget(controller: _webviewController),
    );
  }
}
