/// Repairs rotated X query ids without shipping a new APK.
///
/// The registry file is a small JSON document published next to the app's
/// releases; when X rotates an id, publishing a corrected entry fixes every
/// install on its next launch. Nothing here can redirect traffic: only the
/// query-id segment is replaceable, only for operations this build already
/// knows, and only with a value matching [queryIdPattern] — see
/// [XEndpoints.applyOverrides].
library;

import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/constants.dart';

/// Parses a registry document, tolerating anything that is not the shape we
/// expect rather than throwing into app startup.
Map<String, String> parseEndpointRegistry(String body) {
  final Object? decoded = jsonDecode(body);
  if (decoded is! Map) {
    return const {};
  }
  final endpoints = decoded['endpoints'];
  if (endpoints is! Map) {
    return const {};
  }
  return {
    for (final entry in endpoints.entries)
      if (entry.key is String && entry.value is String) entry.key as String: entry.value as String,
  };
}

class EndpointRegistry {
  final BasePrefService prefs;
  final Logger log;
  final http.Client client;

  EndpointRegistry(this.prefs, {Logger? log, http.Client? client})
    : log = log ?? Logger('EndpointRegistry'),
      client = client ?? http.Client();

  /// Applies the last payload we stored, so a cold start is already repaired
  /// before (or without) any network round trip.
  int applyCached() {
    final cached = prefs.get<String>(optionEndpointRegistryCache) ?? '';
    if (cached.isEmpty) {
      return 0;
    }
    try {
      return XEndpoints.applyOverrides(parseEndpointRegistry(cached));
    } catch (e) {
      log.warning('Ignoring unreadable cached endpoint registry: $e');
      return 0;
    }
  }

  /// Fetches the registry and applies it. Failure is not an error worth
  /// surfacing: the shipped ids stay in force and the app works as it did.
  Future<int> refresh() async {
    if (prefs.get<bool>(optionEndpointRegistryEnabled) == false) {
      return 0;
    }

    final url = (prefs.get<String>(optionEndpointRegistryUrl) ?? '').trim();
    if (url.isEmpty) {
      return 0;
    }

    final uri = Uri.tryParse(url);
    if (uri == null || !uri.isScheme('https')) {
      log.warning('Endpoint registry URL must be https, ignoring "$url"');
      return 0;
    }

    // Once a day is plenty for a file that changes when X rotates a query id.
    // fetchedAt was recorded on every fetch and read by nothing; now it is the
    // throttle. The cached document has already been applied at startup.
    final fetchedAt = DateTime.tryParse(prefs.get<String>(optionEndpointRegistryFetchedAt) ?? '');
    if (fetchedAt != null && DateTime.now().difference(fetchedAt) < const Duration(hours: 24)) {
      return 0;
    }

    try {
      final response = await client.get(uri).timeout(endpointRegistryTimeout);
      if (response.statusCode != 200) {
        log.info('Endpoint registry returned ${response.statusCode}, keeping shipped query ids');
        return 0;
      }

      final ids = parseEndpointRegistry(response.body);
      final applied = XEndpoints.applyOverrides(ids);
      if (applied != ids.length) {
        log.warning('Endpoint registry: ${ids.length - applied} of ${ids.length} entries rejected');
      }

      await prefs.set(optionEndpointRegistryCache, response.body);
      await prefs.set(optionEndpointRegistryFetchedAt, DateTime.now().toIso8601String());
      return applied;
    } catch (e) {
      log.info('Could not refresh the endpoint registry, keeping shipped query ids: $e');
      return 0;
    }
  }
}
