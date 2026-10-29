/// The web sources this repository publishes (`sources.json`), for the Add
/// source screen. Reading the list installs nothing.
library;

import 'dart:convert';

import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/utils/json.dart';

/// One published source: enough to list it before its file is fetched.
class WebSourceListing {
  final String id;
  final String name;
  final String description;
  final Uri url;

  const WebSourceListing({required this.id, required this.name, required this.description, required this.url});
}

/// The entries of [body], a published list read from [base]. An entry
/// that is malformed, or whose file is not on HTTPS, is left out.
List<WebSourceListing> parseWebSourceCatalogue(String body, Uri base) {
  final Object? decoded;
  try {
    decoded = jsonDecode(body);
  } on FormatException {
    return const [];
  }
  return [for (final entry in Json(decoded)['sources'].list) ?_listing(entry, base)];
}

WebSourceListing? _listing(Json entry, Uri base) {
  final id = entry['id'].string;
  final name = entry['name'].string?.trim() ?? '';
  final address = Uri.tryParse(entry['url'].string ?? '');
  if (!isWebSourceId(id) || name.isEmpty || address == null) return null;
  final url = base.resolveUri(address);
  if (!url.isScheme('https') || url.userInfo.isNotEmpty) return null;
  return WebSourceListing(id: id!, name: name, description: entry['description'].string?.trim() ?? '', url: url);
}
