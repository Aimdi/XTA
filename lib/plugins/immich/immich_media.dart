/// Which files of a saved post are worth sending to a photo library, and what
/// to call them.
///
/// The choosing is kept free of the API models — and so of Flutter — so it can
/// be tested, in the same spirit as `AccountSelector`: picking the wrong variant
/// out of a reverse-engineered response is where this goes wrong, not in the
/// HTTP. [mediaCandidates] is the only part that touches them.
library;

import 'package:dart_twitter_api/twitter_api.dart' show Media;

/// A video variant as the response describes it.
typedef MediaVariant = ({String? url, String? contentType, int? bitrate});

/// One media entry of a post, flattened out of the client models.
class MediaCandidate {
  /// `type`: `photo`, `video` or `animated_gif`.
  final String? type;

  /// `media_url_https` — the photo itself, or a video's thumbnail. Unique per
  /// media and the same on every read, which is what makes it usable as an id.
  final String? imageUrl;

  final List<MediaVariant> variants;

  const MediaCandidate({required this.type, required this.imageUrl, this.variants = const []});
}

/// Flattens the client's media models into something testable.
List<MediaCandidate> mediaCandidates(List<Media> media) => media
    .map((m) => MediaCandidate(
          type: m.type,
          imageUrl: m.mediaUrlHttps,
          variants: (m.videoInfo?.variants ?? const [])
              .map((v) => (url: v.url, contentType: v.contentType, bitrate: v.bitrate))
              .toList(),
        ))
    .toList();

/// One file to upload, resolved to the URL that carries the original.
class UploadableMedia {
  /// Where the bytes are.
  final String url;

  /// Stable across re-saves and independent of which variant was chosen, so the
  /// upload log recognises a file it has already sent.
  final String id;

  final String fileName;
  final bool isVideo;

  const UploadableMedia({
    required this.url,
    required this.id,
    required this.fileName,
    required this.isVideo,
  });
}

/// The photos and videos of [media], at the best quality X offers.
///
/// GIFs arrive as `animated_gif` and are served as MP4 like any other video, so
/// they come through as videos — which is what they are by the time a photo
/// library has them.
List<UploadableMedia> uploadableMedia(List<MediaCandidate> media, {bool includeVideos = true}) => media
    .map((m) => switch (m.type) {
          'photo' => _photo(m),
          'video' || 'animated_gif' => includeVideos ? _video(m) : null,
          _ => null,
        })
    .nonNulls
    .toList();

UploadableMedia? _photo(MediaCandidate m) {
  final url = m.imageUrl;
  if (url == null || url.isEmpty) {
    return null;
  }
  return UploadableMedia(
    // `:orig` is the untouched upload rather than the display-sized copy, and a
    // photo library should hold the former.
    url: '$url:orig',
    id: url,
    fileName: _fileNameOf(url, fallback: 'photo.jpg'),
    isVideo: false,
  );
}

UploadableMedia? _video(MediaCandidate m) {
  final url = _bestVariant(m.variants)?.url;
  if (url == null || url.isEmpty) {
    return null;
  }
  return UploadableMedia(
    url: url,
    // The thumbnail, not the variant: which variant wins can change between
    // reads, and an id that moved with it would upload the same video again at
    // a different bitrate.
    id: m.imageUrl ?? url,
    fileName: _fileNameOf(url, fallback: 'video.mp4'),
    isVideo: true,
  );
}

/// The highest-bitrate progressive MP4. The HLS playlist is not a file that can
/// be handed to a photo library, so a variant without an MP4 type or a bitrate
/// is skipped.
MediaVariant? _bestVariant(List<MediaVariant> variants) {
  MediaVariant? best;
  for (final variant in variants) {
    if (variant.contentType != 'video/mp4' || variant.url == null || variant.bitrate == null) {
      continue;
    }
    if (best == null || variant.bitrate! > best.bitrate!) {
      best = variant;
    }
  }
  return best;
}

/// The name at the end of a media URL, without the query or the `:orig` suffix
/// X uses for sizes.
String _fileNameOf(String url, {required String fallback}) {
  final name = url.split('?').first.split('/').last.split(':').first;
  return name.isEmpty ? fallback : name;
}
