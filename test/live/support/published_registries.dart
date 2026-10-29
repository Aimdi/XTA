/// The `endpoints.json` files installs read, fetched as they fetch them.
library;

import 'package:http/http.dart' as http;
import 'package:xta/client/endpoint_overrides.dart';
import 'package:xta/constants.dart';

const currentBuilds = 'builds of this repo';
const olderInstalls = 'installs up to aimdi186';

/// Where each kind of install reads `endpoints.json`: installs up to
/// aimdi186 saved the `main` branch's file as their default.
final publishedRegistryUrls = {
  currentBuilds: Uri.parse(defaultEndpointRegistryUrl),
  olderInstalls: Uri.parse('https://raw.githubusercontent.com/$githubRepo/main/endpoints.json'),
};

/// The published file at [uri] as an install reads it; null when it cannot
/// be read.
Future<Map<String, String>?> fetchPublishedRegistry(http.Client client, Uri uri) async {
  try {
    final response = await client.get(uri);
    return response.statusCode == 200 ? parseEndpointRegistry(response.body) : null;
  } catch (_) {
    return null;
  }
}

/// Every file of [publishedRegistryUrls], by who reads it.
Future<Map<String, Map<String, String>?>> fetchPublishedRegistries(http.Client client) async => {
  for (final MapEntry(key: who, value: uri) in publishedRegistryUrls.entries)
    who: await fetchPublishedRegistry(client, uri),
};
