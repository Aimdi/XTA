import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/utils/json.dart';

/// Versioned, device-local representation shared by reading snapshots and Saved.
Map<String, Object?> mastodonPostSnapshot(MastodonPost post) => {
  'version': 1,
  'id': post.id,
  'acct': post.acct,
  'name': post.authorName,
  'avatar': post.avatarUrl,
  'text': post.text,
  'url': post.url,
  'spoiler': post.spoilerText,
  'sensitive': post.sensitive,
  'images': post.images,
  'published': post.publishedAt?.toIso8601String(),
  'edited': post.editedAt?.toIso8601String(),
  'boosted': post.boosted,
  'boostedBy': post.boostedBy,
  'boostedByAcct': post.boostedByAcct,
  'replyToAcct': post.replyToAcct,
  'replyToId': post.replyToId,
  'mentions': post.mentionAccts,
  'replies': post.repliesCount,
  'boosts': post.reblogsCount,
  'favourites': post.favouritesCount,
  if (post.quote case final quote?) 'quote': mastodonPostSnapshot(quote.asPost),
  if (post.linkCard case final card?)
    'card': {
      'url': card.url,
      'title': card.title,
      'description': card.description,
      'image': card.imageUrl,
      'provider': card.providerName,
      'type': card.type,
    },
  if (post.poll case final poll?)
    'poll': {
      'votes': poll.votesCount,
      'expired': poll.expired,
      'multiple': poll.multiple,
      'options': [
        for (final option in poll.options) {'title': option.title, 'votes': option.votes},
      ],
    },
};

MastodonPost? mastodonPostFromSnapshot(Object? value, {bool includeQuote = true}) {
  final json = Json(value);
  final id = json['id'].string ?? '';
  final acct = json['acct'].string ?? '';
  final url = json['url'].string ?? '';
  final uri = Uri.tryParse(url);
  if (json['version'].integer != 1 ||
      id.isEmpty ||
      acct.isEmpty ||
      uri == null ||
      !uri.hasAuthority ||
      (uri.scheme != 'https' && uri.scheme != 'http'))
    return null;
  final quoted = includeQuote ? mastodonPostFromSnapshot(json['quote'].raw, includeQuote: false) : null;
  final card = json['card'];
  final poll = json['poll'];
  return MastodonPost(
    id: id,
    acct: acct,
    url: url,
    authorName: json['name'].string ?? acct,
    text: json['text'].string ?? '',
    avatarUrl: json['avatar'].string,
    spoilerText: json['spoiler'].string ?? '',
    sensitive: json['sensitive'].boolean ?? false,
    images: [
      for (final image in json['images'].list)
        if (image.string != null) image.string!,
    ],
    publishedAt: DateTime.tryParse(json['published'].string ?? ''),
    editedAt: DateTime.tryParse(json['edited'].string ?? ''),
    boosted: json['boosted'].boolean ?? false,
    boostedBy: json['boostedBy'].string,
    boostedByAcct: json['boostedByAcct'].string,
    replyToAcct: json['replyToAcct'].string,
    replyToId: json['replyToId'].string,
    mentionAccts: [
      for (final mention in json['mentions'].list)
        if (mention.string != null) mention.string!,
    ],
    repliesCount: json['replies'].integer ?? 0,
    reblogsCount: json['boosts'].integer ?? 0,
    favouritesCount: json['favourites'].integer ?? 0,
    quote: quoted == null
        ? null
        : MastodonQuotedPost(
            id: quoted.id,
            acct: quoted.acct,
            authorName: quoted.authorName,
            text: quoted.text,
            url: quoted.url,
            images: quoted.images,
          ),
    linkCard: !card.exists
        ? null
        : MastodonLinkCard(
            url: card['url'].string ?? '',
            title: card['title'].string,
            description: card['description'].string,
            imageUrl: card['image'].string,
            providerName: card['provider'].string,
            type: card['type'].string,
          ),
    poll: !poll.exists
        ? null
        : MastodonPoll(
            votesCount: poll['votes'].integer ?? 0,
            expired: poll['expired'].boolean ?? false,
            multiple: poll['multiple'].boolean ?? false,
            options: [
              for (final option in poll['options'].list)
                MastodonPollOption(title: option['title'].string ?? '', votes: option['votes'].integer ?? 0),
            ],
          ),
  );
}
