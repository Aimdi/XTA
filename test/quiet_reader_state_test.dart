import 'dart:io' show SocketException;

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/search/search_model.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/local_json_store.dart';

TweetChain _chain(String id) => TweetChain(id: id, tweets: [], isPinned: false);

void main() {
  testWidgets('an initial page stays loading without an error until quiet recovery succeeds', (tester) async {
    final feed = TweetFeedController();
    addTearDown(feed.dispose);
    var calls = 0;
    final errors = <Object>[];
    feed.controller.addListener(() {
      final error = feed.controller.value.error;
      if (error != null) errors.add(error);
    });
    feed.loader = (_) async {
      if (++calls == 1) throw const SocketException('temporary');
      return (chains: [_chain('first')], nextCursor: 'older');
    };
    feed.controller.fetchNextPage();
    await tester.pump();
    expect(feed.controller.value.isLoading, isTrue);
    expect(feed.controller.value.error, isNull);
    await tester.pump(const Duration(milliseconds: 400));
    expect(feed.items!.single.id, 'first');
    expect(feed.nextCursor, 'older');
    expect(feed.controller.value.isLoading, isFalse);
    expect(errors, isEmpty);
  });

  testWidgets('refresh keeps visible posts and the cursor while quiet recovery is pending', (tester) async {
    final feed = TweetFeedController();
    addTearDown(feed.dispose);
    feed.loader = (_) async => (chains: [_chain('visible')], nextCursor: 'older');
    feed.controller.fetchNextPage();
    await tester.pump();
    var calls = 0;
    feed.loader = (_) async {
      if (++calls < 3) throw const SocketException('temporary');
      return (chains: [_chain('fresh')], nextCursor: 'new-cursor');
    };
    final refresh = feed.softRefresh();
    for (final delay in [Duration.zero, const Duration(milliseconds: 400)]) {
      await tester.pump(delay);
      expect(feed.items!.single.id, 'visible');
      expect(feed.nextCursor, 'older');
      expect(feed.controller.value.isLoading, isTrue);
      expect(feed.controller.value.error, isNull);
    }
    await tester.pump(const Duration(milliseconds: 1200));
    await refresh;
    expect(feed.items!.single.id, 'fresh');
    expect(feed.nextCursor, 'new-cursor');
    expect(feed.controller.value.error, isNull);
  });

  testWidgets('loaded profile remains visible without a refresh error during recovery', (tester) async {
    var calls = 0;
    final model = ProfileModel(
      storage: _Storage(),
      byId: (id) async {
        if (++calls == 2) throw const SocketException('temporary');
        return Profile(UserWithExtra.fromArguments(idStr: id, name: 'Profile $calls'), []);
      },
    );
    addTearDown(model.destroy);
    await model.loadProfileById('alice');
    final refresh = model.loadProfileById('alice');
    await tester.pump();
    expect(model.state.user.name, 'Profile 1');
    expect(model.state.refreshing, isTrue);
    expect(model.state.refreshError, isNull);
    expect(model.error, isNull);
    await tester.pump(const Duration(milliseconds: 400));
    await refresh;
    expect(model.state.user.name, 'Profile 3');
    expect(model.state.refreshing, isFalse);
    expect(model.error, isNull);
  });

  testWidgets('a replaced search never retries its old query', (tester) async {
    final queries = <String>[];
    final model = SearchUsersModel(
      search: (query) async {
        queries.add(query);
        if (query == 'old') throw const SocketException('temporary');
        return [UserWithExtra.fromArguments(idStr: query)];
      },
    );
    final old = model.searchUsers('old');
    await tester.pump();
    await model.searchUsers('new');
    await old;
    await tester.pump(const Duration(seconds: 2));
    expect(queries, ['old', 'new']);
    expect(model.state.single.idStr, 'new');
    expect(model.error, isNull);
    await model.destroy();
  });
}

class _Storage implements JsonStore {
  @override
  Future<Object?> read(String key) async => null;
  @override
  Future<void> write(String key, Object? value) async {}
  @override
  Future<void> remove(String key) async {}
  @override
  Future<Map<String, Object?>> readPrefix(String prefix) async => {};
}
