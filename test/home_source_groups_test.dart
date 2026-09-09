import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/home_group_drawer.dart';

SubscriptionGroup group(String id, String name, {bool pinned = false}) => SubscriptionGroup(
  id: id,
  name: name,
  icon: 'rss_feed',
  color: null,
  numberOfMembers: 2,
  createdAt: DateTime.utc(2026),
  pinned: pinned,
);

void main() {
  test('legacy For you destination restores the dedicated X source', () {
    expect(feedTabFromId('foryou'), FeedTab.x);
    expect(feedTabFromId('x'), FeedTab.x);
    expect(feedTabFromId(null), FeedTab.following);
    final store = FeedTabStore(const FeedTab('foryou'));
    expect(store.state, FeedTab.x);
    store.destroy();
  });

  test('X is available without optional plugins and For you is not a Home row', () {
    final tabs = availableFeedTabsFromIds([], PrefServiceCache());
    expect(tabs.map((tab) => tab.id.id), ['following', 'x']);
    expect(FeedTab.x.icon, Icons.close);
  });

  test('drawer prioritizes pins without mutating the chosen group order', () {
    final rows = [group('a', 'Art'), group('b', 'Tech', pinned: true), group('c', 'Manga')];
    expect(drawerGroupsForQuery(rows, '').map((row) => row.id), ['b', 'a', 'c']);
    expect(rows.map((row) => row.id), ['a', 'b', 'c']);
    expect(drawerGroupsForQuery(rows, '  aRt ').single.id, 'a');
    expect(drawerGroupsForQuery(rows, 'missing'), isEmpty);
  });
}
