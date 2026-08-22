/// Decides which plugins the store offers, from a document published in the
/// app's own repository.
///
/// It cannot make the app do anything new: a plugin's code is compiled into
/// the build, and an id the catalogue names that this build has never heard of
/// is simply ignored. What it decides is what is *offered* — so a plugin can be
/// published, held back or withdrawn without cutting a release, and one that
/// was never published never appears.
///
/// It also never takes anything away. A plugin the reader has installed stays
/// installed and stays working whatever the catalogue says, or an unreachable
/// file would silently switch off half the app.
library;

import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// Reads the ids a catalogue document offers, tolerating anything that is not
/// the shape we expect rather than throwing into a screen.
///
/// `available: false` withdraws an entry without deleting it, which is how a
/// plugin is held back while it is being fixed.
List<String> parsePluginCatalogue(String body) {
  final Object? decoded = jsonDecode(body);
  if (decoded is! Map) {
    return const [];
  }

  final plugins = decoded['plugins'];
  if (plugins is! List) {
    return const [];
  }

  return [
    for (final entry in plugins)
      if (entry is Map && entry['id'] is String && entry['available'] != false)
        entry['id'] as String,
  ];
}

/// Every id the document names, including ones held back with `available: false`.
Set<String> parsePluginCatalogueMentioned(String body) {
  final Object? decoded = jsonDecode(body);
  if (decoded is! Map) {
    return const {};
  }
  final plugins = decoded['plugins'];
  if (plugins is! List) {
    return const {};
  }
  return {
    for (final entry in plugins)
      if (entry is Map && entry['id'] is String) entry['id'] as String,
  };
}

/// What this build should offer after a catalogue fetch.
///
/// The published file can only *withdraw* an id it names (`available: false`).
/// An id this APK knows that the document never mentions stays on offer — a
/// stale `main` catalogue used to hide Hacker News the moment it was fetched.
List<String> offeredPluginIds({
  required Iterable<String> builtInIds,
  required List<String> catalogueOffered,
  required Set<String> catalogueMentioned,
}) {
  return [
    for (final id in builtInIds)
      if (catalogueOffered.contains(id) || !catalogueMentioned.contains(id)) id,
  ];
}

class PluginCatalogue {
  final BasePrefService prefs;
  final Logger log;
  final http.Client client;

  PluginCatalogue(this.prefs, {Logger? log, http.Client? client})
    : log = log ?? Logger('PluginCatalogue'),
      client = client ?? http.Client();

  /// The last document that arrived, so the store has something to show before
  /// — or without — a round trip.
  List<String> cached() {
    final cached = prefs.get<String>(optionPluginCatalogueCache) ?? '';
    if (cached.isEmpty) {
      return const [];
    }

    try {
      return parsePluginCatalogue(cached);
    } catch (e) {
      log.warning('Ignoring an unreadable cached plugin catalogue: $e');
      return const [];
    }
  }

  /// True once a catalogue has been read at least once, which is what tells an
  /// empty list apart from "we have never asked".
  bool get hasCache =>
      (prefs.get<String>(optionPluginCatalogueCache) ?? '').isNotEmpty;

  /// Fetches the catalogue and remembers it. Returns null when it could not be
  /// read, leaving whatever was cached in place.
  Future<List<String>?> fetch() async {
    final url =
        prefs.get<String>(optionPluginCatalogueUrl) ??
        defaultPluginCatalogueUrl;
    if (url.isEmpty) {
      return null;
    }

    try {
      final response = await client
          .get(Uri.parse(url))
          .timeout(const Duration(seconds: 10));
      if (response.statusCode != 200) {
        log.warning('The plugin catalogue answered ${response.statusCode}');
        return null;
      }

      final ids = parsePluginCatalogue(response.body);
      await prefs.set(optionPluginCatalogueCache, response.body);
      await prefs.set(
        optionPluginCatalogueFetchedAt,
        DateTime.now().toIso8601String(),
      );
      return ids;
    } catch (e) {
      log.warning('Unable to fetch the plugin catalogue: $e');
      return null;
    }
  }
}
