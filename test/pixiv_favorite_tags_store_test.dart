import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

List<String> _names(List<PixivTag> tags) => [for (final tag in tags) tag.name];

void main() {
  late PrefServiceCache prefs;
  late PixivFavoriteTagsStore store;

  setUp(() {
    prefs = PrefServiceCache(cache: {optionPluginPixivFavoriteTags: '[]'});
    store = PixivFavoriteTagsStore(prefs);
  });

  tearDown(() => store.destroy());

  test('adding and removing build on the stored tags, so an import is never saved over', () async {
    await store.add(const PixivTag(name: 'old'));
    await prefs.set(optionPluginPixivFavoriteTags, jsonEncode(['imported', 'other']));
    await store.add(const PixivTag(name: 'new'));
    expect(_names(store.state), ['imported', 'other', 'new']);
    await store.remove('other');
    expect(_names(readPixivFavoriteTags(prefs)), ['imported', 'new']);
  });

  test('a reorder over a list that changed underneath shows the stored list instead', () async {
    await store.add(const PixivTag(name: 'a'));
    await store.add(const PixivTag(name: 'b'));
    await prefs.set(optionPluginPixivFavoriteTags, jsonEncode(['c', 'd']));
    await store.reorder(0, 1);
    expect(_names(store.state), ['c', 'd']);
    expect(_names(readPixivFavoriteTags(prefs)), ['c', 'd']);
  });

  test('adds at the end, and a tag differing only in case replaces the old one', () async {
    await store.add(const PixivTag(name: '初音ミク', translatedName: 'Hatsune Miku'));
    await store.add(const PixivTag(name: 'landscape'));
    await store.add(const PixivTag(name: 'Landscape', translatedName: 'scenery'));
    expect(_names(store.state), ['初音ミク', 'Landscape']);
    expect(store.contains('LANDSCAPE'), isTrue);
    expect(store.contains('風景'), isFalse);
  });

  test('reorders to the index the row was dropped at, and removes', () async {
    for (final name in ['a', 'b', 'c', 'd']) {
      await store.add(PixivTag(name: name));
    }
    await store.reorder(0, 2);
    expect(_names(store.state), ['b', 'c', 'a', 'd']);
    await store.reorder(3, 0);
    expect(_names(store.state), ['d', 'b', 'c', 'a']);
    await store.reorder(9, 0);
    expect(_names(store.state), ['d', 'b', 'c', 'a']);
    await store.remove('B');
    expect(_names(store.state), ['d', 'c', 'a']);
  });

  test('persists in order with translations, for the next screen and the backup', () async {
    await store.add(const PixivTag(name: 'オリジナル', translatedName: 'original'));
    await store.add(const PixivTag(name: '風景'));
    expect(jsonDecode(prefs.get<String>(optionPluginPixivFavoriteTags)!), [
      {'name': 'オリジナル', 'translated_name': 'original'},
      {'name': '風景'},
    ]);

    final reopened = PixivFavoriteTagsStore(prefs);
    addTearDown(reopened.destroy);
    expect(_names(reopened.state), ['オリジナル', '風景']);
    expect(reopened.state.first.translatedName, 'original');
  });

  test('reads bare names, skips blanks and survives garbage', () async {
    await prefs.set(
      optionPluginPixivFavoriteTags,
      jsonEncode([
        'miku',
        {'name': '  '},
        {'name': 'rin', 'translated_name': 7},
        42,
      ]),
    );
    expect(_names(readPixivFavoriteTags(prefs)), ['miku', 'rin']);
    expect(readPixivFavoriteTags(prefs).last.translatedName, isNull);

    await prefs.set(optionPluginPixivFavoriteTags, '{oops');
    expect(readPixivFavoriteTags(prefs), isEmpty);
    store.load();
    expect(store.state, isEmpty);
  });

  test('pixivMoved leaves the list alone for an index outside it', () {
    expect(pixivMoved([1, 2, 3], -1, 0), [1, 2, 3]);
    expect(pixivMoved([1, 2, 3], 1, 99), [1, 3, 2]);
  });
}
