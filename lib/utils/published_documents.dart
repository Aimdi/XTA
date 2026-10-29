/// The documents this repository publishes for installs to read:
/// `endpoints.json`, `plugins.json`, `repairs.json` and the web sources.
library;

import 'package:http/http.dart' as http;
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

/// A document this repository publishes, read at most once per [interval]
/// and kept in preferences, so the last one read applies before (or
/// without) the network.
class PublishedDocument {
  final BasePrefService prefs;
  final Uri url;
  final String keptKey;
  final String readAtKey;
  final Duration interval;
  final http.Client client;

  PublishedDocument(
    this.prefs, {
    required this.url,
    required this.keptKey,
    required this.readAtKey,
    this.interval = const Duration(hours: 24),
    http.Client? client,
  }) : client = client ?? http.Client();

  /// The document last read; null before the first.
  String? get kept => switch (prefs.get<String>(keptKey)) {
    final body? when body.isNotEmpty => body,
    _ => null,
  };

  /// Reads the document again when the last read is older than [interval],
  /// and keeps it. The body read; null when nothing new was: too soon, no
  /// answer, or anything but the document.
  Future<String?> refresh({DateTime? now}) async {
    final at = now ?? DateTime.now();
    final last = DateTime.tryParse(prefs.get<String>(readAtKey) ?? '');
    if (last != null && at.difference(last) < interval) return null;
    try {
      final response = await client.get(url).timeout(const Duration(seconds: 10));
      if (response.statusCode != 200) return null;
      await prefs.set(keptKey, response.body);
      await prefs.set(readAtKey, at.toIso8601String());
      return response.body;
    } on Exception {
      return null;
    }
  }
}
