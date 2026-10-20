/// Each booru site's own maximum rating, blacklist, login and key.
///
/// The settings in use are always the site in use's: the client and the
/// settings screen read them as before. Switching site stores them under
/// the old site's host and brings back the new one's. Followed tags stay
/// shared by every site.
library;

import 'dart:convert';

import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_credentials.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/utils/json.dart';

/// Stores the settings in use under [from] and brings back [to]'s own.
/// Returns whether [to] had settings of its own. A site seen for the first
/// time keeps the blacklist in use and gets no login; the caller picks its
/// rating cap. Staying on a site swaps nothing.
Future<bool> swapBooruSiteProfile(BasePrefService prefs, {required String from, required String to}) async {
  if (from == to) return false;
  await _keep(prefs, from);
  final settings = Json(_read(prefs, optionPluginBooruSiteSettings)[to]);
  final login = Json(_read(prefs, optionPluginBooruSiteCredentials)[to]);
  if (login.exists) {
    await prefs.set(optionPluginBooruLogin, login['login'].string ?? '');
    await prefs.set(optionPluginBooruApiKey, login['key'].string ?? '');
    await prefs.set(optionPluginBooruCredentialsHost, to);
  }
  if (!settings.exists) return false;
  await prefs.set(optionPluginBooruMaxRating, settings['rating'].string ?? BooruRating.general.code);
  await prefs.set(optionPluginBooruMutedTags, settings['muted'].string ?? '[]');
  return true;
}

/// Drops what [host] kept, for a site that is removed.
Future<void> forgetBooruSiteProfile(BasePrefService prefs, String host) async {
  for (final key in [optionPluginBooruSiteSettings, optionPluginBooruSiteCredentials]) {
    await prefs.set(key, jsonEncode(_read(prefs, key)..remove(host)));
  }
}

/// Stores the settings in use under [host]; its login only while it has one.
Future<void> _keep(BasePrefService prefs, String host) async {
  final settings = _read(prefs, optionPluginBooruSiteSettings)
    ..[host] = {
      'rating': (BooruRating.tryParse(prefs.get<String>(optionPluginBooruMaxRating)) ?? BooruRating.general).code,
      'muted': prefs.get<String>(optionPluginBooruMutedTags) ?? '[]',
    };
  final login = booruCredential(prefs, optionPluginBooruLogin, host: host);
  final key = booruCredential(prefs, optionPluginBooruApiKey, host: host);
  final logins = _read(prefs, optionPluginBooruSiteCredentials);
  if (login.isEmpty && key.isEmpty) {
    logins.remove(host);
  } else {
    logins[host] = {'login': login, 'key': key};
  }
  await prefs.set(optionPluginBooruSiteSettings, jsonEncode(settings));
  await prefs.set(optionPluginBooruSiteCredentials, jsonEncode(logins));
}

Map<String, Object?> _read(BasePrefService prefs, String key) {
  try {
    final decoded = jsonDecode(prefs.get<String>(key) ?? '{}');
    if (decoded is! Map) return {};
    return {
      for (final MapEntry(key: host, :value) in decoded.entries)
        if (host is String) host: value,
    };
  } catch (_) {
    return {};
  }
}
