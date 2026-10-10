import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';

import 'support/pixiv_novel_fakes.dart';

Map<String, Object?> novelJson(int id, {int xRestrict = 0, int aiType = 1, bool? visible, Object? tags}) => {
  'id': id,
  'title': ' Novel $id ',
  'caption': 'First line<br />See <a href="https://www.pixiv.net/novel/show.php?id=2">part two</a>',
  'restrict': 0,
  'x_restrict': xRestrict,
  'is_original': true,
  'image_urls': {
    'square_medium': 'https://i.pximg.net/c/128x128/novel-cover-master/$id.jpg',
    'medium': 'https://i.pximg.net/c/176x352/novel-cover-master/$id.jpg',
    'large': 'https://i.pximg.net/c/240x480_80/novel-cover-master/$id.jpg',
  },
  'create_date': '2026-09-01T12:30:00+09:00',
  'tags':
      tags ??
      [
        {'name': 'オリジナル', 'translated_name': 'original', 'added_by_uploaded_user': true},
        {'name': '   ', 'translated_name': null},
        {'name': '秋', 'translated_name': null},
      ],
  'page_count': 3,
  'text_length': 15234,
  'user': {
    'id': 42,
    'name': 'Mika',
    'account': 'mika',
    'profile_image_urls': {'medium': 'https://i.pximg.net/user-profile/42.jpg'},
    'is_followed': true,
  },
  'series': {'id': 77, 'title': 'Seasons'},
  'is_bookmarked': true,
  'total_bookmarks': 321,
  'total_view': 4567,
  'visible': ?visible,
  'total_comments': 8,
  'is_muted': false,
  'is_mypixiv_only': false,
  'is_x_restricted': false,
  'novel_ai_type': aiType,
};

