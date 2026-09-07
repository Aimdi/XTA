import 'package:flutter_triple/flutter_triple.dart';

class HomeFeedViewState {
  final String? sourceId;
  final int followingEpoch;
  final int forYouEpoch;
  final int stripEpoch;
  final bool followingMediaOnly;
  const HomeFeedViewState({
    this.sourceId,
    this.followingEpoch = 0,
    this.forYouEpoch = 0,
    this.stripEpoch = 0,
    this.followingMediaOnly = false,
  });
  HomeFeedViewState copyWith({
    String? sourceId,
    int? followingEpoch,
    int? forYouEpoch,
    int? stripEpoch,
    bool? followingMediaOnly,
  }) => HomeFeedViewState(
    sourceId: sourceId ?? this.sourceId,
    followingEpoch: followingEpoch ?? this.followingEpoch,
    forYouEpoch: forYouEpoch ?? this.forYouEpoch,
    stripEpoch: stripEpoch ?? this.stripEpoch,
    followingMediaOnly: followingMediaOnly ?? this.followingMediaOnly,
  );
}

/// Only presentation invalidation belongs here; the existing feed controllers
/// and account/group stores continue to own fetching and filtering.
class HomeFeedViewStore extends Store<HomeFeedViewState> {
  HomeFeedViewStore() : super(const HomeFeedViewState());
  void selectSource(String id, {bool external = false}) =>
      update(state.copyWith(sourceId: id, stripEpoch: state.stripEpoch + (external ? 1 : 0)));
  void refreshFollowing() => update(state.copyWith(followingEpoch: state.followingEpoch + 1));
  void refreshForYou() => update(state.copyWith(forYouEpoch: state.forYouEpoch + 1));
  void refreshStrip() => update(state.copyWith(stripEpoch: state.stripEpoch + 1));
  void setFollowingMediaOnly(bool value) => update(state.copyWith(followingMediaOnly: value));
}
