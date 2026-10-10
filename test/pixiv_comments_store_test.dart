import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_store.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';

import 'support/pixiv_comments_fake.dart';

const _work = PixivCommentTarget.illust(120);

List<int> _ids(List<PixivComment> comments) => [for (final comment in comments) comment.id];

void main() {
  group('PixivCommentsStore', () {
    test('pages through a work\'s comments without repeats', () async {
      final api = FakePixivCommentsApi({
        FakePixivCommentsApi.commentsKey(_work): [
          PixivCommentPage([pixivTestComment(1), pixivTestComment(2)], nextUrl: 'next-1', total: 3),
          PixivCommentPage([pixivTestComment(2), pixivTestComment(3)]),
        ],
      });
      final store = PixivCommentsStore(api, _work);
      addTearDown(store.destroy);

      await store.refresh();
      expect(_ids(store.state), [1, 2]);
      expect(store.hasMore, isTrue);

      await store.loadMore();
      expect(_ids(store.state), [1, 2, 3]);
      expect(store.hasMore, isFalse);
      expect(api.calls, ['illust:120@first', 'illust:120@next-1']);
    });

    test('a thread reads the replies under its parent', () async {
      final parent = pixivTestComment(9, hasReplies: true);
      final api = FakePixivCommentsApi({
        FakePixivCommentsApi.repliesKey(_work, 9): [
          PixivCommentPage([pixivTestComment(10), pixivTestComment(11)], nextUrl: 'replies-2'),
          PixivCommentPage([pixivTestComment(12)]),
        ],
      });
      final store = PixivCommentsStore(api, _work, parent: parent);
      addTearDown(store.destroy);

      await store.refresh();
      await store.loadMore();
      expect(store.isThread, isTrue);
      expect(_ids(store.state), [10, 11, 12]);
      expect(api.calls, ['illust:120/9@first', 'illust:120/9@replies-2']);
    });

    test('novel comments and replies use the novel target', () async {
      const novel = PixivCommentTarget.novel(55);
      final api = FakePixivCommentsApi({
        FakePixivCommentsApi.commentsKey(novel): [
          PixivCommentPage([pixivTestComment(1, hasReplies: true)]),
        ],
      });
      final comments = PixivCommentsStore(api, novel);
      final replies = PixivCommentsStore(api, novel, parent: pixivTestComment(1));
      addTearDown(comments.destroy);
      addTearDown(replies.destroy);

      await comments.refresh();
      await replies.refresh();
      expect(api.calls, ['novel:55@first', 'novel:55/1@first']);
    });

    test('leaves out muted comments and muted authors, as they are when each page lands', () async {
      var mutes = PixivMuteState(commentIds: const {2}, authorIds: const {7});
      final api = FakePixivCommentsApi({
        FakePixivCommentsApi.commentsKey(_work): [
          PixivCommentPage([
            pixivTestComment(1),
            pixivTestComment(2),
            pixivTestComment(3, user: pixivCommenter(7, name: 'Spam')),
            pixivTestComment(4, anonymous: true),
          ], nextUrl: 'next-1'),
          PixivCommentPage([pixivTestComment(5), pixivTestComment(6)]),
        ],
      });
      final store = PixivCommentsStore(api, _work, mutes: () => mutes);
      addTearDown(store.destroy);

      await store.refresh();
      expect(_ids(store.state), [1, 4]);

      mutes = mutes.copyWith(commentIds: {2, 6});
      await store.loadMore();
      expect(_ids(store.state), [1, 4, 5]);
    });

    test('skips pages the mutes empty instead of stopping on them', () async {
      final mutes = PixivMuteState(authorIds: const {7});
      final spam = pixivCommenter(7, name: 'Spam');
      final api = FakePixivCommentsApi({
        FakePixivCommentsApi.commentsKey(_work): [
          PixivCommentPage([pixivTestComment(1, user: spam)], nextUrl: 'next-1'),
          PixivCommentPage([pixivTestComment(2)]),
        ],
      });
      final store = PixivCommentsStore(api, _work, mutes: () => mutes);
      addTearDown(store.destroy);

      await store.refresh();
      expect(_ids(store.state), [2]);
      expect(store.hasMore, isFalse);
    });

    test('a last page the mutes empty still ends the paging for the list', () async {
      final mutes = PixivMuteState(authorIds: const {7});
      final api = FakePixivCommentsApi({
        FakePixivCommentsApi.commentsKey(_work): [
          PixivCommentPage([pixivTestComment(1)], nextUrl: 'next-1'),
          PixivCommentPage([pixivTestComment(2, user: pixivCommenter(7, name: 'Spam'))]),
        ],
      });
      final store = PixivCommentsStore(api, _work, mutes: () => mutes);
      addTearDown(store.destroy);
      await store.refresh();

      final announced = <List<PixivComment>>[];
      final stop = store.observer(onState: announced.add);
      addTearDown(stop);
      await store.loadMore();
      expect(store.hasMore, isFalse);
      expect(announced, isNotEmpty);
      expect(_ids(store.state), [1]);
    });

    test('a failed first page is an error, a failed later page keeps the list', () async {
      final api = FakePixivCommentsApi({
        FakePixivCommentsApi.commentsKey(_work): [
          null,
          PixivCommentPage([pixivTestComment(1)], nextUrl: 'next-1'),
          null,
        ],
      });
      final store = PixivCommentsStore(api, _work);
      addTearDown(store.destroy);

      await store.refresh();
      expect(store.triple.error, isNotNull);

      await store.refresh();
      expect(store.moreError, isNull);
      await store.loadMore();
      expect(store.triple.error, isNull);
      expect(_ids(store.state), [1]);
      expect(store.hasMore, isTrue);
      expect(store.moreError, isA<PixivException>());
    });

    test('a later page that failed is announced, and clears once one arrives', () async {
      final api = FakePixivCommentsApi({
        FakePixivCommentsApi.commentsKey(_work): [
          PixivCommentPage([pixivTestComment(1)], nextUrl: 'next-1'),
          null,
          PixivCommentPage([pixivTestComment(2)]),
        ],
      });
      final store = PixivCommentsStore(api, _work);
      addTearDown(store.destroy);
      await store.refresh();

      final announced = <bool>[];
      final stop = store.observer(onState: (_) => announced.add(store.loadingMore));
      addTearDown(stop);
      await store.loadMore();
      expect(announced, [true, false]);
      expect(store.moreError, isNotNull);

      final retry = store.loadMore();
      expect(store.moreError, isNull, reason: 'no failure is shown while the retry runs');
      await retry;
      expect(store.moreError, isNull);
      expect(_ids(store.state), [1, 2]);
    });

    test('the first load counts as loading from the start, not as an empty list', () async {
      final store = PixivCommentsStore(FakePixivCommentsApi({}), _work);
      addTearDown(store.destroy);
      final loading = store.refresh();
      expect(store.isLoading, isTrue);
      await loading;
      expect(store.isLoading, isFalse);
      expect(store.state, isEmpty);
    });
  });

  group('pixivVisibleComments', () {
    test('hides a muted comment and everything by a muted author', () {
      final comments = [
        pixivTestComment(1),
        pixivTestComment(2, user: pixivCommenter(7)),
        pixivTestComment(3),
        pixivTestComment(4, anonymous: true),
      ];
      final mutes = PixivMuteState(commentIds: const {3}, authorIds: const {7});
      expect(_ids(pixivVisibleComments(comments, mutes)), [1, 4]);
      expect(_ids(pixivVisibleComments(comments, PixivMuteState.empty)), [1, 2, 3, 4]);
    });
  });
}
