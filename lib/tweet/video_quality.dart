/// One selectable progressive MP4 variant; [label] is e.g. `720p`.
class TweetVideoQuality {
  final String url;
  final String label;

  const TweetVideoQuality(this.url, this.label);
}

/// The file a download saves: the variant [playingUrl] when it is one of the
/// [qualities], so the reader keeps the quality they are watching, otherwise
/// [bestUrl] — an HLS playlist or an unknown stream cannot be saved as a file.
String? downloadUrlFor(
  String? playingUrl,
  List<TweetVideoQuality> qualities,
  String? bestUrl,
) => qualities.any((q) => q.url == playingUrl) ? playingUrl : bestUrl;
