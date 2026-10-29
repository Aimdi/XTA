/// The documents this repository publishes for installs to read:
/// `endpoints.json` and `plugins.json`.
library;

import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// Branches earlier releases read the published documents from.
const _formerPublishBranches = ['master', 'main'];

/// Whether [stored] is the address an earlier release saved as its default
/// for [current], a document on [githubPublishBranch].
bool isFormerPublishedDefault(String stored, String current) => _formerPublishBranches.any(
  (branch) => stored.trim() == current.replaceFirst('/$githubPublishBranch/', '/$branch/'),
);

/// Points an install that saved an earlier release's default address at
/// today's. The query ids read from the old address are forgotten, so the
/// shipped ones apply until the new one is read. An address set by hand stays.
Future<void> migratePublishedDocumentUrls(BasePrefService prefs) async {
  if (await _repoint(prefs, optionEndpointRegistryUrl, defaultEndpointRegistryUrl)) {
    await prefs.set(optionEndpointRegistryCache, '');
    await prefs.set(optionEndpointRegistryFetchedAt, '');
  }
  await _repoint(prefs, optionPluginCatalogueUrl, defaultPluginCatalogueUrl);
}

Future<bool> _repoint(BasePrefService prefs, String key, String current) async {
  final stored = prefs.get<String>(key);
  if (stored == null || !isFormerPublishedDefault(stored, current)) return false;
  await prefs.set(key, current);
  return true;
}
