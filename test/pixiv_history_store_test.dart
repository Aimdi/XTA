import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/utils/json.dart';

import 'support/memory_json_store.dart';
import 'support/pixiv_novel_fakes.dart';
import 'support/pixiv_reader_harness.dart';

PixivHistoryEntry _entry(int id, {String title = 'Work', String user = 'Mika', int minute = 0}) => PixivHistoryEntry(
  id: id,
  title: title,
  userId: 42,
  userName: user,
  thumbUrl: 'https://i.pximg.net/c/540x540_70/img-master/img/$id.jpg',
  viewedAt: DateTime.utc(2026, 10, 1, 12, minute),
);

void main() {
  group('pixivHistoryWith', () {
    test('puts the newest first and moves a reopened work to the top', () {
      var entries = <PixivHistoryEntry>[];
      for (final id in [1, 2, 3]) {
        entries = pixivHistoryWith(entries, _entry(id));
      }
      expect(entries.map((entry) => entry.id), [3, 2, 1]);

      entries = pixivHistoryWith(entries, _entry(1, minute: 5));
      expect(entries.map((entry) => entry.id), [1, 3, 2]);
      expect(entries.first.viewedAt.minute, 5);
    });

    test('keeps at most 500 works, dropping the oldest', () {
      var entries = <PixivHistoryEntry>[];
      for (var id = 1; id <= 505; id++) {
        entries = pixivHistoryWith(entries, _entry(id));
      }
      expect(entries, hasLength(pixivHistoryLimit));
      expect(entries.first.id, 505);
      expect(entries.last.id, 6);
    });
  });

  test('filters by title or artist, ignoring case', () {
    final entries = [_entry(1, title: 'Sommerfest'), _entry(2, title: 'Winter', user: 'Haruka'), _entry(3)];
    expect(pixivHistoryFiltered(entries, 'sommer').map((entry) => entry.id), [1]);
    expect(pixivHistoryFiltered(entries, 'HARU').map((entry) => entry.id), [2]);
    expect(pixivHistoryFiltered(entries, '  '), entries);
    expect(pixivHistoryFiltered(entries, 'nothing'), isEmpty);
  });

  group('PixivHistoryEntry.fromJson', () {
    test('round-trips every field', () {
      final entry = PixivHistoryEntry.of(pixivWork(id: 7, tags: const [PixivTag(name: 'cat')]), DateTime.utc(2026));
      final back = PixivHistoryEntry.fromJson(Json(entry.toJson()))!;
      expect(
        [back.id, back.title, back.userId, back.userName, back.thumbUrl],
        [7, 'Sommerfest', 42, 'Mika', entry.thumbUrl],
      );
      expect(back.tags, ['cat']);
      expect(back.viewedAt, DateTime.utc(2026));
      expect([back.width, back.height, back.bookmarks, back.bookmarked], [1200, 1700, 0, false]);
      expect(back.toIllust().aspectRatio, closeTo(1200 / 1700, 0.001), reason: 'the tile keeps its shape');
    });

    test("an R-18 or AI work keeps its rating through the file, so its tile keeps the badges", () {
      final r18 = PixivHistoryEntry.of(
        PixivIllust(
          id: 8,
          title: 'Night',
          caption: '',
          type: 'illust',
          thumbnailUrl: 'https://i.pximg.net/c/540x540_70/img-master/img/8.jpg',
          pageCount: 1,
          userId: 42,
          userName: 'Mika',
          userAccount: 'mika',
          isR18: true,
          isAi: true,
        ),
        DateTime.utc(2026),
      );
      final back = PixivHistoryEntry.fromJson(Json(r18.toJson()))!.toIllust();
      expect((back.isR18, back.isAi), (true, true));
      final plain = PixivHistoryEntry.fromJson(
        Json(PixivHistoryEntry.of(pixivWork(id: 9), DateTime.utc(2026)).toJson()),
      );
      expect((plain!.toIllust().isR18, plain.toIllust().isAi), (false, false));
      final older = PixivHistoryEntry.fromJson(const Json({'id': 10, 'thumbUrl': 'u'}))!.toIllust();
      expect((older.isR18, older.isAi), (false, false), reason: 'entries kept before the rating read as unrated');
    });

    test('drops entries without an id or thumbnail and tolerates the rest missing', () {
      expect(PixivHistoryEntry.fromJson(const Json({'title': 'x', 'thumbUrl': 'u'})), isNull);
      expect(PixivHistoryEntry.fromJson(const Json({'id': 3})), isNull);
      expect(PixivHistoryEntry.fromJson(const Json('not a map')), isNull);
      final sparse = PixivHistoryEntry.fromJson(const Json({'id': '9', 'thumbUrl': 'u', 'tags': 'oops'}))!;
      expect([sparse.id, sparse.title, sparse.userName, sparse.tags], [9, '', '', isEmpty]);
    });

    test('opens as a stub the detail fills in, keeping tags for the mute gate', () {
      final illust = PixivHistoryEntry.of(
        pixivWork(id: 5, tags: const [PixivTag(name: 'dog')]),
        DateTime.utc(2026),
      ).toIllust();
      expect([illust.id, illust.userId, illust.tags.single.name], [5, 42, 'dog']);
    });
  });

  group('PixivHistoryStore', () {
    test('records, removes and clears, keeping the file in step', () async {
      final storage = MemoryJsonStore();
      final store = PixivHistoryStore(storage: storage);
      addTearDown(store.destroy);
      await store.record(_entry(1));
      await store.record(_entry(2));
      await store.record(_entry(1));
      expect(store.state.map((entry) => entry.id), [1, 2]);

      final reloaded = PixivHistoryStore(storage: storage);
      addTearDown(reloaded.destroy);
      await reloaded.load();
      expect(reloaded.state.map((entry) => entry.id), [1, 2]);

      await store.remove(1);
      expect(store.state.map((entry) => entry.id), [2]);
      await store.clear();
      expect(store.state, isEmpty);
      expect(storage.values[pixivIllustHistoryKey], isEmpty);
    });

    test('a record made before the file was read keeps what the file held', () async {
      final storage = MemoryJsonStore()
        ..values[pixivIllustHistoryKey] = [_entry(1).toJson(), _entry(1).toJson(), 'junk', _entry(2).toJson()];
      final store = PixivHistoryStore(storage: storage);
      addTearDown(store.destroy);
      await store.record(_entry(3));
      expect(store.state.map((entry) => entry.id), [3, 1, 2]);
    });

    test('keeps novels apart under their own key', () async {
      final storage = MemoryJsonStore();
      final novels = PixivHistoryStore(storage: storage, key: 'pixiv-history:novels');
      addTearDown(novels.destroy);
      await novels.record(_entry(1));
      expect(storage.values.keys, ['pixiv-history:novels']);
    });

    test('a novel without a cover keeps its row after a restart', () async {
      final storage = MemoryJsonStore();
      final novels = PixivNovelHistoryStore(storage: storage);
      addTearDown(novels.destroy);
      await novels.record(PixivHistoryEntry.ofNovel(pixivNovel(id: 5, cover: false), DateTime.utc(2026)));
      await novels.record(PixivHistoryEntry.ofNovel(pixivNovel(id: 6), DateTime.utc(2026)));

      final reloaded = PixivNovelHistoryStore(storage: storage);
      addTearDown(reloaded.destroy);
      await reloaded.load();
      expect(reloaded.state.map((entry) => (entry.id, entry.thumbUrl.isEmpty)), [(6, false), (5, true)]);
    });
  });
}
