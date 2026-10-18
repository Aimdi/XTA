import 'dart:convert';

import 'package:xta/media/podcast_checkpoint.dart';
import 'package:xta/utils/json.dart';

const podcastPlacesPreference = 'plugin.substack.podcast.places';
const podcastPlacesLimit = 50;

typedef PodcastPlace = ({Duration position, Duration duration});

/// Where each recent episode was left, newest first, by its URL. The episode
/// playing now is in the checkpoint; this is what lets another one resume.
class PodcastPlaces {
  final Map<String, PodcastPlace> _places;

  const PodcastPlaces._(this._places);

  static const empty = PodcastPlaces._({});

  PodcastPlace? of(String url) => _places[url];

  int get length => _places.length;

  /// With [url] left at [position], first, and the oldest beyond the limit dropped.
  PodcastPlaces leftAt(String url, Duration position, Duration duration) {
    if (!PodcastCheckpoint.validUrl(url) || position <= Duration.zero) return without(url);
    final rest = Map.of(_places)..remove(url);
    return PodcastPlaces._({
      url: (position: position, duration: duration),
      for (final entry in rest.entries.take(podcastPlacesLimit - 1)) entry.key: entry.value,
    });
  }

  PodcastPlaces without(String url) => _places.containsKey(url) ? PodcastPlaces._(Map.of(_places)..remove(url)) : this;

  String encode() => jsonEncode([
    for (final MapEntry(key: url, value: place) in _places.entries)
      {'url': url, 'position': place.position.inMilliseconds, 'duration': place.duration.inMilliseconds},
  ]);

  /// The places in [raw]; a damaged entry is skipped, a damaged list is empty.
  static PodcastPlaces decode(String? raw) {
    if (raw == null || raw.isEmpty || raw.length > 262144) return empty;
    try {
      final places = <String, PodcastPlace>{};
      for (final entry in Json(jsonDecode(raw)).list) {
        final url = entry['url'].string ?? '';
        final position = entry['position'].integer ?? -1;
        final duration = entry['duration'].integer ?? 0;
        if (!PodcastCheckpoint.validUrl(url) || position <= 0 || duration < 0 || places.containsKey(url)) continue;
        places[url] = (position: Duration(milliseconds: position), duration: Duration(milliseconds: duration));
        if (places.length == podcastPlacesLimit) break;
      }
      return PodcastPlaces._(places);
    } catch (_) {
      return empty;
    }
  }
}
