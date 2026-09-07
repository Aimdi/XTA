import 'dart:convert';

import 'package:xta/utils/json.dart';

const podcastCheckpointPreference = 'plugin.substack.podcast.v1';
const _maximumDuration = Duration(days: 7);

class PodcastCheckpoint {
  final String url;
  final String title;
  final Duration position;
  final Duration duration;

  const PodcastCheckpoint({required this.url, required this.title, required this.position, required this.duration});

  static bool validUrl(String url) {
    if (url.isEmpty || url.length > 4096) return false;
    final uri = Uri.tryParse(url);
    return uri != null && (uri.scheme == 'https' || uri.scheme == 'http') && uri.host.isNotEmpty;
  }

  String encode() => jsonEncode({
    'version': 1,
    'url': url,
    'title': title.length > 512 ? title.substring(0, 512) : title,
    'position': position.inMilliseconds.clamp(0, _maximumDuration.inMilliseconds),
    'duration': duration.inMilliseconds.clamp(0, _maximumDuration.inMilliseconds),
  });

  static PodcastCheckpoint? decode(String? raw) {
    if (raw == null || raw.isEmpty || raw.length > 16384) return null;
    try {
      final json = Json(jsonDecode(raw));
      final url = json['url'].string ?? '';
      final title = json['title'].string ?? '';
      final position = json['position'].integer ?? -1;
      final duration = json['duration'].integer ?? -1;
      if (json['version'].integer != 1 ||
          !validUrl(url) ||
          title.length > 512 ||
          position < 0 ||
          duration < 0 ||
          position > _maximumDuration.inMilliseconds ||
          duration > _maximumDuration.inMilliseconds ||
          (duration > 0 && position >= duration))
        return null;
      return PodcastCheckpoint(
        url: url,
        title: title,
        position: Duration(milliseconds: position),
        duration: Duration(milliseconds: duration),
      );
    } catch (_) {
      return null;
    }
  }
}
