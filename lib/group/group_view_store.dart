import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';

typedef GroupRouteSelection = ({String id, String name});

class GroupRouteStore extends Store<GroupRouteSelection> {
  GroupRouteStore(super.initialState);

  void switchTo(SubscriptionGroup group) {
    update((id: group.id, name: group.name));
  }
}

class GroupMediaModeStore extends Store<bool> {
  GroupMediaModeStore(super.initialState);

  void toggle() => update(!state);
}

class GroupPreviewStore extends Store<List<TweetChain>?> {
  GroupPreviewStore() : super(null);

  void show(List<TweetChain> chains) => update(chains);
}
