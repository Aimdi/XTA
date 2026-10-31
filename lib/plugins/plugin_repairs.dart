/// Values a network needs that the network itself may change: request ids,
/// app ids and versions, client ids and user agents. Each is named once here
/// with the value this build ships; `repairs.json` can replace one with a
/// value of the same kind, so a change on a service is repaired without a
/// release (docs/specs/published-repairs.md).
///
/// Nothing here can send a request somewhere else: no host, address or path
/// is a slot, and every value is checked against its kind before it is used.
library;

import 'dart:convert';

import 'package:xta/utils/json.dart';

enum RepairKind {
  /// Request document ids, app ids.
  digits,

  /// Request names, app client ids: letters, digits, `_` and `-`.
  token,

  /// App versions: numbers joined by dots.
  version,

  /// Header text such as a user agent: printable ASCII on one line.
  header,
}

final _shapes = {
  RepairKind.digits: RegExp(r'^[0-9]{1,32}$'),
  RepairKind.token: RegExp(r'^[A-Za-z0-9_-]{1,128}$'),
  RepairKind.version: RegExp(r'^[0-9]{1,6}(?:\.[0-9]{1,6}){0,5}$'),
  RepairKind.header: RegExp(r'^[\x21-\x7E](?:[\x20-\x7E]{0,398}[\x21-\x7E])?$'),
};

/// Whether [value] may stand for a slot of [kind].
bool fitsRepairKind(RepairKind kind, String value) => _shapes[kind]!.hasMatch(value);

class RepairSlot {
  final String key;
  final String shipped;
  final RepairKind kind;

  const RepairSlot(this.key, this.shipped, this.kind);

  /// The published value when one fits, else the one this build ships.
  String get value => PluginRepairs.inForce(this);
}

abstract final class PluginRepairs {
  static const threadsGuestDocId = RepairSlot('threads.guest_doc_id', '6232751443445612', RepairKind.digits);
  static const threadsGuestQueryName = RepairSlot(
    'threads.guest_query_name',
    'BarcelonaProfileThreadsTabQuery',
    RepairKind.token,
  );
  static const threadsAppId = RepairSlot('threads.app_id', '238260118697367', RepairKind.digits);
  static const threadsAsbdId = RepairSlot('threads.asbd_id', '129477', RepairKind.digits);
  static const threadsAppUserAgent = RepairSlot(
    'threads.app_user_agent',
    'Barcelona 289.0.0.77.109 Android',
    RepairKind.header,
  );
  static const threadsWebUserAgent = RepairSlot(
    'threads.web_user_agent',
    'Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) '
        'Version/16.6 Mobile/15E148 Safari/604.1',
    RepairKind.header,
  );
  static const instagramAppId = RepairSlot('instagram.app_id', '936619743392459', RepairKind.digits);
  static const instagramUserAgent = RepairSlot('instagram.user_agent', _desktopChrome, RepairKind.header);
  static const tiktokAid = RepairSlot('tiktok.aid', '1988', RepairKind.digits);
  static const tiktokUserAgent = RepairSlot('tiktok.user_agent', _desktopChrome, RepairKind.header);
  static const pixivAppVersion = RepairSlot('pixiv.app_version', '5.0.234', RepairKind.version);
  static const pixivUserAgent = RepairSlot(
    'pixiv.user_agent',
    'PixivAndroidApp/5.0.234 (Android 11; Pixel 5)',
    RepairKind.header,
  );
  static const pixivClientId = RepairSlot('pixiv.client_id', 'MOBrBDS8blbauoSck0ZfDbtuzpyT', RepairKind.token);
  static const pixivClientSecret = RepairSlot(
    'pixiv.client_secret',
    'lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj',
    RepairKind.token,
  );
  static const redditWebUserAgent = RepairSlot(
    'reddit.web_user_agent',
    'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 '
        'Mobile Safari/537.36',
    RepairKind.header,
  );
  static const stocksUserAgent = RepairSlot('stocks.user_agent', 'Mozilla/5.0 (Android) XTA', RepairKind.header);

  static const _desktopChrome =
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';

  static const all = [
    threadsGuestDocId,
    threadsGuestQueryName,
    threadsAppId,
    threadsAsbdId,
    threadsAppUserAgent,
    threadsWebUserAgent,
    instagramAppId,
    instagramUserAgent,
    tiktokAid,
    tiktokUserAgent,
    pixivAppVersion,
    pixivUserAgent,
    pixivClientId,
    pixivClientSecret,
    redditWebUserAgent,
    stocksUserAgent,
  ];

  static Map<String, String> _applied = const {};

  /// The published values in force, by key.
  static Map<String, String> get applied => Map.unmodifiable(_applied);

  /// Puts [published]'s values in force, in place of any before. An entry
  /// for a key this build does not know, or that does not fit its slot's
  /// kind, changes nothing. How many were taken.
  static int apply(Map<String, String> published) {
    final slots = {for (final slot in all) slot.key: slot};
    _applied = {
      for (final MapEntry(:key, :value) in published.entries)
        if (slots[key] case final slot? when fitsRepairKind(slot.kind, value)) key: value,
    };
    return _applied.length;
  }

  static String inForce(RepairSlot slot) => _applied[slot.key] ?? slot.shipped;

  static void clear() => _applied = const {};
}

/// The entries of [body], a published repairs file; none when it does not
/// read as one.
Map<String, String> parsePluginRepairs(String body) {
  final Object? decoded;
  try {
    decoded = jsonDecode(body);
  } on FormatException {
    return const {};
  }
  final repairs = Json(decoded)['repairs'].raw;
  if (repairs is! Map) return const {};
  return {
    for (final MapEntry(:key, :value) in repairs.entries)
      if (key is String && value is String) key: value,
  };
}
