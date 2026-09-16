import 'package:html/parser.dart' as html;
import 'package:xta/offline/offline_models.dart';
import 'package:xta/saved/saved_content_index.dart';

List<OfflineMediaSource> offlineMediaOf(SavedContent content) {
  final mastodon = content.mastodon;
  if (mastodon != null) return mastodon.images.map(OfflineMediaSource.new).toList();
  final reddit = content.reddit;
  if (reddit != null) {
    if (reddit.videoFallbackUrl != null) return [OfflineMediaSource(reddit.videoFallbackUrl!, video: true)];
    if (reddit.galleryImages.isNotEmpty) return reddit.galleryImages.map(OfflineMediaSource.new).toList();
    final uri = Uri.tryParse(reddit.url ?? '');
    if (uri != null && RegExp(r'\.(jpe?g|png|gif|webp|avif)$', caseSensitive: false).hasMatch(uri.path)) {
      return [OfflineMediaSource(uri.toString())];
    }
    return const [];
  }
  final tweet = content.tweet;
  final attachments = tweet?.extendedEntities?.media ?? tweet?.entities?.media;
  if (attachments == null) return const [];
  return [
    for (final media in attachments)
      if (media.type == 'photo' && (media.mediaUrlHttps ?? media.mediaUrl) != null)
        OfflineMediaSource(media.mediaUrlHttps ?? media.mediaUrl!)
      else if (media.videoInfo?.variants != null)
        ..._video(
          media.videoInfo!.variants!
              .where((v) => v.contentType == 'video/mp4')
              .map((v) => (url: v.url, bitrate: v.bitrate ?? 0))
              .toList(),
        ),
  ];
}

List<OfflineMediaSource> _video(List<({String? url, int bitrate})> variants) {
  variants.sort((a, b) => b.bitrate.compareTo(a.bitrate));
  for (final variant in variants) {
    if (variant.url != null) return [OfflineMediaSource(variant.url!, video: true)];
  }
  return const [];
}

bool hasOfflineMedia(SavedContent content) => offlineMediaOf(content).isNotEmpty;

bool offlineSavedSensitive(SavedContent content) =>
    content.tweet?.possiblySensitive == true ||
    content.mastodon?.sensitive == true ||
    (content.mastodon?.spoilerText.isNotEmpty ?? false) ||
    content.reddit?.over18 == true ||
    content.reddit?.spoiler == true;

String offlineSavedTitle(SavedContent content) {
  final title = content.reddit?.title ?? content.mastodon?.text ?? content.tweet?.fullText ?? content.tweet?.text ?? '';
  final plain = html.parseFragment(title).text ?? '';
  return plain.length > 300 ? '${plain.substring(0, 300)}…' : plain;
}

String offlineSavedSource(SavedContent content) =>
    content.mastodon?.acct ?? content.reddit?.subreddit ?? content.tweet?.user?.screenName ?? '';

String? offlineSavedUrl(SavedContent content) {
  if (content.mastodon != null) return content.mastodon!.url;
  if (content.reddit != null) return 'https://www.reddit.com${content.reddit!.permalink}';
  final id = content.tweet?.idStr;
  return id == null ? null : 'https://x.com/i/status/$id';
}
