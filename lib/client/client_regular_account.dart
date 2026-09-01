import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/client/http_client.dart';
import 'dart:async';
import 'package:webview_cookie_manager_plus/webview_cookie_manager_plus.dart';
import 'package:xta/database/repository.dart';

class XRegularAccount extends ChangeNotifier {
  static final log = Logger('XRegularAccount');

  XRegularAccount() : super();

  Future<http.Response> fetch(
    Uri uri, {
    Map<String, String>? headers,
    required Logger log,
    required Map<dynamic, dynamic> authHeader,
  }) async {
    log.info('Fetching $uri');

    final baseHeaders = await TwitterHeaders.getHeaders(uri, authHeader);

    var response = await xHttpClient.get(uri, headers: {...?headers, ...baseHeaders});

    return response;
  }

  /// Forgets an account: its row, and the X session behind it.
  ///
  /// The login webview keeps its own cookie jar, and dropping the row never
  /// touched it — so a signed-out account was still signed in as far as x.com
  /// was concerned, and the next "add account" was handed straight back to the
  /// same session instead of a login form. For a reader who deleted an account
  /// to get it off their device, leaving `auth_token` there is the whole of
  /// what they were trying to undo.
  Future<void> deleteAccount(String username) async {
    var database = await Repository.writable();
    await database.delete(tableAccounts, where: 'id = ?', whereArgs: [username]);

    try {
      await WebviewCookieManager().clearCookies();
    } catch (e) {
      // The row is already gone, which is the part the reader asked for.
      log.warning('Could not clear the webview cookies after deleting an account: $e');
    }
  }
}
