import 'dart:ui';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/database/repository.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/user.dart';
import 'package:quax/utils/iterables.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';

class SubscriptionsModel extends Store<List<Subscription>> {
  static final log = Logger('SubscriptionsModel');

  final BasePrefService prefs;
  final GroupsModel groupModel;
  final Map<String, VoidCallback> _onSubscriptionsReloaded = {};

  SubscriptionsModel(this.prefs, this.groupModel) : super([]);

  void addReloadListener(String key, VoidCallback callback) {
    _onSubscriptionsReloaded[key] = callback;
  }

  void removeReloadListener(String key) {
    _onSubscriptionsReloaded.remove(key);
  }

  Future<void> reloadSubscriptions() async {
    log.info('Listing subscriptions');

    await execute(() async {
      var database = await Repository.readOnly();

      String orderCustom = prefs.get(optionSubscriptionOrderCustom);
      bool orderByAscending = prefs.get(optionSubscriptionOrderByAscending);
      String orderByField = prefs.get(optionSubscriptionOrderByField);

      List<Subscription> users = (await database.query(tableSubscription)).map((e) => UserSubscription.fromMap(e)).toList();

      List<Subscription> searches = (await database.query(tableSearchSubscription)).map((e) => SearchSubscription.fromMap(e)).toList();

      // Followed Substack publications are subscriptions too, so they appear in
      // this list and can be picked as group members like anyone else.
      List<Subscription> publications =
          (await database.query(tableSubstackSubscription)).map((e) => SubstackSubscription.fromMap(e)).toList();

      List<Subscription> subreddits =
          (await database.query(tableRedditSubscription)).map((e) => RedditSubscription.fromMap(e)).toList();

      List<Subscription> lst = [...users, ...searches, ...publications, ...subreddits];
      
      // Use efficient sorting with custom order support
      if (orderCustom.isEmpty) {
        // Standard sorting - O(n log n) with comparator
        return lst.sorted((a, b) {
          var one = orderByAscending ? a : b;
          var two = orderByAscending ? b : a;

          switch (orderByField) {
            case 'name':
              return one.name.toLowerCase().compareTo(two.name.toLowerCase());
            case 'screen_name':
              return one.screenName.toLowerCase().compareTo(two.screenName.toLowerCase());
            case 'created_at':
              return one.createdAt.compareTo(two.createdAt);
            default:
              return one.name.toLowerCase().compareTo(two.name.toLowerCase());
          }
        }).toList();
      }
      else {
        // Custom order - O(n*m) where m is custom list length
        // This is more efficient than the previous O(n^2) approach
        List<Subscription> newLst = [];
        final customScreenNames = orderCustom.split(',');
        
        // First pass: add custom ordered subscriptions
        for (String screenName in customScreenNames) {
          // Find and remove from lst in O(n) - but only for custom ordered items
          int index = lst.indexWhere((e) => e.screenName == screenName);
          if (index >= 0) {
            newLst.add(lst.removeAt(index));
          }
        }
        
        // Second pass: add remaining subscriptions
        if (lst.isNotEmpty) {
          newLst.addAll(lst);
        }
        
        // Only update preferences if the order actually changed
        final newOrder = newLst.map((s) => s.screenName).join(',');
        if (newOrder != orderCustom) {
          await prefs.set(optionSubscriptionOrderCustom, newOrder);
        }
        return newLst;
      }
    });
    for(final callback in _onSubscriptionsReloaded.values) {
      callback();
    }
  }

  Future<void> _toggleSearchSubscribe(SearchSubscription user, bool currentlyFollowed) async {
    var database = await Repository.writable();

    await execute(() async {
      if (currentlyFollowed) {
        await database.delete(tableSearchSubscription, where: 'id = ?', whereArgs: [user.id]);
        await database.delete(tableSearchSubscriptionGroupMember, where: 'search_id = ?', whereArgs: [user.id]);

        state.removeWhere((e) => e.id == user.id);
      } else {
        database.insert(tableSearchSubscription, {
          'id': user.id,
        });
      }

      return state;
    });
  }

  Future<void> _toggleUserSubscribe(UserSubscription user, bool currentlyFollowed) async {
    var database = await Repository.writable();

    await execute(() async {
      if (currentlyFollowed) {
        await database.delete(tableSubscription, where: 'id = ?', whereArgs: [user.id]);
        await database.delete(tableSubscriptionGroupMember, where: 'profile_id = ?', whereArgs: [user.id]);

        state.removeWhere((e) => e.id == user.id);
      } else {
        database.insert(tableSubscription, {
          'id': user.id,
          'screen_name': user.screenName,
          'name': user.name,
          'profile_image_url_https': user.profileImageUrlHttps,
          'verified': user.verified ? 1 : 0
        });
      }

      return state;
    });
  }

  /// Refreshes a stored subscription whose account was renamed, using the
  /// fresh profile data. The caller reloads the list when it is done.
  Future<void> repairSubscription(UserSubscription user, UserWithExtra fresh) async {
    var database = await Repository.writable();

    await database.update(
        tableSubscription,
        {
          'screen_name': fresh.screenName,
          'name': fresh.name,
          'profile_image_url_https': fresh.profileImageUrlHttps,
          'verified': (fresh.verified ?? false) ? 1 : 0,
        },
        where: 'id = ?',
        whereArgs: [user.id]);
  }

  Future<void> removeSubscriptions(List<UserSubscription> users) async {
    var database = await Repository.writable();

    await execute(() async {
      for (final user in users) {
        await database.delete(tableSubscription, where: 'id = ?', whereArgs: [user.id]);
        await database.delete(tableSubscriptionGroupMember, where: 'profile_id = ?', whereArgs: [user.id]);
      }

      await reloadSubscriptions();

      return state;
    });

    await groupModel.reloadGroups();
  }

  Future<void> toggleSubscribe(Subscription user, bool currentlyFollowed) async {
    if (user is UserSubscription) {
      await _toggleUserSubscribe(user, currentlyFollowed);
    } else if (user is SearchSubscription) {
      await _toggleSearchSubscribe(user, currentlyFollowed);
    }

    await groupModel.reloadGroups();
  }

  Future<void> toggleInFeed(Subscription user, bool wasInFeed) async {
    var database = await Repository.writable();
    await execute(() async {
      database.update(tableSubscription, {
        'in_Feed': wasInFeed ? 0 : 1
      }, where: 'id = ?', whereArgs: [user.id]);

      await reloadSubscriptions();

      return state;
    });
  }

  Future<void> changeOrderSubscriptionsBy(String? value) async {
    await execute(() async {
      await prefs.set(optionSubscriptionOrderCustom, '');
      await prefs.set(optionSubscriptionOrderByField, value ?? 'name');
      await reloadSubscriptions();

      return state;
    });
  }

  Future<void> toggleOrderSubscriptionsAscending() async {
    await execute(() async {
      await prefs.set(optionSubscriptionOrderCustom, '');
      await prefs.set(optionSubscriptionOrderByAscending, !prefs.get(optionSubscriptionOrderByAscending));
      await reloadSubscriptions();

      return state;
    });
  }
}
