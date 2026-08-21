import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';

/// Live smoke against Garbage Day's leftover custom domain.
/// Opt in with:
///   fvm flutter test test/live/substack_custom_domain_smoke_test.dart --dart-define=RUN_LIVE=true
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'www.garbageday.email resolves to Garbage Day with public posts',
    () async {
      final client = SubstackClient();
      final pub = await client.fetchPublication(
        Uri.parse('https://www.garbageday.email'),
      );

      expect(pub.name, 'Garbage Day');
      expect(pub.subdomain, 'garbageday');
      expect(publicationNameLooksGeneric(pub.name), isFalse);
      expect(pub.logoUrl, isNotNull);

      final posts = await client.fetchPosts(
        SubstackPublication(
          subdomain: 'www',
          baseUrl: 'https://www.garbageday.email',
          name: 'www',
        ),
      );
      expect(
        posts,
        isNotEmpty,
        reason: 'custom-domain follow must not look empty',
      );
      expect(posts.first.title, isNotEmpty);
    },
    skip: enabled ? false : 'Set RUN_LIVE=true to hit live Substack hosts',
  );
}
