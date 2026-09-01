import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_catalogue.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/plugin_storage.dart';

void main() {
  group('reading a published catalogue', () {
    test('offers the ids it lists, in the order it lists them', () {
      final ids = parsePluginCatalogue(
        '{"plugins":[{"id":"reddit"},{"id":"stocks"}]}',
      );

      expect(ids, ['reddit', 'stocks']);
    });

    test('an entry marked unavailable is held back rather than offered', () {
      final ids = parsePluginCatalogue(
        '{"plugins":[{"id":"reddit","available":false},{"id":"stocks","available":true},{"id":"pixiv","available":false}]}',
      );

      expect(ids, ['stocks']);
      expect(ids, isNot(contains('pixiv')));
    });

    test('every id it names is one this build actually has', () {
      final ids = parsePluginCatalogue(
        '{"plugins":[{"id":"substack"},{"id":"reddit"},{"id":"stocks"},{"id":"karakeep"},{"id":"deepmarks"}]}',
      );

      for (final id in ids) {
        expect(
          pluginById(id),
          isNotNull,
          reason: '$id is offered but not compiled in',
        );
      }
    });

    test('a document that no longer fits gives up rather than throwing', () {
      for (final body in [
        '{}',
        '[]',
        '"nonsense"',
        '{"plugins":"nope"}',
        '{"plugins":[1,2]}',
      ]) {
        expect(parsePluginCatalogue(body), isEmpty, reason: body);
      }
    });

    test('an entry with no id is skipped, not counted as one', () {
      expect(
        parsePluginCatalogue('{"plugins":[{"note":"soon"},{"id":"reddit"}]}'),
        ['reddit'],
      );
    });

    test('a stale catalogue still offers a plugin this build compiled in', () {
      const stale =
          '{"plugins":[{"id":"reddit"},{"id":"pixiv","available":false}]}';
      expect(
        offeredPluginIds(
          builtInIds: ['reddit', 'hackernews', 'pixiv'],
          catalogueOffered: parsePluginCatalogue(stale),
          catalogueMentioned: parsePluginCatalogueMentioned(stale),
        ),
        ['reddit', 'hackernews'],
      );
    });
  });

  group('what the store says a plugin is holding', () {
    test('bytes are shown whole, larger units to one decimal', () {
      expect(formatStorageSize(0), '0 B');
      expect(formatStorageSize(512), '512 B');
      expect(formatStorageSize(1024), '1.0 kB');
      expect(formatStorageSize(1536), '1.5 kB');
      expect(formatStorageSize(1024 * 1024 * 3), '3.0 MB');
    });

    test('it does not run out of units', () {
      expect(formatStorageSize(1024 * 1024 * 1024 * 5), endsWith('GB'));
    });
  });
}
