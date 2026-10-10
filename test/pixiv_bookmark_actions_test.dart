import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';

import 'support/pixiv_bookmark_fakes.dart';
import 'support/pixiv_reader_harness.dart';

const _tags = [
  PixivTag(name: 'オリジナル'),
  PixivTag(name: '1000users入り'),
  PixivTag(name: '東方100users入り'),
  PixivTag(name: '風景'),
];

class _Fixture {
  final PrefServiceCache prefs;
  final FakePixivClient client;
  final FakePixivBookmarkApi api;
  final PixivBookmarkStore bookmarks;
  final PixivFollowStore follows;
  final downloads = <int>[];
  late final actions = PixivBookmarkActions(
    api: api,
    bookmarks: bookmarks,
    follows: follows,
    prefs: prefs,
    download: (illust) async => downloads.add(illust.id),
  );

  _Fixture._(this.prefs, this.client, this.api, this.bookmarks, this.follows);

  factory _Fixture({Map<String, Object> prefs = const {}}) {
    final cache = PrefServiceCache(cache: {...prefs});
    final client = FakePixivClient(cache);
    return _Fixture._(
      cache,
      client,
      FakePixivBookmarkApi(client: client),
      PixivBookmarkStore(),
      PixivFollowStore(client),
    );
  }

  List<String> get followCalls => client.calls.where((call) => call.startsWith('follow')).toList();
}

PixivIllust _work({bool bookmarked = false, bool followed = false, int userId = 42}) => PixivIllust(
  id: 7,
  title: 'Work',
  caption: '',
  type: 'illust',
  thumbnailUrl: 'https://i.pximg.net/7.jpg',
  pageCount: 1,
  userId: userId,
  userName: 'Mika',
  userAccount: 'mika',
  tags: _tags,
  isBookmarked: bookmarked,
  userIsFollowed: followed,
);

void main() {
  test('auto-tags are the work\'s own tags without popularity tags', () {
    expect(pixivAutoBookmarkTags(_work()), ['オリジナル', '風景']);
  });

  group('default visibility', () {
    test('the heart bookmarks publicly unless the reader chose private', () async {
      final public = _Fixture();
      final private = _Fixture(prefs: {optionPluginPixivDefaultPrivateBookmark: true});
      await public.actions.toggle(_work());
      await private.actions.toggle(_work());
      expect(public.api.writes, ['add:7:public:']);
      expect(private.api.writes, ['add:7:private:']);
    });

    test('the editor\'s visibility wins over the default', () async {
      final fixture = _Fixture(prefs: {optionPluginPixivDefaultPrivateBookmark: true});
      await fixture.actions.bookmark(_work(), restrict: 'public', tags: const []);
      expect(fixture.api.writes, ['add:7:public:']);
    });
  });

  group('auto-tag', () {
    test('sends the work\'s tags when on and no tags were chosen', () async {
      final fixture = _Fixture(prefs: {optionPluginPixivAutoTagBookmarks: true});
      await fixture.actions.toggle(_work());
      expect(fixture.api.writes, ['add:7:public:オリジナル 風景']);
    });

    test('explicit tags from the editor are sent as they are, even none', () async {
      final fixture = _Fixture(prefs: {optionPluginPixivAutoTagBookmarks: true});
      await fixture.actions.bookmark(_work(), tags: const []);
      await fixture.actions.bookmark(_work(), tags: const ['mine']);
      expect(fixture.api.writes, ['add:7:public:', 'add:7:public:mine']);
    });

    test('off sends no tags', () async {
      final fixture = _Fixture();
      await fixture.actions.toggle(_work());
      expect(fixture.api.writes, ['add:7:public:']);
    });
  });

  group('follow after bookmarking', () {
    final on = {optionPluginPixivFollowAfterBookmark: true};

    test('follows an author the reader does not follow yet, publicly', () async {
      final fixture = _Fixture(prefs: on);
      final outcome = await fixture.actions.toggle(_work());
      expect(outcome, (bookmarked: true, followedAuthor: true));
      expect(fixture.followCalls, ['follow:42:public']);
      expect(fixture.follows.state.followed[42], isTrue);
    });

    test('leaves a followed author, the reader\'s own work and a re-filed bookmark alone', () async {
      final followed = _Fixture(prefs: on);
      await followed.actions.toggle(_work(followed: true));
      final own = _Fixture(prefs: {...on, optionPluginPixivUserId: 42});
      await own.actions.toggle(_work());
      final refiled = _Fixture(prefs: on);
      await refiled.actions.bookmark(_work(bookmarked: true), tags: const ['x']);
      expect([...followed.followCalls, ...own.followCalls, ...refiled.followCalls], isEmpty);
    });

    test('off follows no one', () async {
      final fixture = _Fixture();
      await fixture.actions.toggle(_work());
      expect(fixture.followCalls, isEmpty);
    });
  });

  group('save after bookmarking', () {
    final on = {optionPluginPixivDownloadAfterBookmark: true};

    test('a new bookmark saves the work once', () async {
      final fixture = _Fixture(prefs: on);
      await fixture.actions.toggle(_work());
      expect(fixture.downloads, [7]);
    });

    test('removing, re-filing or bookmarking after a save does not save again', () async {
      final fixture = _Fixture(prefs: {...on, optionPluginPixivBookmarkAfterDownload: true});
      await fixture.actions.bookmark(_work(bookmarked: true), tags: const []);
      await fixture.actions.toggle(_work(bookmarked: true));
      await fixture.actions.ensureBookmarked(_work());
      expect(fixture.downloads, isEmpty);
    });
  });

  group('bookmark after saving', () {
    test('bookmarks a saved work with the defaults when on', () async {
      final fixture = _Fixture(
        prefs: {
          optionPluginPixivBookmarkAfterDownload: true,
          optionPluginPixivDefaultPrivateBookmark: true,
          optionPluginPixivAutoTagBookmarks: true,
          optionPluginPixivFollowAfterBookmark: true,
        },
      );
      final outcome = await fixture.actions.ensureBookmarked(_work());
      expect(outcome, (bookmarked: true, followedAuthor: true));
      expect(fixture.api.writes, ['add:7:private:オリジナル 風景']);
    });

    test('does nothing when off or already bookmarked', () async {
      final off = _Fixture();
      final on = _Fixture(prefs: {optionPluginPixivBookmarkAfterDownload: true});
      expect(await off.actions.ensureBookmarked(_work()), isNull);
      expect(await on.actions.ensureBookmarked(_work(bookmarked: true)), isNull);
      expect([...off.api.writes, ...on.api.writes], isEmpty);
    });
  });

  group('the heart', () {
    test('bookmarks, then removes, keeping the store in step', () async {
      final fixture = _Fixture();
      final work = _work();
      expect(await fixture.actions.toggle(work), (bookmarked: true, followedAuthor: false));
      expect(fixture.bookmarks.isBookmarked(work), isTrue);
      expect(await fixture.actions.toggle(work), (bookmarked: false, followedAuthor: false));
      expect(fixture.bookmarks.isBookmarked(work), isFalse);
      expect(fixture.api.writes, ['add:7:public:', 'delete:7']);
    });

    test('a failed write leaves the bookmark as it was and frees the work', () async {
      final fixture = _Fixture();
      fixture.api.failWrite = pixivNetworkFailure();
      await expectLater(fixture.actions.toggle(_work()), throwsA(isA<Object>()));
      expect((fixture.bookmarks.isBookmarked(_work()), fixture.bookmarks.isBusy(7)), (false, false));
    });
  });
}
