import 'dart:async';
import 'dart:io' show Cookie;

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:xta/client/login_webview.dart';
import 'package:xta/client/x_session.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/desktop.dart';
import 'package:xta/utils/reader_value_store.dart';

/// The page that adds an X account: X's own login in a web view, or, on the
/// desktop, which has none, the session cookies copied from a browser.
Widget xLoginScreen() => isDesktop ? const XCookieLogin() : const TwitterLoginWebview();

/// The `auth_token` and `ct0` cookies in [text] — a browser's `Cookie` header,
/// or the two values pasted one per line — or null unless both are there.
List<Cookie>? parseXSessionCookies(String text) {
  final pairs = {
    for (final part in text.split(RegExp(r'[;\n]')))
      if (part.contains('=')) part.substring(0, part.indexOf('=')).trim(): part.substring(part.indexOf('=') + 1).trim(),
  };
  final authToken = pairs['auth_token'] ?? '';
  final ct0 = pairs['ct0'] ?? '';
  if (authToken.isEmpty || ct0.isEmpty) return null;
  return [Cookie('auth_token', authToken), Cookie('ct0', ct0)];
}

/// The screen name typed with or without its `@`.
String normalizeScreenName(String text) => text.trim().replaceFirst(RegExp(r'^@'), '');

class XCookieLogin extends StatefulWidget {
  const XCookieLogin({super.key});

  @override
  State<XCookieLogin> createState() => _XCookieLoginState();
}

class _XCookieLoginState extends State<XCookieLogin> {
  final _screenName = TextEditingController();
  final _cookies = TextEditingController();
  final _saving = ReaderValueStore<bool>(false);

  bool get _ready => normalizeScreenName(_screenName.text).isNotEmpty && parseXSessionCookies(_cookies.text) != null;

  Future<void> _save() async {
    final cookies = parseXSessionCookies(_cookies.text);
    if (cookies == null || _saving.state) return;
    final navigator = Navigator.of(context);
    _saving.update(true);
    try {
      await saveXAccount(cookies, normalizeScreenName(_screenName.text));
      navigator.pop();
    } finally {
      _saving.update(false);
    }
  }

  @override
  void dispose() {
    _screenName.dispose();
    _cookies.dispose();
    _saving.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.add_account)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.x_cookie_login_help),
          const SizedBox(height: 8),
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: TextButton.icon(
              icon: const Icon(Icons.open_in_browser),
              label: Text(l10n.open_in_browser),
              onPressed: () => unawaited(launchUrl(Uri.https('x.com', 'login'))),
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _screenName,
            autocorrect: false,
            decoration: InputDecoration(labelText: l10n.username, prefixText: '@'),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _cookies,
            autocorrect: false,
            minLines: 2,
            maxLines: 4,
            decoration: InputDecoration(labelText: l10n.x_cookie_login_field),
          ),
          const SizedBox(height: 24),
          _saveButton(l10n),
        ],
      ),
    );
  }

  Widget _saveButton(L10n l10n) => ListenableBuilder(
    listenable: Listenable.merge([_screenName, _cookies]),
    builder: (context, _) => ScopedBuilder<ReaderValueStore<bool>, bool>(
      store: _saving,
      onState: (context, saving) =>
          FilledButton(onPressed: _ready && !saving ? () => unawaited(_save()) : null, child: Text(l10n.save)),
    ),
  );
}
