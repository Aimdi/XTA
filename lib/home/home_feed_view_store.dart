import 'package:flutter_triple/flutter_triple.dart';

class HomeFeedViewState {
  final String? sourceId;
  final int followingEpoch;
  final int forYouEpoch;
  final int stripEpoch;
  final bool followingMediaOnly;
  final Set<String> collapsedSources;
  const HomeFeedViewState({
    this.sourceId,
    this.followingEpoch = 0,
    this.forYouEpoch = 0,
    this.stripEpoch = 0,
    this.followingMediaOnly = false,
    this.collapsedSources = const {},
  });
  bool get controlsVisible => !collapsedSources.contains(sourceId);
  HomeFeedViewState copyWith({
    String? sourceId,
    int? followingEpoch,
    int? forYouEpoch,
    int? stripEpoch,
    bool? followingMediaOnly,
    Set<String>? collapsedSources,
  }) => HomeFeedViewState(
    sourceId: sourceId ?? this.sourceId,
    followingEpoch: followingEpoch ?? this.followingEpoch,
    forYouEpoch: forYouEpoch ?? this.forYouEpoch,
    stripEpoch: stripEpoch ?? this.stripEpoch,
    followingMediaOnly: followingMediaOnly ?? this.followingMediaOnly,
    collapsedSources: collapsedSources ?? this.collapsedSources,
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

  void observeScroll({required double extentBefore, required double scrollExtent, required double controlsHeight}) {
    final source = state.sourceId;
    if (source == null) return;
    final collapsed = state.collapsedSources.contains(source);
    // Leave enough scrollable content after reclaiming the controls' space.
    // Otherwise resizing a short feed forces it to zero and reveals them again.
    final next = extentBefore > 0.5 &&
        (collapsed || (extentBefore >= 24 && scrollExtent > controlsHeight + 24));
    if (next == collapsed) return;
    final sources = {...state.collapsedSources};
    if (next) {
      sources.add(source);
    } else {
      sources.remove(source);
    }
    update(state.copyWith(collapsedSources: Set.unmodifiable(sources)));
  }
}
