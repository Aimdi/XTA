import 'dart:async';
import 'dart:convert';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_snapshot.dart';
import 'package:xta/utils/json.dart';

const mastodonReadingPreference = 'plugin.mastodon.reading.v1';
const mastodonReadingLimit = 144;

class MastodonReadPoint {
  final List<MastodonPost> posts;
  final List<MastodonTrendingTag> tags;
  final String anchor;
  final double leading;
  final String? instance;
  const MastodonReadPoint({
    required this.posts,
    this.tags = const [],
    required this.anchor,
    this.leading = 0,
    this.instance,
  });

  Map<String, Object?> toJson() => {
    'posts': posts.map(mastodonPostSnapshot).toList(),
    'anchor': anchor,
    'leading': leading,
    'instance': instance,
    'tags': [
      for (final tag in tags.take(20)) {'name': tag.name, 'url': tag.url, 'uses': tag.uses},
    ],
  };

  static MastodonReadPoint? parse(Object? value) {
    final json = Json(value);
    final posts = [
      for (final item in json['posts'].list.take(mastodonReadingLimit)) ?mastodonPostFromSnapshot(item.raw),
    ];
    if (posts.isEmpty) return null;
    final rawLeading = json['leading'].raw;
    final leading = rawLeading is num && rawLeading.isFinite ? rawLeading.toDouble().clamp(-10000.0, 10000.0) : 0.0;
    return MastodonReadPoint(
      posts: posts,
      anchor: json['anchor'].string ?? posts.first.url,
      leading: leading,
      instance: json['instance'].string,
      tags: [
        for (final tag in json['tags'].list.take(20))
          if ((tag['name'].string ?? '').isNotEmpty)
            MastodonTrendingTag(name: tag['name'].string!, url: tag['url'].string, uses: tag['uses'].integer ?? 0),
      ],
    );
  }
}

class MastodonReadingState {
  final int tab;
  final bool people;
  final Map<String, MastodonReadPoint> points;
  const MastodonReadingState({this.tab = 0, this.people = false, this.points = const {}});
}

/// Bounded text snapshots. Media URLs are retained; media downloads are not implied.
class MastodonReadingStore extends Store<MastodonReadingState> {
  final BasePrefService prefs;
  String scope;
  Timer? _timer;
  Future<void> _writes = Future.value();
  bool _closed = false;
  final _diskPoints = <String>{};
  final _origins = <String, MastodonReadPoint?>{};
  final _positioned = <String>{};
  bool get enabled =>
      !prefs.getKeys().contains(optionFeedReadingPosition) || prefs.get(optionFeedReadingPosition) != false;
  MastodonReadingStore(this.prefs, this.scope) : super(const MastodonReadingState()) {
    try {
      if (!enabled) return;
      final raw = prefs.getKeys().contains(mastodonReadingPreference)
          ? prefs.get<String>(mastodonReadingPreference)
          : null;
      if (raw == null || raw.length > 3000000) return;
      final json = Json(jsonDecode(raw));
      if (json['scope'].string != scope || json['version'].integer != 1) return;
      final points = <String, MastodonReadPoint>{};
      for (final surface in ['home', 'client']) {
        for (var tab = 0; tab < 4; tab++) {
          final key = '$surface:$tab';
          final point = MastodonReadPoint.parse(json['points'][key].raw);
          if (point != null) points[key] = point;
        }
      }
      update(
        MastodonReadingState(
          tab: (json['tab'].integer ?? 0).clamp(0, 3),
          people: json['people'].boolean ?? false,
          points: points,
        ),
      );
      _diskPoints.addAll(points.keys);
    } catch (_) {
      /* A broken snapshot must not prevent opening the reader. */
    }
  }

  void changeScope(String value) {
    if (scope == value) return;
    scope = value;
    _diskPoints.clear();
    _origins.clear();
    _positioned.clear();
    update(const MastodonReadingState());
    _schedule();
  }

  ({MastodonReadPoint? point, bool position}) layoutPoint(String slot) {
    final point = _origins.putIfAbsent(slot, () => _diskPoints.contains(slot) ? state.points[slot] : null);
    return (point: point, position: _positioned.add(slot));
  }

  void select(int tab, bool people) {
    if (_closed || (tab == state.tab && people == state.people)) return;
    update(MastodonReadingState(tab: tab, people: people, points: state.points));
    _schedule();
  }

  void remember(String key, MastodonReadPoint point) {
    if (_closed || point.posts.isEmpty) return;
    update(MastodonReadingState(tab: state.tab, people: state.people, points: {...state.points, key: point}));
    _schedule();
  }

  void _schedule() {
    _timer?.cancel();
    _timer = Timer(const Duration(milliseconds: 350), flush);
  }

  Future<void> flush() {
    _timer?.cancel();
    if (!enabled) {
      _writes = _writes
          .then((_) async {
            await prefs.set(mastodonReadingPreference, '');
          })
          .catchError((Object _) {});
      return _writes;
    }
    final points = <String, Object?>{};
    var budget = 2500000;
    for (final entry in state.points.entries.toList().reversed) {
      final value = entry.value.toJson();
      final length = jsonEncode(value).length;
      if (length > budget) continue;
      budget -= length;
      points[entry.key] = value;
    }
    final raw = jsonEncode({'version': 1, 'scope': scope, 'tab': state.tab, 'people': state.people, 'points': points});
    // Queue writes so a slower old write cannot replace the latest position.
    _writes = _writes
        .then((_) async {
          await prefs.set(mastodonReadingPreference, raw);
        })
        .catchError((Object _) {});
    return _writes;
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    await flush();
    await super.destroy();
  }
}
