import 'dart:convert';
import 'dart:io' show Cookie;

import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';

/// Stores the X session in [cookies] as an account named [screenName]. Nothing
/// is stored without a `ct0` cookie, which doubles as the account's id.
Future<void> saveXAccount(List<Cookie> cookies, String screenName) async {
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
