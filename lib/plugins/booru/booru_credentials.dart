/// The reader's booru login and key, and the host they belong to.
///
/// One pair is kept, for the host it was entered for, and no other host is
/// sent it: a key typed for one site never reaches the next one chosen.
library;

import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_engines.dart';

/// The host requests go to: the configured one, else the first preset.
String booruHostOf(BasePrefService prefs) {
  final custom = normaliseBooruHost(prefs.get<String>(optionPluginBooruHost) ?? '');
  return custom.isNotEmpty ? custom : booruPresets.first.host;
}

/// The host the saved login and key belong to; empty when there is none.
String booruCredentialsHost(BasePrefService prefs) =>
    normaliseBooruHost(prefs.get<String>(optionPluginBooruCredentialsHost) ?? '');

bool _hasCredentials(BasePrefService prefs) =>
    [optionPluginBooruLogin, optionPluginBooruApiKey].any((key) => (prefs.get<String>(key) ?? '').trim().isNotEmpty);

/// The saved login or key ([key]) when it belongs to [host] (the host in use
/// by default), else empty.
String booruCredential(BasePrefService prefs, String key, {String? host}) =>
    booruCredentialsHost(prefs) == (host ?? booruHostOf(prefs)) ? prefs.get<String>(key) ?? '' : '';

/// The host saved credentials belong to when it is not the host in use; null
/// when there are none, or they are this host's.
String? booruCredentialsElsewhere(BasePrefService prefs) {
  final owner = booruCredentialsHost(prefs);
  return _hasCredentials(prefs) && owner.isNotEmpty && owner != booruHostOf(prefs) ? owner : null;
}

/// Saves [value] as the login or key ([key]) for the host in use. A pair saved
/// for another host is dropped first, so its other half cannot follow.
Future<void> saveBooruCredential(BasePrefService prefs, String key, String value) async {
  final host = booruHostOf(prefs);
  if (booruCredentialsHost(prefs) != host) {
    await prefs.set(optionPluginBooruLogin, '');
    await prefs.set(optionPluginBooruApiKey, '');
  }
  await prefs.set(key, value);
  await prefs.set(optionPluginBooruCredentialsHost, host);
}

/// Credentials saved before they were tied to a host belong to the host in
/// use, which is where they have been going.
Future<void> migrateBooruCredentialsHost(BasePrefService prefs) async {
  if (booruCredentialsHost(prefs).isNotEmpty || !_hasCredentials(prefs)) return;
  await prefs.set(optionPluginBooruCredentialsHost, booruHostOf(prefs));
}
