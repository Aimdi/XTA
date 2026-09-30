import 'dart:convert';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/reading/reader_preference_writes.dart';

const feedAppearancePreferenceKey = 'reader.feedAppearance';
const feedAppearanceMaxEntries = 1000;
const feedAppearanceMaxBytes = 2 * 1024 * 1024;

enum FeedPreset { compact, gallery, reading }

class FeedIdentity {
  final String source;
  final String nativeId;
  const FeedIdentity(this.source, this.nativeId);
  String get encoded => jsonEncode([source, nativeId]);
  bool get valid => source.isNotEmpty && source.length <= 128 && nativeId.isNotEmpty && nativeId.length <= 2048;

  static FeedIdentity? decode(String encoded) {
    if (encoded.length > 13100) return null;
    try {
      final pair = jsonDecode(encoded);
      if (pair is! List || pair.length != 2 || pair[0] is! String || pair[1] is! String) return null;
      final feed = FeedIdentity(pair[0], pair[1]);
      return feed.valid ? feed : null;
    } catch (_) {
      return null;
    }
  }

  @override
  bool operator ==(Object other) => other is FeedIdentity && source == other.source && nativeId == other.nativeId;
  @override
  int get hashCode => Object.hash(source, nativeId);
}

class FeedAppearance {
  final bool? counts;
  final bool? linkPreviews;
  final bool? media;
  final FeedPreset? preset;
  const FeedAppearance({this.counts, this.linkPreviews, this.media, this.preset});
  bool get inherits => counts == null && linkPreviews == null && media == null && preset == null;
  FeedAppearance copyWith({bool? counts, bool? linkPreviews, bool? media, FeedPreset? preset}) => FeedAppearance(
    counts: counts ?? this.counts,
    linkPreviews: linkPreviews ?? this.linkPreviews,
    media: media ?? this.media,
    preset: preset ?? this.preset,
  );

  Map<String, Object> toJson() => {
    'counts': ?counts,
    'linkPreviews': ?linkPreviews,
    'media': ?media,
    if (preset != null) 'preset': preset!.name,
  };

  static FeedAppearance? fromJson(Object? raw) {
    if (raw is! Map || raw.keys.any((key) => !{'counts', 'linkPreviews', 'media', 'preset'}.contains(key))) return null;
    for (final key in ['counts', 'linkPreviews', 'media']) {
      if (raw[key] != null && raw[key] is! bool) return null;
    }
    final preset = FeedPreset.values.where((value) => value.name == raw['preset']).firstOrNull;
    if (raw['preset'] != null && preset == null) return null;
    return FeedAppearance(
      counts: raw['counts'],
      linkPreviews: raw['linkPreviews'],
      media: raw['media'],
      preset: preset,
    );
  }
}

typedef FeedAppearanceWrite = Future<bool> Function(String key, String value);

/// Shared ownership follows the preference service, never an appearance route.
class FeedAppearanceStore extends Store<Map<FeedIdentity, FeedAppearance>> {
  static final _instances = Expando<FeedAppearanceStore>();
  final BasePrefService prefs;
  final FeedAppearanceWrite _write;
  final Map<FeedIdentity, String> _labels = {};
  bool _closed = false;

  FeedAppearanceStore(this.prefs, {FeedAppearanceWrite? write})
    : _write = write ?? ((key, value) => ReaderPreferenceWrites.putString(prefs, key, value)),
      super(_read(prefs));

  static FeedAppearanceStore forPrefs(BasePrefService prefs) => _instances[prefs] ??= FeedAppearanceStore(prefs);
  FeedAppearance appearance(FeedIdentity feed) => state[feed] ?? const FeedAppearance();
  Iterable<FeedIdentity> get knownFeeds => {..._labels.keys, ...state.keys};
  String? labelFor(FeedIdentity feed) => _labels[feed];
  void rememberLabel(FeedIdentity feed, String label) {
    if (_closed || !feed.valid || label.trim().isEmpty) return;
    if (_labels.length >= feedAppearanceMaxEntries && !_labels.containsKey(feed)) _labels.remove(_labels.keys.first);
    _labels[feed] = label;
  }

  static Map<FeedIdentity, FeedAppearance> _read(BasePrefService prefs) {
    prefs.makeSecret(feedAppearancePreferenceKey);
    try {
      final raw = prefs.get<Object>(feedAppearancePreferenceKey);
      if (raw is! String || raw.length > feedAppearanceMaxBytes || utf8.encode(raw).length > feedAppearanceMaxBytes) {
        return const {};
      }
      final parsed = jsonDecode(raw);
      if (parsed is! Map || parsed['v'] != 1 || parsed['feeds'] is! Map) return const {};
      final result = <FeedIdentity, FeedAppearance>{};
      for (final entry in (parsed['feeds'] as Map).entries) {
        final feed = entry.key is String ? FeedIdentity.decode(entry.key) : null;
        final appearance = FeedAppearance.fromJson(entry.value);
        if (feed != null && appearance != null && !appearance.inherits) result[feed] = appearance;
        if (result.length == feedAppearanceMaxEntries) break;
      }
      return Map.unmodifiable(result);
    } catch (_) {
      return const {};
    }
  }

  Future<bool> save(FeedIdentity feed, FeedAppearance value) => modify(feed, (_) => value);

  Future<bool> modify(FeedIdentity feed, FeedAppearance Function(FeedAppearance) edit) {
    if (_closed || !feed.valid) return Future.value(false);
    return ReaderPreferenceWrites.enqueue(prefs, () async {
      if (_closed) return false;
      try {
        final value = edit(appearance(feed));
        final next = {...state};
        if (value.inherits) {
          next.remove(feed);
        } else {
          next[feed] = value;
        }
        if (next.length > feedAppearanceMaxEntries) return false;
        final payload = jsonEncode({
          'v': 1,
          'feeds': {for (final entry in next.entries) entry.key.encoded: entry.value.toJson()},
        });
        if (utf8.encode(payload).length > feedAppearanceMaxBytes ||
            !await _write(feedAppearancePreferenceKey, payload)) {
          return false;
        }
        if (_closed) return false;
        update(Map.unmodifiable(next));
        return true;
      } catch (_) {
        return false;
      }
    });
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    if (identical(_instances[prefs], this)) _instances[prefs] = null;
    await ReaderPreferenceWrites.drain(prefs);
    await super.destroy();
  }
}
