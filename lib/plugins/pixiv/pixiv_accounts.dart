import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';
import 'package:xta/utils/json.dart';

/// The accounts stored under [optionPluginPixivAccounts], each user once.
List<PixivAccount> readPixivAccounts(String? raw) {
  final ids = <int>{};
  try {
    return List.unmodifiable([
      for (final item in Json(jsonDecode(raw ?? '[]')).list)
        if (PixivAccount.fromJson(item) case final account? when ids.add(account.userId)) account,
    ]);
  } on FormatException {
    return const [];
  }
}

/// [accounts] with [account] in its place, or added last when new.
List<PixivAccount> pixivAccountsWith(List<PixivAccount> accounts, PixivAccount account) {
  final known = accounts.any((old) => old.userId == account.userId);
  return List.unmodifiable([
    for (final old in accounts) old.userId == account.userId ? account : old,
    if (!known) account,
  ]);
}

List<PixivAccount> pixivAccountsWithout(List<PixivAccount> accounts, int userId) =>
    List.unmodifiable(accounts.where((account) => account.userId != userId));

String _currentToken(BasePrefService prefs) => (prefs.get<String>(optionPluginPixivRefreshToken) ?? '').trim();

List<PixivAccount> _stored(BasePrefService prefs) => readPixivAccounts(prefs.get<String>(optionPluginPixivAccounts));

Future<List<PixivAccount>> _store(BasePrefService prefs, List<PixivAccount> accounts) async {
  await prefs.set(optionPluginPixivAccounts, jsonEncode([for (final account in accounts) account.toJson()]));
  return accounts;
}

/// Keeps [user] — just signed in, or confirmed by a token check — with the
/// refresh token now in use.
Future<List<PixivAccount>> rememberPixivAccount(BasePrefService prefs, PixivAuthUser user) async {
  final accounts = _stored(prefs);
  final token = _currentToken(prefs);
  if (user.id <= 0 || token.isEmpty) return accounts;
  return _store(prefs, pixivAccountsWith(accounts, PixivAccount.of(user, token)));
}

/// Copies the token now in use into the active account's entry: Pixiv may
/// have rotated it since that account was stored.
Future<List<PixivAccount>> keepActivePixivToken(BasePrefService prefs) async {
  final accounts = _stored(prefs);
  final activeId = prefs.get<int>(optionPluginPixivUserId) ?? 0;
  final active = accounts.where((account) => account.userId == activeId).firstOrNull;
  final token = _currentToken(prefs);
  if (active == null || token.isEmpty || token == active.refreshToken) return accounts;
  return _store(prefs, pixivAccountsWith(accounts, active.withRefreshToken(token)));
}

/// Every Pixiv account signed in on this device. The active one is the account
/// [PixivClient] uses; switching copies another into its keys.
///
/// Everything lives in preferences, so each screen keeps its own store and
/// sees what another one changed after [load].
class PixivAccountsStore extends Store<List<PixivAccount>> {
  final PixivClient client;

  PixivAccountsStore(this.client) : super(const []);

  BasePrefService get _prefs => client.prefs;

  int? get activeId => client.storedUserId;

  bool get signedIn => _currentToken(_prefs).isNotEmpty;

  PixivAccount? get active => state.where((account) => account.userId == activeId).firstOrNull;

  void load() => update(_stored(_prefs));

  Future<void> remember(PixivAuthUser user) async => update(await rememberPixivAccount(_prefs, user));

  Future<void> switchTo(PixivAccount account) async {
    final accounts = await keepActivePixivToken(_prefs);
    update(accounts);
    await client.switchTo(accounts.where((known) => known.userId == account.userId).firstOrNull ?? account);
  }

  /// Forgets an account that is not the active one.
  Future<void> remove(PixivAccount account) async =>
      update(await _store(_prefs, pixivAccountsWithout(_stored(_prefs), account.userId)));

  /// Signs the active account out and forgets it. Another stored account takes
  /// over when there is one; it is returned.
  Future<PixivAccount?> signOutActive() async {
    final id = activeId;
    final rest = await _store(_prefs, id == null ? _stored(_prefs) : pixivAccountsWithout(_stored(_prefs), id));
    update(rest);
    if (rest.isEmpty) {
      await client.signOut();
      return null;
    }
    await client.switchTo(rest.first);
    return rest.first;
  }
}

/// What was loaded for the account in use: its feed, the bookmark marks and
/// the follows made this session. Read now, so the returned call still works
/// after the awaits of a switch.
VoidCallback pixivAccountDataForgetter(BuildContext context) {
  final feed = context.read<PixivFeedStore?>();
  final bookmarks = context.read<PixivBookmarkStore?>();
  final follows = context.read<PixivFollowStore?>();
  return () {
    feed?.update(const []);
    bookmarks?.update(const {});
    follows?.clear();
  };
}
