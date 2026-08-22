import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/home_model.dart';
import 'package:xta/home/feed_strip_store.dart';

/// Switching a plugin on should put its tab in the navigation bar. Only
/// Substack did, so enabling Reddit registered a tab that never appeared —
/// the toggle looked like it did nothing at all.
///
/// The tab is seeded once and remembered, because a tab the reader removes
/// afterwards has to stay removed rather than returning on the next launch.
BasePrefService _prefs({
  required bool redditEnabled,
  List<String> homePages = const ['feed', 'subscriptions', 'trending', 'saved'],
  List<String> seeded = const [],
}) => PrefServiceCache(
  cache: {
    optionHomePages: homePages,
    optionSeededPluginTabs: seeded,
    optionPluginRedditEnabled: redditEnabled,
    optionPluginSubstackEnabled: false,
    optionPluginDeepmarksEnabled: false,
    optionPluginKarakeepEnabled: false,
  },
);

Future<HomeModel> _load(BasePrefService prefs) async {
  final model = HomeModel(prefs, GroupsModel(prefs));
  await model.loadPages();
  return model;
}

bool _selected(HomeModel model, String id) =>
    model.state.any((page) => page.id == id && page.selected);

bool _present(HomeModel model, String id) =>
    model.state.any((page) => page.id == id);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('a plugin tab reaching the navigation bar', () {
    test('an enabled plugin takes a tab the first time it is loaded', () async {
      final prefs = _prefs(redditEnabled: true);
      final model = await _load(prefs);

      expect(
        _selected(model, pluginIdReddit),
        isTrue,
        reason: 'the toggle has to show something',
      );
      expect(
        prefs.getStringList(optionSeededPluginTabs),
        contains(pluginIdReddit),
      );
    });

    test('a disabled plugin offers no tab at all', () async {
      final model = await _load(_prefs(redditEnabled: false));

      expect(_present(model, pluginIdReddit), isFalse);
    });

    test('a tab the reader removed stays removed', () async {
      // Seeded already, and absent from the saved pages: that is what "removed"
      // looks like, and it must not come back.
      final model = await _load(
        _prefs(redditEnabled: true, seeded: [pluginIdReddit]),
      );

      expect(_selected(model, pluginIdReddit), isFalse);
      expect(
        _present(model, pluginIdReddit),
        isTrue,
        reason: 'still offered in settings, just not in the bar',
      );
    });

    test('a tab the reader kept is left alone', () async {
      final model = await _load(
        _prefs(
          redditEnabled: true,
          homePages: const ['feed', pluginIdReddit],
          seeded: const [pluginIdReddit],
        ),
      );

      expect(_selected(model, pluginIdReddit), isTrue);
    });

    test('the default tabs are still the ones selected', () async {
      final model = await _load(_prefs(redditEnabled: false));

      for (final id in ['feed', 'subscriptions', 'trending', 'saved']) {
        expect(_selected(model, id), isTrue, reason: id);
      }
    });

    test('an empty saved page list still keeps the default tabs', () async {
      final model = await _load(
        _prefs(redditEnabled: false, homePages: const []),
      );

      for (final id in ['feed', 'subscriptions', 'trending', 'saved']) {
        expect(_selected(model, id), isTrue, reason: id);
      }
    });

    test('enabling RSS selects its tab and pins the home strip', () async {
      final prefs = PrefServiceCache(
        cache: {
          optionHomePages: ['feed', 'subscriptions', 'trending', 'saved'],
          optionSeededPluginTabs: <String>[],
          optionPluginRssEnabled: true,
          optionPluginRedditEnabled: false,
          optionPluginSubstackEnabled: false,
          optionPluginDeepmarksEnabled: false,
          optionPluginKarakeepEnabled: false,
        },
      );
      final model = await _load(prefs);

      expect(_selected(model, pluginIdRss), isTrue);
      expect(feedStripPluginIds(prefs), contains(pluginIdRss));
    });

    test(
      'a JSON-string home.pages pref still loads the default tabs',
      () async {
        final prefs = PrefServiceCache(
          cache: {
            optionHomePages: '["feed","subscriptions","trending","saved"]',
            optionSeededPluginTabs: <String>[],
            optionPluginRedditEnabled: false,
            optionPluginSubstackEnabled: false,
            optionPluginDeepmarksEnabled: false,
            optionPluginKarakeepEnabled: false,
          },
        );
        final model = await _load(prefs);

        for (final id in ['feed', 'subscriptions', 'trending', 'saved']) {
          expect(_selected(model, id), isTrue, reason: id);
        }
      },
    );
  });
}
