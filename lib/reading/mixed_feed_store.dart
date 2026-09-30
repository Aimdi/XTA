import 'package:pref/pref.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/reader_preference_list.dart';

/// The reader's mixes, in the order they sit on the Home strip. Saves report failures honestly.
class MixedFeedStore extends ReaderPreferenceListStore<MixedFeedDefinition> {
  static final _instances = Expando<MixedFeedStore>();

  MixedFeedStore(BasePrefService prefs)
    : super(
        prefs,
        mixedFeedPreferenceKey,
        'mixes',
        readReaderPreferenceList(
          prefs,
          mixedFeedPreferenceKey,
          'mixes',
          max: mixedFeedMaxMixes,
          decode: MixedFeedDefinition.fromJson,
          idOf: (mix) => mix.id,
        ),
      );

  static MixedFeedStore forPrefs(BasePrefService prefs) => _instances[prefs] ??= MixedFeedStore(prefs);

  MixedFeedDefinition? byId(String id) => state.where((mix) => mix.id == id).firstOrNull;

  @override
  Map<String, Object?> encodeItem(MixedFeedDefinition item) => item.toJson();

  /// Adds [mix], or replaces the saved mix with its id in place.
  Future<bool> save(MixedFeedDefinition mix) => modify((mixes) {
    if (mix.problem != null) return null;
    final index = mixes.indexWhere((old) => old.id == mix.id);
    if (index < 0) return mixes.length >= mixedFeedMaxMixes ? null : [...mixes, mix];
    return [...mixes]..[index] = mix;
  });

  Future<bool> remove(String id) => modify((mixes) => mixes.where((mix) => mix.id != id).toList());

  /// Moves the mix at [from] so it ends up at [to].
  Future<bool> move(int from, int to) => modify((mixes) {
    if (from < 0 || from >= mixes.length || to < 0 || to >= mixes.length) return null;
    final next = [...mixes];
    return next..insert(to, next.removeAt(from));
  });

  @override
  Future<void> destroy() async {
    if (identical(_instances[prefs], this)) _instances[prefs] = null;
    await super.destroy();
  }
}
