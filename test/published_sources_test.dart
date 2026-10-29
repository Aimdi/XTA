import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/websources/web_source_catalogue.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';

/// The published list and every source it names must be what installs can
/// read: a file that broke a rule would be refused by every install.
void main() {
  final catalogueUrl = Uri.parse(defaultWebSourceCatalogueUrl);
  final listings = parseWebSourceCatalogue(File('sources.json').readAsStringSync(), catalogueUrl);

  test('the published list names sources', () {
    expect(listings, isNotEmpty);
    expect(listings.map((listing) => listing.id).toSet(), hasLength(listings.length));
  });

  for (final listing in listings) {
    test('${listing.id} is a valid source, published next to the list', () {
      final path = listing.url.path.split('/$githubPublishBranch/').last;
      final file = readWebSource(File(path).readAsStringSync());

      expect(file, isA<ReadWebSource>(), reason: file is RefusedWebSource ? '${file.refusal}: ${file.detail}' : '');
      final source = (file as ReadWebSource).source;
      expect(source.id, listing.id);
      expect(source.name, listing.name);
      expect(source.description, listing.description);
    });
  }
}
