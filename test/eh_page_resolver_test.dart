import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_page_resolver.dart';

import 'support/eh_reader_harness.dart';

List<EhPreview> _pages(int first, int last) => [for (var page = first; page <= last; page++) EhFakeSite.preview(page)];

EhPageResolver _resolver(EhFakeSite site, {List<EhPreview> previews = const []}) {
  final resolver = EhPageResolver(
    client: EhClient(PrefServiceCache(), httpClient: site.client),
    gallery: site.gallery,
    total: site.total,
    previews: previews,
  );
  addTearDown(resolver.destroy);
  return resolver;
}

void main() {
  group('ehSheetSizeFrom', () {
    test('a full first sheet gives its own length', () {
      expect(ehSheetSizeFrom(sheet: 0, previews: _pages(1, 40), total: 100), 40);
      expect(ehSheetSizeFrom(sheet: 0, previews: _pages(1, 20), total: 100), 20);
    });

    test('a later sheet gives it by the page it starts at', () {
      expect(ehSheetSizeFrom(sheet: 2, previews: _pages(81, 100), total: 100), 40);
      expect(ehSheetSizeFrom(sheet: 3, previews: _pages(61, 80), total: 100), 20);
    });

    test('a single sheet, an empty one or one that does not line up says nothing', () {
      expect(ehSheetSizeFrom(sheet: 0, previews: _pages(1, 12), total: 12), isNull);
      expect(ehSheetSizeFrom(sheet: 1, previews: const [], total: 50), isNull);
      expect(ehSheetSizeFrom(sheet: 3, previews: _pages(81, 100), total: 100), isNull);
    });

    test('only the unbroken run from page 1 counts as the first sheets', () {
      final run = ehLeadingRun([..._pages(1, 3), ..._pages(5, 9)]);
      expect(run.map((preview) => preview.page), [1, 2, 3]);
      expect(ehLeadingRun(_pages(2, 5)), isEmpty);
    });
  });

  group('EhPageResolver', () {
    test('fetches the sheet a page sits on, sized from the previews it was given', () async {
      final site = EhFakeSite(total: 100, sheetSize: 40);
      final resolver = _resolver(site, previews: _pages(1, 40));

      final image = await resolver.resolve(50);

      expect(image?.imageUrl, 'https://a.hath.example/h/50/050.webp');
      expect(site.sheetRequests.map((uri) => uri.queryParameters['p']), ['1']);
      expect(resolver.state[50], isA<EhPageReady>());
    });

    test('a wrong guess at the sheet size corrects itself from the sheet it got', () async {
      final site = EhFakeSite(total: 100, sheetSize: 40);
      final resolver = _resolver(site);

      final image = await resolver.resolve(50);

      expect(image, isNotNull);
      expect(site.sheetRequests.map((uri) => uri.queryParameters['p']), ['2', '1']);
      await resolver.resolve(60);
      expect(site.sheetRequests, hasLength(2), reason: 'the second sheet came back with page 60 on it');
    });

    test('caches pages and shares a request already in flight', () async {
      final site = EhFakeSite();
      final resolver = _resolver(site, previews: _pages(1, 20));

      final both = await Future.wait([resolver.resolve(3), resolver.resolve(3)]);
      await resolver.resolve(3);

      expect(identical(both.first, both.last), isTrue);
      expect(site.imageRequests(3), hasLength(1));
      expect(site.sheetRequests, isEmpty);
    });

    test('reading on follows the next-page link instead of a preview sheet', () async {
      final site = EhFakeSite(total: 60);
      final resolver = _resolver(site, previews: [EhFakeSite.preview(45)]);

      await resolver.resolve(45);
      final next = await resolver.resolve(46);

      expect(next?.imageUrl, contains('/h/46/'));
      expect(resolver.previewOf(44)?.pageToken, EhFakeSite.tokenOf(44));
      expect(site.sheetRequests, isEmpty);
    });

    test('reload asks for another image server with the page nl key', () async {
      final site = EhFakeSite();
      final resolver = _resolver(site, previews: _pages(1, 20));
      await resolver.resolve(3);

      final pending = resolver.reload(3);
      expect(resolver.state[3], isA<EhPageLoading>());
      final reloaded = await pending;

      expect(site.requests.last.queryParameters['nl'], 'nl-3');
      expect(reloaded?.imageUrl, startsWith('https://b.hath.example/'));
      expect(resolver.imageOf(3), same(reloaded));
    });

    test('a failed page stays failed until it is retried', () async {
      final site = EhFakeSite(failingPages: {4});
      final resolver = _resolver(site, previews: _pages(1, 20));

      expect(await resolver.resolve(4), isNull);
      expect(resolver.state[4], isA<EhPageFailed>());
      expect((resolver.state[4] as EhPageFailed).error, isA<EhException>());
      await resolver.resolve(4);
      expect(site.imageRequests(4), hasLength(1));

      site.failingPages.clear();
      expect(await resolver.retry(4), isNotNull);
      expect(resolver.state[4], isA<EhPageReady>());
    });

    test('pages outside the gallery are never asked for', () async {
      final site = EhFakeSite();
      final resolver = _resolver(site);

      expect(await resolver.resolve(0), isNull);
      expect(await resolver.resolve(31), isNull);
      expect(site.requests, isEmpty);
    });

    test('a page no sheet lists fails as not found', () async {
      final site = EhFakeSite(total: 10);
      final resolver = EhPageResolver(
        client: EhClient(PrefServiceCache(), httpClient: site.client),
        gallery: site.gallery,
        total: 12,
      );
      addTearDown(resolver.destroy);

      expect(await resolver.resolve(12), isNull);
      final failed = resolver.state[12] as EhPageFailed;
      expect((failed.error as EhException).kind, EhErrorKind.notFound);
    });
  });
}
