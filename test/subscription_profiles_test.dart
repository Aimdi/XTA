import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/subscription_profiles.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/user.dart';

String _picture(String id, String size) => 'https://pbs.twimg.com/profile_images/1/${id}_$size.jpg';

UserSubscription _followed(String id, {bool inFeed = true, int? maxPostsPerLoad}) => UserSubscription(
  id: id,
  screenName: 'handle_$id',
  name: 'Name $id',
  profileImageUrlHttps: _picture(id, 'normal'),
  verified: false,
  createdAt: DateTime(2026),
  inFeed: inFeed,
  maxPostsPerLoad: maxPostsPerLoad,
);

UserWithExtra _sent(String id, {String? screenName, String? name, String? picture}) => UserWithExtra()
  ..idStr = id
  ..screenName = screenName ?? 'handle_$id'
  ..name = name ?? 'Name $id'
  ..profileImageUrlHttps = picture ?? _picture(id, 'normal');

TweetWithCard _post(String author, {TweetWithCard? reposts, TweetWithCard? quotes}) => TweetWithCard()
  ..idStr = 'post-$author'
  ..user = _sent(author)
  ..retweetedStatusWithCard = reposts
  ..quotedStatusWithCard = quotes;

void main() {
  group('postAuthors', () {
    test("names each post's author and the authors of what it reposts and quotes", () {
      final chains = [
        TweetChain(
          id: '1',
          tweets: [_post('a', reposts: _post('b', quotes: _post('c')))],
          isPinned: false,
        ),
        TweetChain(id: '2', tweets: [_post('d'), TweetWithCard()..idStr = 'no author'], isPinned: false),
      ];

      expect(postAuthors(chains).map((user) => user.idStr), ['a', 'b', 'c', 'd']);
    });
  });

  group('refreshedSubscriptions', () {
    final stored = <Subscription>[
      _followed('1', inFeed: false, maxPostsPerLoad: 3),
      SearchSubscription(id: 'from:someone', createdAt: DateTime(2026)),
    ];

    test('picks up a new handle, name or picture and keeps the feed settings', () {
      for (final sent in [
        _sent('1', screenName: 'renamed'),
        _sent('1', name: 'New name'),
        _sent('1', picture: 'https://pbs.twimg.com/profile_images/2/new_normal.jpg'),
      ]) {
        final fresh = refreshedSubscriptions(stored, [sent])['1']!;

        expect(
          (fresh.screenName, fresh.name, fresh.profileImageUrlHttps),
          (sent.screenName, sent.name, sent.profileImageUrlHttps),
        );
        expect((fresh.inFeed, fresh.maxPostsPerLoad), (false, 3));
      }
    });

    test('the same picture at another size is no change', () {
      for (final size in ['400x400', 'bigger', 'mini', 'reasonably_small']) {
        expect(refreshedSubscriptions(stored, [_sent('1', picture: _picture('1', size))]), isEmpty);
      }
    });

    test('ignores accounts not followed and what X left out', () {
      expect(refreshedSubscriptions(stored, [_sent('2', name: 'Someone else')]), isEmpty);
      expect(refreshedSubscriptions(stored, [_sent('1', name: '  ', picture: '')]), isEmpty);

      final unnamed = UserWithExtra()
        ..idStr = '1'
        ..screenName = 'renamed';
      final fresh = refreshedSubscriptions(stored, [unnamed])['1']!;
      expect(
        (fresh.screenName, fresh.name, fresh.profileImageUrlHttps),
        ('renamed', 'Name 1', _picture('1', 'normal')),
      );
    });
  });

  test('a change of case is the same handle', () {
    final stored = _followed('1');
    UserSubscription fresh(String handle) => refreshedSubscriptions([stored], [_sent('1', screenName: handle)])['1']!;

    expect(subscriptionRenamed(stored, fresh('Handle_1')), isFalse);
    expect(subscriptionRenamed(stored, fresh('renamed')), isTrue);
  });

  test('withProfiles changes only the handle, name and picture', () {
    final fresh = refreshedSubscriptions([_followed('1')], [_sent('1', name: 'New name')]);
    final changedSince = <Subscription>[_followed('1', inFeed: false), _followed('2')];

    final updated = withProfiles(changedSince, fresh);

    expect(
      [for (final subscription in updated) (subscription.name, subscription.inFeed)],
      [('New name', false), ('Name 2', true)],
    );
    expect(identical(updated[1], changedSince[1]), isTrue);
  });

  group('SubscriptionsModel.refreshProfiles', () {
    late SubscriptionsModel model;
    late int reloads;

    setUpAll(() async {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
      final directory = await Directory.systemTemp.createTemp('xta_subscription_profiles');
      await databaseFactory.setDatabasesPath(directory.path);
      await Repository().migrate();
    });

    setUp(() async {
      final prefs = PrefServiceCache(
        cache: {
          optionSubscriptionGroupsOrderByField: 'name',
          optionSubscriptionGroupsOrderByAscending: true,
          optionSubscriptionOrderByField: 'name',
          optionSubscriptionOrderByAscending: true,
          optionSubscriptionOrderCustom: '',
          optionPluginPixivGroupSubscriptions: '[]',
          optionPluginHnFollows: '[]',
        },
      );
      model = SubscriptionsModel(prefs, GroupsModel(prefs));
      final database = await Repository.writable();
      await database.delete(tableSubscription);
      for (final id in ['1', '2']) {
        await model.toggleSubscribe(_followed(id), false);
      }
      reloads = 0;
      model.addReloadListener('test', () => reloads++);
    });

    tearDown(() => model.destroy());

    Future<Map<String, Map<String, Object?>>> rows() async => {
      for (final row in await (await Repository.readOnly()).query(tableSubscription)) row['id'] as String: row,
    };

    test('saves a new name in place, without rebuilding the feeds', () async {
      await model.refreshProfiles([_sent('1', name: 'New name'), _sent('2')]);

      final saved = await rows();
      expect((saved['1']!['name'], saved['2']!['name']), ('New name', 'Name 2'));
      expect(model.state.map((subscription) => subscription.name), containsAll(['New name', 'Name 2']));
      expect(reloads, 0);
    });

    test('a new handle reloads the subscriptions and the feeds', () async {
      await model.refreshProfiles([_sent('1', screenName: 'renamed')]);

      expect((await rows())['1']!['screen_name'], 'renamed');
      expect(model.state.map((subscription) => subscription.screenName), contains('renamed'));
      expect(reloads, 1);
    });

    test('nothing new changes nothing', () async {
      final before = model.state;

      await model.refreshProfiles([_sent('1', picture: _picture('1', '400x400')), _sent('3', name: 'Not followed')]);

      expect(identical(model.state, before), isTrue);
      expect(reloads, 0);
    });
  });
}
