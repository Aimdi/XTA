import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_model.dart';

SubscriptionGroupGet group(String name) => SubscriptionGroupGet(
  id: 'group',
  name: name,
  icon: defaultGroupIcon,
  subscriptions: [],
  includeReplies: false,
  includeRetweets: false,
  popular: false,
);

void main() {
  test('a stalled group snapshot times out and a retry can load the feed', () async {
    final stalled = Completer<SubscriptionGroupGet>();
    var reads = 0;
    final model = GroupModel(
      'group',
      readTimeout: const Duration(milliseconds: 10),
      reader: () => ++reads == 1 ? stalled.future : Future.value(group('recovered')),
    );
    addTearDown(model.destroy);

    await model.loadGroup();
    expect(model.isLoading, isFalse);
    expect(model.triple.error, isA<TimeoutException>());
    await model.loadGroup();
    expect(model.state.name, 'recovered');
    expect(model.isLoading, isFalse);
    expect(model.triple.error, isNull);

    stalled.complete(group('obsolete'));
    await Future<void>.delayed(Duration.zero);
    expect(model.state.name, 'recovered');
  });

  test('a soft reload cancels the old snapshot and preserves current results', () async {
    final stale = Completer<SubscriptionGroupGet>();
    var reads = 0;
    final model = GroupModel('group', reader: () => ++reads == 1 ? stale.future : Future.value(group('current')));
    addTearDown(model.destroy);

    final first = model.loadGroup();
    await model.loadGroup(showLoading: false);
    await first;
    expect(model.state.name, 'current');
    expect(model.isLoading, isFalse);
    stale.complete(group('stale'));
    await Future<void>.delayed(Duration.zero);
    expect(model.state.name, 'current');
  });

  test('a failed membership reload keeps an already readable group visible', () async {
    var reads = 0;
    final visible = group('visible');
    final model = GroupModel(
      'group',
      reader: () async {
        if (++reads == 2) throw TimeoutException('snapshot stalled');
        return visible;
      },
    );
    addTearDown(model.destroy);
    await model.loadGroup();
    await model.loadGroup(showLoading: false);
    expect(identical(model.state, visible), isTrue);
    expect(model.triple.error, isNull);
    expect(model.isLoading, isFalse);
  });

  test('destroy releases pending group reads and ignores late completion', () async {
    final stalled = Completer<SubscriptionGroupGet>();
    var reads = 0;
    final model = GroupModel(
      'group',
      reader: () {
        reads++;
        return stalled.future;
      },
    );
    final loading = model.loadGroup();
    await model.destroy();
    await loading.timeout(const Duration(seconds: 1));
    stalled.complete(group('closed'));
    await Future<void>.delayed(Duration.zero);
    expect(model.state.id, isEmpty);
    await model.loadGroup();
    expect(reads, 1);
  });
}