void main() {
  group('a novel', () {
    test('reads every field a list sends', () {
      final novel = pixivNovelFromJson(novelJson(5))!;

      expect(novel.id, 5);
      expect(novel.title, 'Novel 5');
      expect(novel.caption, 'First lineSee part two');
      expect(novel.captionHtml, contains('<a href'));
      expect(novel.coverUrl, endsWith('176x352/novel-cover-master/5.jpg'));
      expect((novel.user.id, novel.user.name, novel.user.account, novel.user.isFollowed), (42, 'Mika', 'mika', true));
      expect(novel.user.avatarUrl, 'https://i.pximg.net/user-profile/42.jpg');
      expect([for (final tag in novel.tags) tag.displayName], ['original', '秋']);
      expect((novel.series!.id, novel.series!.title), (77, 'Seasons'));
      expect((novel.textLength, novel.pageCount), (15234, 3));
      expect(novel.createdAt, DateTime.utc(2026, 9, 1, 3, 30).toLocal());
      expect((novel.totalBookmarks, novel.totalViews, novel.totalComments), (321, 4567, 8));
      expect((novel.isBookmarked, novel.isR18, novel.isAi, novel.isOriginal), (true, false, false, true));
      expect(novel.url, 'https://www.pixiv.net/novel/show.php?id=5');
    });

    test('R-18, R-18G and AI come from x_restrict and novel_ai_type', () {
      final r18 = pixivNovelFromJson(novelJson(1, xRestrict: 1))!;
      final r18g = pixivNovelFromJson(novelJson(2, xRestrict: 2, aiType: 2))!;

      expect((r18.isR18, r18.isR18G, r18.isAi), (true, false, false));
      expect((r18g.isR18, r18g.isR18G, r18g.isAi), (true, true, true));
    });

    test('a missing or reshaped payload reads as empty fields, never a throw', () {
      final bare = pixivNovelFromJson({'id': '12'})!;
      expect(bare.id, 12);
      expect((bare.title, bare.caption, bare.coverUrl, bare.tags.length, bare.series), ('', '', null, 0, null));
      expect((bare.user.id, bare.textLength, bare.pageCount, bare.createdAt), (0, 0, 1, null));

      final reshaped = pixivNovelFromJson({
        'id': 13,
        'title': 7,
        'tags': 'not a list',
        'user': ['not', 'a', 'map'],
        'series': {'id': 0},
        'image_urls': {'medium': '  '},
        'x_restrict': 'r18',
        'novel_ai_type': null,
        'create_date': 'yesterday',
      })!;
      expect((reshaped.title, reshaped.tags.length, reshaped.series, reshaped.coverUrl), ('', 0, null, null));
      expect((reshaped.isR18, reshaped.isAi, reshaped.createdAt), (false, false, null));
    });

    test('a novel Pixiv withholds, or one without an id, is not a novel', () {
      expect(pixivNovelFromJson(novelJson(3, visible: false)), isNull);
      expect(pixivNovelFromJson({'title': 'no id'}), isNull);
      expect(pixivNovelFromJson({'id': -1}), isNull);
      expect(pixivNovelFromJson('a string'), isNull);
      expect(pixivNovelFromJson(null), isNull);
    });
  });

  group('a novel list', () {
    final json = {
      'novels': [
        novelJson(1),
        novelJson(2, xRestrict: 1),
        novelJson(3, aiType: 2),
        novelJson(4, visible: false),
        {'broken': true},
        'junk',
      ],
      'next_url': 'https://app-api.pixiv.net/v1/novel/recommended?offset=30',
    };

    test('leaves out R-18 and AI novels unless asked for, and skips what does not parse', () {
      List<int> ids({required bool r18, required bool ai}) => [
        for (final novel in parsePixivNovelList(json, includeR18: r18, includeAi: ai)) novel.id,
      ];

      expect(ids(r18: false, ai: true), [1, 3]);
      expect(ids(r18: true, ai: true), [1, 2, 3]);
      expect(ids(r18: true, ai: false), [1, 2]);
      expect(ids(r18: false, ai: false), [1]);
    });

    test('a reshaped list is an empty list', () {
      expect(parsePixivNovelList({'novels': 'none'}), isEmpty);
      expect(parsePixivNovelList(null), isEmpty);
      expect(parsePixivNovelList(const ['novels']), isEmpty);
    });
  });

  group('a series page', () {
    Map<String, Object?> seriesJson() => {
      'novel_series_detail': {
        'id': 77,
        'title': 'Seasons',
        'caption': 'Four <b>seasons</b>',
        'is_original': true,
        'is_concluded': true,
        'content_count': 4,
        'total_character_count': 54321,
        'user': {'id': 42, 'name': 'Mika', 'account': 'mika', 'profile_image_urls': {}},
        'display_text': '全4話',
        'novel_ai_type': 1,
        'watchlist_added': true,
      },
      'novel_series_first_novel': novelJson(10),
      'novel_series_latest_novel': novelJson(13, xRestrict: 1),
      'novels': [novelJson(10), novelJson(11, xRestrict: 1), novelJson(12)],
      'next_url': 'https://app-api.pixiv.net/v2/novel/series?series_id=77&last_order=3',
    };

    test('reads the series, its first and newest chapters, and numbers the chapters in order', () {
      final page = parsePixivNovelSeriesPage(seriesJson());
      final series = page.series!;

      expect(
        (series.id, series.title, series.caption, series.captionHtml),
        (77, 'Seasons', 'Four seasons', 'Four <b>seasons</b>'),
      );
      expect((series.isConcluded, series.contentCount, series.totalCharacterCount), (true, 4, 54321));
      expect((series.watchlistAdded, series.isOriginal, series.user.name), (true, true, 'Mika'));
      expect(series.url, 'https://www.pixiv.net/novel/series/77');
      expect((page.first!.id, page.latest!.id), (10, 13));
      expect([for (final chapter in page.chapters) (chapter.order, chapter.novel.id)], [(1, 10), (2, 11), (3, 12)]);
      expect((page.listed, page.nextUrl), (3, 'https://app-api.pixiv.net/v2/novel/series?series_id=77&last_order=3'));
    });

    test('a chapter the filters hide keeps the next one\'s number, and a hidden newest chapter is not offered', () {
      final page = parsePixivNovelSeriesPage(seriesJson(), allowed: (novel) => !novel.isR18);

      expect([for (final chapter in page.chapters) (chapter.order, chapter.novel.id)], [(1, 10), (3, 12)]);
      expect(page.listed, 3);
      expect((page.first?.id, page.latest?.id), (10, null));
    });

    test('a reshaped page has no series and no chapters', () {
      final page = parsePixivNovelSeriesPage({'novel_series_detail': 'gone', 'novels': {}, 'next_url': ''});
      expect(
        (page.series, page.first, page.latest, page.chapters.length, page.listed, page.nextUrl),
        (null, null, null, 0, 0, null),
      );
      expect(pixivNovelSeriesFromJson({'id': 'x'}), isNull);
    });
  });

  group('muting novels', () {
    final cat = pixivNovel(id: 1, tags: const [PixivTag(name: 'Cat')]);
    final dogAndRain = pixivNovel(
      id: 2,
      tags: const [
        PixivTag(name: 'dog'),
        PixivTag(name: 'rain'),
      ],
    );
    final byRen = pixivNovel(id: 3, userId: 9, userName: 'Ren', tags: const []);

    List<int> shown(PixivMuteState mute) => [
      for (final novel in mute.filterNovels([cat, dogAndRain, byRen])) novel.id,
    ];

    test('hides novels by author, by tag name, by pattern and by id', () {
      expect(shown(const PixivMuteState()), [1, 2, 3]);
      expect(shown(const PixivMuteState(authorIds: {9})), [1, 2]);
      expect(shown(const PixivMuteState(tags: {'cat'})), [2, 3]);
      expect(shown(const PixivMuteState(tags: {r"r'#dog.*#rain'"})), [1, 3]);
      expect(shown(const PixivMuteState(novelIds: {2})), [1, 3]);
      expect(shown(const PixivMuteState(illustIds: {1, 2, 3})), [1, 2, 3], reason: 'a muted work id is not a novel id');
    });

    test('filters any list whose items carry a novel', () {
      final chapters = [PixivNovelChapter(order: 1, novel: cat), PixivNovelChapter(order: 2, novel: byRen)];
      final kept = const PixivMuteState(authorIds: {9}).filterNovelsOf(chapters, (chapter) => chapter.novel);
      expect([for (final chapter in kept) chapter.order], [1]);
    });
  });
}
