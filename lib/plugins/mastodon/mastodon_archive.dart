import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_snapshot.dart';
import 'package:xta/utils/json.dart';

/// Status ids are local to an instance; the canonical URL disambiguates them.
String mastodonArchiveId(MastodonPost post) => 'mastodon:${post.url}';

Map<String, dynamic> mastodonArchiveBlob(MastodonPost post) => {
  'xtaPlugin': 'mastodon',
  'post': mastodonPostSnapshot(post),
};

MastodonPost? mastodonPostFromArchive(Object? value) {
  final json = Json(value);
  return json['xtaPlugin'].string == 'mastodon' ? mastodonPostFromSnapshot(json['post'].raw) : null;
}

String mastodonArchiveHaystack(MastodonPost post) => [
  post.text,
  post.authorName,
  post.acct,
  post.spoilerText,
  if (post.quote case final quote?) quote.text,
  if (post.linkCard case final card?) ...[card.title ?? '', card.description ?? ''],
].join('\n').toLowerCase();
