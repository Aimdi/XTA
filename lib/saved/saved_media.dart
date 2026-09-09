import 'package:dart_twitter_api/twitter_api.dart' show Media;
import 'package:xta/client/client.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/plugins/mastodon/mastodon_archive.dart';
import 'package:xta/plugins/bluesky/bluesky_archive.dart';

/// Reuse the existing download/upload pipeline for a plugin's saved attachments.
List<Media> mediaOfSavedContent(Map<String, dynamic> content) {
  final plugin = PluginLinkPost.fromArchive(content);
  if (plugin != null) return [
    for (var i = 0; i < plugin.images.length; i++) Media.fromJson({
      'id_str': '${plugin.source}:${plugin.url}:$i', 'type': 'photo', 'media_url_https': plugin.images[i],
    }),
  ];
  final bluesky = blueskyPostFromArchive(content);
  if (bluesky != null) return [
    for (var i = 0; i < bluesky.mediaItems.length; i++)
      if (!bluesky.mediaItems[i].isVideo) Media.fromJson({
        'id_str': '${blueskyArchiveId(bluesky)}:$i', 'type': 'photo',
        'media_url_https': bluesky.mediaItems[i].url,
      }),
  ];
  final mastodon = mastodonPostFromArchive(content);
  if (mastodon != null)
    return [
      for (var i = 0; i < mastodon.images.length; i++)
        Media.fromJson({
          'id_str': '${mastodonArchiveId(mastodon)}:$i',
          'type': 'photo',
          'media_url_https': mastodon.images[i],
        }),
    ];
  final tweet = TweetWithCard.fromJson(content);
  return tweet.extendedEntities?.media ?? tweet.entities?.media ?? const [];
}
