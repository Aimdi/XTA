import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';

SubstackPublication pub(String id, String name) => SubstackPublication(
      subdomain: id,
      baseUrl: 'https://$id.substack.com',
      name: name,
    );

void main() {
  test('pinned publications sort ahead of the A–Z rest', () {
    final publications = [
      pub('alpha', 'Alpha'),
      pub('beta', 'Beta'),
      pub('zeta', 'Zeta'),
    ];

    expect(
      sortSubstackPublicationsWithPins(publications, ['zeta', 'alpha'])
          .map((p) => p.id)
          .toList(),
      ['zeta', 'alpha', 'beta'],
    );
  });

  test('unknown pin ids are skipped', () {
    final publications = [pub('alpha', 'Alpha')];
    expect(
      sortSubstackPublicationsWithPins(publications, ['missing', 'alpha'])
          .map((p) => p.id)
          .toList(),
      ['alpha'],
    );
  });

  test('empty pins leave the list alone', () {
    final publications = [pub('beta', 'Beta'), pub('alpha', 'Alpha')];
    expect(
      sortSubstackPublicationsWithPins(publications, const []),
      publications,
    );
  });
}
