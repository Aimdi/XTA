import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/utils/published_documents.dart';

const _mainRegistry = 'https://raw.githubusercontent.com/$githubRepo/main/endpoints.json';
const _masterRegistry = 'https://raw.githubusercontent.com/$githubRepo/master/endpoints.json';
const _mainCatalogue = 'https://raw.githubusercontent.com/$githubRepo/main/plugins.json';
const _readFromMain = '{"endpoints": {"TweetDetail": "oCon7R-cgWRFy6EfZjaKfg"}}';

PrefServiceCache _prefs(Map<String, Object> stored) => PrefServiceCache(
  cache: {
    optionEndpointRegistryCache: _readFromMain,
    optionEndpointRegistryFetchedAt: '2026-10-29T08:00:00',
    ...stored,
  },
);

void main() {
  test('the documents are read from the default branch', () {
    expect(defaultEndpointRegistryUrl, 'https://raw.githubusercontent.com/$githubRepo/HEAD/endpoints.json');
    expect(defaultPluginCatalogueUrl, 'https://raw.githubusercontent.com/$githubRepo/HEAD/plugins.json');
  });

  test('only an earlier default counts as one', () {
    expect(isFormerPublishedDefault(_mainRegistry, defaultEndpointRegistryUrl), isTrue);
    expect(isFormerPublishedDefault('$_masterRegistry ', defaultEndpointRegistryUrl), isTrue);
    expect(isFormerPublishedDefault(_mainCatalogue, defaultEndpointRegistryUrl), isFalse);
    expect(isFormerPublishedDefault(defaultEndpointRegistryUrl, defaultEndpointRegistryUrl), isFalse);
    expect(isFormerPublishedDefault('https://example.org/main/endpoints.json', defaultEndpointRegistryUrl), isFalse);
  });

  group('migratePublishedDocumentUrls', () {
    for (final former in [_mainRegistry, _masterRegistry]) {
      test('moves $former to the default branch and forgets what it read', () async {
        final prefs = _prefs({optionEndpointRegistryUrl: former});

        await migratePublishedDocumentUrls(prefs);

        expect(prefs.get<String>(optionEndpointRegistryUrl), defaultEndpointRegistryUrl);
        expect(prefs.get<String>(optionEndpointRegistryCache), isEmpty);
        expect(prefs.get<String>(optionEndpointRegistryFetchedAt), isEmpty);
      });
    }

    test('moves the plugin catalogue and keeps its list', () async {
      final prefs = PrefServiceCache(
        cache: {optionPluginCatalogueUrl: _mainCatalogue, optionPluginCatalogueCache: '[]'},
      );

      await migratePublishedDocumentUrls(prefs);

      expect(prefs.get<String>(optionPluginCatalogueUrl), defaultPluginCatalogueUrl);
      expect(prefs.get<String>(optionPluginCatalogueCache), '[]');
    });

    test('keeps an address set by hand, and what was read from it', () async {
      const own = 'https://example.org/endpoints.json';
      final prefs = _prefs({optionEndpointRegistryUrl: own});

      await migratePublishedDocumentUrls(prefs);

      expect(prefs.get<String>(optionEndpointRegistryUrl), own);
      expect(prefs.get<String>(optionEndpointRegistryCache), _readFromMain);
      expect(prefs.get<String>(optionEndpointRegistryFetchedAt), '2026-10-29T08:00:00');
    });

    test('leaves an install already on the default branch alone', () async {
      final prefs = _prefs({optionEndpointRegistryUrl: defaultEndpointRegistryUrl});

      await migratePublishedDocumentUrls(prefs);

      expect(prefs.get<String>(optionEndpointRegistryCache), _readFromMain);
      expect(prefs.get<String>(optionPluginCatalogueUrl), isNull);
    });
  });
}
