import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_gate.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';

void main() {
  group('PixivMuteState', () {
    test('filters muted authors, tags and illust ids', () {
      const state = PixivMuteState(authorIds: {10}, tags: {'cat'}, illustIds: {3});

      final visible = state.filter([
        _illust(id: 1, userId: 10),
        _illust(id: 2, tags: const [PixivTag(name: 'Cat')]),
        _illust(id: 3),
        _illust(id: 4, tags: const [PixivTag(name: 'dog')]),
      ]);

      expect(visible.map((illust) => illust.id), [4]);
    });

    test("an r'pattern' entry matches single tags, ignoring case", () {
      final state = PixivMuteState(tags: {pixivNormalizeMuteTag(r"r'^ai[- ]?art$'")});
      expect(state.isMuted(_illust(id: 1, tags: const [PixivTag(name: 'AI-Art')])), isTrue);
      expect(state.isMuted(_illust(id: 2, tags: const [PixivTag(name: 'fairart')])), isFalse);
      expect(state.mutedTagOf(_illust(id: 1, tags: const [PixivTag(name: 'AIart')])), r"r'^ai[- ]?art$'");
    });

    test('a pattern sees every tag joined as #a#b, so it can ask for several together', () {
      final state = PixivMuteState(tags: {r"r'#cat#.*#dog'"});
      final both = _illust(
        id: 1,
        tags: const [
          PixivTag(name: 'cat'),
          PixivTag(name: 'tree'),
          PixivTag(name: 'dog'),
        ],
      );
      final one = _illust(
        id: 2,
        tags: const [
          PixivTag(name: 'cat'),
          PixivTag(name: 'tree'),
        ],
      );
      expect(state.filter([both, one]).map((illust) => illust.id), [2]);
    });

    test('plain entries still match whole tags only', () {
      const state = PixivMuteState(tags: {'cat'});
      expect(state.isMuted(_illust(id: 1, tags: const [PixivTag(name: 'catgirl')])), isFalse);
    });
  });

  group('muted tag entries', () {
    test('patterns keep their case; plain names are lower-cased', () {
      expect(pixivNormalizeMuteTag(r"  r'\D+'  "), r"r'\D+'");
      expect(pixivNormalizeMuteTag('  Cat '), 'cat');
      expect(pixivMutePattern("r'x'"), 'x');
      expect(pixivMutePattern('rx'), isNull);
      expect(pixivMutePattern("r''"), isNull);
    });

    test('an invalid pattern or a blank entry is refused', () {
      expect(pixivMuteTagValid("r'(unclosed'"), isFalse);
      expect(pixivMuteTagValid('   '), isFalse);
      expect(pixivMuteTagValid("r'ok+'"), isTrue);
      expect(pixivMuteTagValid('(plain'), isTrue);
    });
  });

  group('PixivMuteStore', () {
    test('legacy bare author ids still load, without names', () async {
      final prefs = PrefServiceCache()..set(optionPluginPixivMutedAuthors, '[10, "11", 0, "x"]');
      final store = PixivMuteStore(prefs);
      addTearDown(store.destroy);
      await store.load();
      expect(store.state.authorIds, {10, 11});
      expect(store.state.authorNames, isEmpty);
    });

    test('authors round-trip with the name they were muted under', () async {
      final prefs = PrefServiceCache();
      final store = PixivMuteStore(prefs);
      addTearDown(store.destroy);
      await store.muteAuthor(12, name: ' Mika ');
      await store.muteAuthor(5);
      expect(jsonDecode(prefs.get<String>(optionPluginPixivMutedAuthors)!), [
        {'id': 5, 'name': ''},
        {'id': 12, 'name': 'Mika'},
      ]);

      final reloaded = PixivMuteStore(prefs);
      addTearDown(reloaded.destroy);
      await reloaded.load();
      expect(reloaded.state.authorIds, {5, 12});
      expect(reloaded.state.authorNames, {12: 'Mika'});

      await reloaded.unmuteAuthor(12);
      expect(reloaded.state.authorNames, isEmpty);
    });

    test('a typed pattern is kept as written; an invalid one is refused and not saved', () async {
      final prefs = PrefServiceCache();
      final store = PixivMuteStore(prefs);
      addTearDown(store.destroy);
      expect(await store.muteTag(r"r'\D+'"), isTrue);
      expect(await store.muteTag("r'[broken'"), isFalse);
      expect(await store.muteTag('Cat'), isTrue);
      expect(store.state.tags, {r"r'\D+'", 'cat'});

      final reloaded = PixivMuteStore(prefs);
      addTearDown(reloaded.destroy);
      await reloaded.load();
      expect(reloaded.state.tags, {r"r'\D+'", 'cat'});
    });

    test('a damaged preference reads as nothing muted', () async {
      final prefs = PrefServiceCache()
        ..set(optionPluginPixivMutedAuthors, '{not json')
        ..set(optionPluginPixivMutedTags, '"cat"');
      final store = PixivMuteStore(prefs);
      addTearDown(store.destroy);
      await store.load();
      expect(store.state.isEmpty, isTrue);
    });
  });

  group('pixivMuteReason', () {
    test('names the work, then the author by name, then the matching tag', () {
      final work = _illust(id: 1, userId: 9, tags: const [PixivTag(name: 'cat')]);
      expect(pixivMuteReason(const PixivMuteState(illustIds: {1}, authorIds: {9}), work), isA<PixivMutedWork>());
      expect(
        pixivMuteReason(const PixivMuteState(authorIds: {9}, authorNames: {9: 'Mika'}), work),
        isA<PixivMutedAuthor>().having((reason) => reason.name, 'name', 'Mika'),
      );
      expect(
        pixivMuteReason(const PixivMuteState(tags: {'cat'}), work),
        isA<PixivMutedTag>().having((reason) => reason.entry, 'entry', 'cat'),
      );
      expect(pixivMuteReason(const PixivMuteState(tags: {'dog'}), work), isNull);
    });
  });
}

PixivIllust _illust({required int id, int userId = 1, List<PixivTag> tags = const []}) {
  return PixivIllust(
    id: id,
    title: '',
    caption: '',
    type: 'illust',
    thumbnailUrl: 'https://i.pximg.net/$id.jpg',
    pageCount: 1,
    userId: userId,
    userName: '',
    userAccount: '',
    tags: tags,
  );
}
