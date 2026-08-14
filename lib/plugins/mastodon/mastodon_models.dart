import 'package:html/parser.dart' as html;
import 'package:xta/utils/json.dart';

/// Open Graph–style link preview attached to a public status (`card` in the API).
class MastodonLinkCard {
  final String url;
  final String? title;
  final String? description;
  final String? imageUrl;
  final String? providerName;
  final String? type;

  const MastodonLinkCard({
    required this.url,
    this.title,
    this.description,
    this.imageUrl,
    this.providerName,
    this.type,
  });

  bool get hasImage => imageUrl != null && imageUrl!.isNotEmpty;
}

/// A read-only poll on a public status. Voting is not sent anywhere.
class MastodonPollOption {
  final String title;
  final int votes;

  const MastodonPollOption({required this.title, this.votes = 0});
}

class MastodonPoll {
  final List<MastodonPollOption> options;
  final int votesCount;
  final bool expired;
  final bool multiple;

  const MastodonPoll({
    required this.options,
    this.votesCount = 0,
    this.expired = false,
    this.multiple = false,
  });
}

/// One public Mastodon status, as much as a card needs.
class MastodonPost {
  final String id;
  final String acct;
  final String authorName;
  final String? avatarUrl;
  final String text;
  final String spoilerText;
  final bool sensitive;
  final List<String> images;
  final DateTime? publishedAt;
  final String url;

  /// True when this card shows a boosted status (reblog unwrapped).
  final bool boosted;

  /// Display name of who boosted, when [boosted] is true.
  final String? boostedBy;

  /// Handle this status is a reply to, when the payload names one.
  final String? replyToAcct;

  /// `acct` values from the status mentions, used to resolve `@name` taps.
  final List<String> mentionAccts;

  final DateTime? editedAt;
  final MastodonQuotedPost? quote;
  final int repliesCount;
  final int reblogsCount;
  final int favouritesCount;
  final MastodonLinkCard? linkCard;
  final MastodonPoll? poll;

  const MastodonPost({
    required this.id,
    required this.acct,
    required this.authorName,
    required this.text,
    required this.url,
    this.avatarUrl,
    this.spoilerText = '',
    this.sensitive = false,
    this.images = const [],
    this.publishedAt,
    this.boosted = false,
    this.boostedBy,
    this.replyToAcct,
    this.mentionAccts = const [],
    this.editedAt,
    this.quote,
    this.repliesCount = 0,
    this.reblogsCount = 0,
    this.favouritesCount = 0,
    this.linkCard,
    this.poll,
  });

  bool get hasMedia => images.isNotEmpty;

  bool get hasSpoiler => spoilerText.trim().isNotEmpty;

  bool get edited => editedAt != null;
}

/// A quoted status shown under the card — one level, no nested quotes.
class MastodonQuotedPost {
  final String id;
  final String acct;
  final String authorName;
  final String text;
  final String url;
  final List<String> images;

  const MastodonQuotedPost({
    required this.id,
    required this.acct,
    required this.authorName,
    required this.text,
    required this.url,
    this.images = const [],
  });

  MastodonPost get asPost => MastodonPost(
    id: id,
    acct: acct,
    authorName: authorName,
    text: text,
    url: url,
    images: images,
  );
}

/// Guest search: accounts, statuses, and hashtags from one `/api/v2/search`.
class MastodonSearchPage {
  final List<MastodonProfile> accounts;
  final List<MastodonPost> posts;
  final List<MastodonTrendingTag> tags;

  const MastodonSearchPage({
    this.accounts = const [],
    this.posts = const [],
    this.tags = const [],
  });

  bool get isEmpty => accounts.isEmpty && posts.isEmpty && tags.isEmpty;
}

enum MastodonTextKind { text, mention, tag }

/// One run of a status body: plain text, `@mention`, or `#tag`.
class MastodonTextPart {
  final MastodonTextKind kind;
  final String text;
  final String value;

  const MastodonTextPart(this.kind, this.text, this.value);
}

final _mastodonEntity = RegExp(
  r'(@[A-Za-z0-9_]+(?:@[A-Za-z0-9.-]+\.[A-Za-z]{2,})?|#[^\s#@]+)',
);

/// Splits [text] into tappable mentions/tags. [mentionAccts] resolve `@name`.
List<MastodonTextPart> mastodonTextParts(
  String text, {
  List<String> mentionAccts = const [],
}) {
  if (text.isEmpty) return const [];
  final parts = <MastodonTextPart>[];
  var cursor = 0;
  for (final match in _mastodonEntity.allMatches(text)) {
    if (match.start > cursor) {
      parts.add(
        MastodonTextPart(
          MastodonTextKind.text,
          text.substring(cursor, match.start),
          '',
        ),
      );
    }
    final raw = match.group(0)!;
    if (raw.startsWith('#')) {
      parts.add(MastodonTextPart(MastodonTextKind.tag, raw, raw.substring(1)));
    } else {
      parts.add(
        MastodonTextPart(
          MastodonTextKind.mention,
          raw,
          resolveMastodonMention(raw, mentionAccts),
        ),
      );
    }
    cursor = match.end;
  }
  if (cursor < text.length) {
    parts.add(
      MastodonTextPart(MastodonTextKind.text, text.substring(cursor), ''),
    );
  }
  return parts;
}

String resolveMastodonMention(String raw, List<String> mentionAccts) {
  final handle = raw.replaceFirst(RegExp(r'^@+'), '').toLowerCase();
  if (handle.contains('@')) return handle;
  for (final acct in mentionAccts) {
    final lower = acct.toLowerCase();
    if (lower == handle || lower.startsWith('$handle@')) return lower;
  }
  return handle;
}

/// A Mastodon / Fediverse profile as the home instance reports it.
class MastodonProfile {
  /// Numeric id on the **home** instance — not portable across instances.
  final String id;
  final String acct;
  final String username;
  final String displayName;
  final String? avatarUrl;
  final String note;
  final String url;
  final int followersCount;
  final int followingCount;
  final int statusesCount;
  final bool locked;
  final bool bot;
  final List<MastodonField> fields;

  const MastodonProfile({
    required this.id,
    required this.acct,
    required this.username,
    required this.displayName,
    required this.note,
    required this.url,
    this.avatarUrl,
    this.followersCount = 0,
    this.followingCount = 0,
    this.statusesCount = 0,
    this.locked = false,
    this.bot = false,
    this.fields = const [],
  });

  factory MastodonProfile.fromJson(Object? json, {String? homeDomain}) {
    final data = Json(json);
    final username = data['username'].string?.trim() ?? '';
    final rawAcct = data['acct'].string?.trim() ?? username;
    final acct = canonicalMastodonAcct(rawAcct, homeDomain: homeDomain);
    final name = data['display_name'].string?.trim();
    final avatar =
        data['avatar'].string?.trim() ?? data['avatar_static'].string?.trim();
    final noteHtml = data['note'].string;

    return MastodonProfile(
      id: data['id'].string ?? '${data['id'].integer ?? ''}',
      acct: acct,
      username: username.isEmpty ? acct.split('@').first : username,
      displayName: (name == null || name.isEmpty) ? acct : name,
      avatarUrl: avatar == null || avatar.isEmpty ? null : avatar,
      note: mastodonHtmlToText(noteHtml),
      url: data['url'].string?.trim() ?? '',
      followersCount: data['followers_count'].integer ?? 0,
      followingCount: data['following_count'].integer ?? 0,
      statusesCount: data['statuses_count'].integer ?? 0,
      locked: data['locked'].boolean ?? false,
      bot: data['bot'].boolean ?? false,
      fields: _fieldsOf(data),
    );
  }

  MastodonAccount toAccount() =>
      MastodonAccount(acct: acct, name: displayName, avatarUrl: avatarUrl);
}

/// A profile metadata row (`fields` in the Mastodon API).
class MastodonField {
  final String name;
  final String value;

  const MastodonField({required this.name, required this.value});
}

/// An account the reader follows locally — not a Mastodon follow-graph edge.
class MastodonAccount {
  /// Canonical `user@domain` (always includes the domain).
  final String acct;
  final String name;
  final String? avatarUrl;

  const MastodonAccount({
    required this.acct,
    required this.name,
    this.avatarUrl,
  });

  MastodonAccount copyWith({String? name, String? avatarUrl}) =>
      MastodonAccount(
        acct: acct,
        name: name ?? this.name,
        avatarUrl: avatarUrl ?? this.avatarUrl,
      );
}

/// Instances the plugin can read through with nothing configured.
///
/// Chosen for reach rather than character: large, long-lived, open general
/// instances whose public API answers without a login, ordered by the size of
/// the slice of the Fediverse each one federates with. The first two are run
/// by Mastodon gGmbH itself; the rest are the biggest independent generalists
/// that have stayed up and open for years. Broad instances are the point —
/// a big instance's federated view contains what the small ones see.
///
/// This list is the *fallback*, not the strategy. Coverage of the whole
/// Fediverse comes from [mastodonInstanceCandidates] asking an account's own
/// instance first: the origin has every post its accounts ever made, which no
/// amount of federation guarantees anywhere else. The client walks the list,
/// so an instance being down or newly closed costs one failed try, never the
/// feature.
const kMastodonDefaultInstances = [
  'https://mastodon.social',
  'https://mastodon.online',
  'https://mstdn.social',
  'https://mas.to',
  'https://mastodon.world',
];

/// Every instance worth asking about [acct], best answer first.
///
/// Order is the whole design: the account's own instance (complete by
/// definition — though a Misskey-family origin will not answer the Mastodon
/// API, which is why the walk goes on), then the reader's instances in the
/// order they gave them, then the built-in defaults. Duplicates collapse to
/// their first appearance, so a reader whose home is an origin or a default
/// never asks it twice.
List<String> mastodonInstanceCandidates(
  String acct, {
  List<String> configured = const [],
}) {
  final normalisedAcct = normaliseMastodonAcct(acct) ?? acct.trim();
  final at = normalisedAcct.indexOf('@');
  final origin = at > 0
      ? normalisedAcct.substring(at + 1).trim().toLowerCase()
      : '';

  final ordered = [
    if (origin.isNotEmpty) 'https://$origin',
    ...configured,
    ...kMastodonDefaultInstances,
  ];

  final seen = <String>{};
  return [
    for (final candidate in ordered)
      if (normaliseMastodonInstance(candidate) case final instance?
          when seen.add(instance))
        instance,
  ];
}

/// Strip scheme/trailing slash; require http(s). Null when unusable.
String? normaliseMastodonInstance(String input) {
  var value = input.trim();
  if (value.isEmpty) {
    return null;
  }
  if (!value.contains('://')) {
    value = 'https://$value';
  }
  final uri = Uri.tryParse(value);
  if (uri == null || (uri.scheme != 'https' && uri.scheme != 'http')) {
    return null;
  }
  // Reject spaces / percent-encoded junk; hostnames are DNS-like for our purposes.
  if (uri.host.isEmpty ||
      uri.host.contains('%') ||
      RegExp(r'\s').hasMatch(uri.host)) {
    return null;
  }
  final port = uri.hasPort ? ':${uri.port}' : '';
  return '${uri.scheme}://${uri.host}$port';
}

/// Host of a normalised instance URL, or null.
String? mastodonInstanceDomain(String instance) {
  final uri = Uri.tryParse(instance);
  final host = uri?.host.trim().toLowerCase();
  return host == null || host.isEmpty ? null : host;
}

/// Snowflake id from a public status URL (`/@user/123` or `/users/user/statuses/123`).
///
/// Status ids are instance-local for the API, but the path segment on the
/// *origin* URL is the id that origin's `/api/v1/statuses/:id` understands —
/// which matters when the card was federated through another instance and
/// carries that other instance's id in [MastodonPost.id].
String? mastodonStatusIdFromUrl(String url) {
  final uri = Uri.tryParse(url.trim());
  if (uri == null || uri.host.isEmpty) {
    return null;
  }
  final segments = uri.pathSegments.where((e) => e.isNotEmpty).toList();
  if (segments.length >= 4 &&
      segments[0].toLowerCase() == 'users' &&
      segments[2].toLowerCase() == 'statuses') {
    final id = segments[3];
    return RegExp(r'^\d+$').hasMatch(id) ? id : null;
  }
  if (segments.length >= 2) {
    final user = segments[0].startsWith('@')
        ? segments[0].substring(1)
        : segments[0];
    final id = segments[1];
    if (user.isNotEmpty && RegExp(r'^\d+$').hasMatch(id)) {
      return id;
    }
  }
  return null;
}

/// Whether two status URLs name the same public post (ignore trailing slash / case).
bool sameMastodonStatusUrl(String a, String b) {
  String? key(String raw) {
    final uri = Uri.tryParse(raw.trim());
    if (uri == null || uri.host.isEmpty) {
      return null;
    }
    final path = uri.path.replaceAll(RegExp(r'/+$'), '');
    return '${uri.host.toLowerCase()}$path'.toLowerCase();
  }

  final left = key(a);
  final right = key(b);
  return left != null && left == right;
}

/// `user`, `@user`, `user@domain`, or `https://domain/@user` → lookup acct.
///
/// Bare local usernames are kept without a domain (the home instance resolves
/// them). Profile URLs and `user@domain` become a WebFinger-style address.
String? normaliseMastodonAcct(String input) {
  var value = input.trim();
  if (value.isEmpty) {
    return null;
  }

  final uri = Uri.tryParse(value);
  if (uri != null && uri.hasScheme && uri.host.isNotEmpty) {
    final segments = uri.pathSegments.where((e) => e.isNotEmpty).toList();
    if (segments.isEmpty) {
      return null;
    }
    var user = segments.first;
    if (user.startsWith('@')) {
      user = user.substring(1);
    }
    // /users/Name or /@Name
    if (user.toLowerCase() == 'users' && segments.length >= 2) {
      user = segments[1];
    }
    user = user.replaceFirst(RegExp(r'^@+'), '').trim();
    if (user.isEmpty) {
      return null;
    }
    return '${user.toLowerCase()}@${uri.host.toLowerCase()}';
  }

  value = value.replaceFirst(RegExp(r'^@+'), '').trim();
  if (value.isEmpty) {
    return null;
  }

  final lower = value.toLowerCase();
  if (lower.contains('@')) {
    final parts = lower.split('@');
    if (parts.length != 2 ||
        parts[0].isEmpty ||
        parts[1].isEmpty ||
        !parts[1].contains('.')) {
      return null;
    }
    if (!RegExp(r'^[a-z0-9_]+([a-z0-9_.-]*[a-z0-9_])?$').hasMatch(parts[0])) {
      return null;
    }
    return lower;
  }

  if (!RegExp(r'^[a-z0-9_]+([a-z0-9_.-]*[a-z0-9_])?$').hasMatch(lower)) {
    return null;
  }
  return lower;
}

/// Prefer `user@domain` for storage so a home-instance change cannot collide.
String canonicalMastodonAcct(String acct, {String? homeDomain}) {
  final trimmed = acct.trim().toLowerCase();
  if (trimmed.contains('@')) {
    return trimmed;
  }
  final domain = homeDomain?.trim().toLowerCase();
  if (domain == null || domain.isEmpty) {
    return trimmed;
  }
  return '$trimmed@$domain';
}

/// Mastodon status HTML → plain text for a card.
String mastodonHtmlToText(String? contentHtml) {
  if (contentHtml == null || contentHtml.trim().isEmpty) {
    return '';
  }
  final document = html.parse(
    contentHtml.replaceAll(RegExp(r'<br\s*/?>', caseSensitive: false), '\n'),
  );
  for (final block in document.querySelectorAll('p, div')) {
    block.append(html.parseFragment('\n').nodes.first);
  }
  return document.body?.text.replaceAll(RegExp(r'\n{3,}'), '\n\n').trim() ?? '';
}

List<String> mastodonImagesOf(Json status) {
  final urls = <String>[];
  for (final media in status['media_attachments'].list) {
    final type = media['type'].string ?? '';
    if (type != 'image' && type != 'gifv') {
      continue;
    }
    final url = media['preview_url'].string ?? media['url'].string;
    if (url != null && url.isNotEmpty && !urls.contains(url)) {
      urls.add(url);
    }
  }
  return urls;
}

/// PreviewCard on a status, or null when Mastodon sent nothing useful.
MastodonLinkCard? mastodonLinkCardOf(Json status) {
  final card = status['card'];
  if (!card.exists) {
    return null;
  }
  final url = card['url'].string?.trim() ?? '';
  if (url.isEmpty) {
    return null;
  }
  final title = card['title'].string?.trim();
  final description = card['description'].string?.trim();
  final image = card['image'].string?.trim();
  final provider = card['provider_name'].string?.trim();
  final type = card['type'].string?.trim();
  if ((title == null || title.isEmpty) &&
      (description == null || description.isEmpty) &&
      (image == null || image.isEmpty)) {
    return null;
  }
  return MastodonLinkCard(
    url: url,
    title: title == null || title.isEmpty ? null : title,
    description: description == null || description.isEmpty
        ? null
        : description,
    imageUrl: image == null || image.isEmpty ? null : image,
    providerName: provider == null || provider.isEmpty ? null : provider,
    type: type == null || type.isEmpty ? null : type,
  );
}

/// One status JSON object → [MastodonPost], or null when empty.
MastodonPost? mastodonPostFromStatus(
  Object? json, {
  String? homeDomain,
  bool includeQuote = true,
}) {
  final root = Json(json);
  // Unwrap boosts so the card shows the original public post.
  final boosted = root['reblog'].exists;
  final status = boosted ? root['reblog'] : root;

  final id = status['id'].string ?? '${status['id'].integer ?? ''}';
  if (id.isEmpty) {
    return null;
  }

  final author = MastodonProfile.fromJson(
    status['account'].raw,
    homeDomain: homeDomain,
  );
  final spoiler = status['spoiler_text'].string?.trim() ?? '';
  final body = mastodonHtmlToText(status['content'].string);
  final images = mastodonImagesOf(status);
  final linkCard = mastodonLinkCardOf(status);
  final poll = mastodonPollOf(status);
  if (spoiler.isEmpty &&
      body.isEmpty &&
      images.isEmpty &&
      linkCard == null &&
      poll == null) {
    return null;
  }

  final url =
      status['url'].string?.trim() ?? status['uri'].string?.trim() ?? '';
  if (url.isEmpty) {
    return null;
  }

  return MastodonPost(
    id: id,
    acct: author.acct,
    authorName: author.displayName,
    avatarUrl: author.avatarUrl,
    text: body,
    spoilerText: spoiler,
    sensitive: status['sensitive'].boolean ?? spoiler.isNotEmpty,
    images: images,
    publishedAt: DateTime.tryParse(
      status['created_at'].string ?? '',
    )?.toLocal(),
    url: url,
    boosted: boosted,
    boostedBy: boosted ? _boostedByName(root, homeDomain: homeDomain) : null,
    replyToAcct: _replyToAcct(status),
    mentionAccts: _mentionAccts(status),
    editedAt: DateTime.tryParse(status['edited_at'].string ?? '')?.toLocal(),
    quote: includeQuote
        ? mastodonQuoteOf(status, homeDomain: homeDomain)
        : null,
    repliesCount: status['replies_count'].integer ?? 0,
    reblogsCount: status['reblogs_count'].integer ?? 0,
    favouritesCount: status['favourites_count'].integer ?? 0,
    linkCard: linkCard,
    poll: poll,
  );
}

String? _boostedByName(Json root, {String? homeDomain}) {
  final booster = MastodonProfile.fromJson(
    root['account'].raw,
    homeDomain: homeDomain,
  );
  final name = booster.displayName.trim();
  return name.isEmpty ? null : name;
}

List<String> _mentionAccts(Json status) {
  final accts = <String>[];
  for (final mention in status['mentions'].list) {
    final acct = (mention['acct'].string ?? mention['username'].string ?? '')
        .trim();
    if (acct.isNotEmpty) accts.add(acct);
  }
  return accts;
}

MastodonQuotedPost? mastodonQuoteOf(Json status, {String? homeDomain}) {
  var quoted = status['quote']['quoted_status'];
  if (!quoted.exists) quoted = status['quoted_status'];
  if (!quoted.exists &&
      status['quote']['id'].exists &&
      status['quote']['account'].exists) {
    quoted = status['quote'];
  }
  if (!quoted.exists) return null;
  final post = mastodonPostFromStatus(
    quoted.raw,
    homeDomain: homeDomain,
    includeQuote: false,
  );
  if (post == null) return null;
  return MastodonQuotedPost(
    id: post.id,
    acct: post.acct,
    authorName: post.authorName,
    text: post.text,
    url: post.url,
    images: post.images,
  );
}

String? _replyToAcct(Json status) {
  if (!status['in_reply_to_id'].exists &&
      !status['in_reply_to_account_id'].exists) {
    return null;
  }
  for (final mention in status['mentions'].list) {
    final acct = (mention['acct'].string ?? mention['username'].string ?? '')
        .trim();
    if (acct.isNotEmpty) return acct;
  }
  return null;
}

MastodonPoll? mastodonPollOf(Json status) {
  final poll = status['poll'];
  if (!poll.exists) return null;
  final options = <MastodonPollOption>[];
  for (final option in poll['options'].list) {
    final title = (option['title'].string ?? '').trim();
    if (title.isEmpty) continue;
    options.add(
      MastodonPollOption(
        title: title,
        votes: option['votes_count'].integer ?? 0,
      ),
    );
  }
  if (options.isEmpty) return null;
  return MastodonPoll(
    options: options,
    votesCount: poll['votes_count'].integer ?? 0,
    expired: poll['expired'].boolean ?? false,
    multiple: poll['multiple'].boolean ?? false,
  );
}

List<MastodonField> _fieldsOf(Json data) {
  final fields = <MastodonField>[];
  for (final field in data['fields'].list) {
    final name = (field['name'].string ?? '').trim();
    if (name.isEmpty) continue;
    fields.add(
      MastodonField(
        name: name,
        value: mastodonHtmlToText(field['value'].string),
      ),
    );
  }
  return fields;
}

/// Misskey / Sharkey `notes/*` payload → the same card model.
MastodonPost? mastodonPostFromMisskeyNote(
  Object? json, {
  required String instance,
}) {
  final note = Json(json);
  final outerText = (note['text'].string ?? '').trim();
  final isRenote = note['renote'].exists && outerText.isEmpty;
  final source = isRenote ? note['renote'] : note;
  final id = (source['id'].string ?? '').trim();
  if (id.isEmpty) return null;

  final user = source['user'];
  final username = (user['username'].string ?? '').trim();
  if (username.isEmpty) return null;
  final remote = (user['host'].string ?? '').trim().toLowerCase();
  final host = remote.isEmpty
      ? (mastodonInstanceDomain(instance) ?? '')
      : remote;
  final acct = host.isEmpty ? username : '$username@$host';
  final name = (user['name'].string ?? '').trim();
  final spoiler = (source['cw'].string ?? '').trim();
  final body = (source['text'].string ?? '').trim();
  final images = _misskeyImages(source);
  if (spoiler.isEmpty && body.isEmpty && images.isEmpty && !isRenote) {
    return null;
  }

  final origin = mastodonInstanceDomain(instance) ?? host;
  final booster = isRenote ? note['user'] : const Json(null);
  final boosterName = (booster['name'].string ?? '').trim();
  final boosterUser = (booster['username'].string ?? '').trim();
  final isQuote = note['renote'].exists && outerText.isNotEmpty;
  return MastodonPost(
    id: id,
    acct: acct,
    authorName: name.isEmpty ? username : name,
    avatarUrl: user['avatarUrl'].string,
    text: body,
    spoilerText: spoiler,
    sensitive: spoiler.isNotEmpty,
    images: images,
    publishedAt: DateTime.tryParse(source['createdAt'].string ?? '')?.toLocal(),
    url: 'https://$origin/notes/$id',
    boosted: isRenote,
    boostedBy: isRenote
        ? (boosterName.isNotEmpty ? boosterName : boosterUser)
        : null,
    replyToAcct: _misskeyReplyTo(source),
    quote: isQuote ? _misskeyQuote(note['renote'], instance: instance) : null,
    repliesCount: source['repliesCount'].integer ?? 0,
    reblogsCount: source['renoteCount'].integer ?? 0,
    favouritesCount: _misskeyReactionCount(source),
  );
}

MastodonQuotedPost? _misskeyQuote(Json note, {required String instance}) {
  final post = mastodonPostFromMisskeyNote(note.raw, instance: instance);
  if (post == null) return null;
  return MastodonQuotedPost(
    id: post.id,
    acct: post.acct,
    authorName: post.authorName,
    text: post.text,
    url: post.url,
    images: post.images,
  );
}

List<String> _misskeyImages(Json note) {
  final images = <String>[];
  for (final file in note['files'].list) {
    final url = file['thumbnailUrl'].string ?? file['url'].string ?? '';
    if (url.startsWith('http')) images.add(url);
  }
  return images;
}

String? _misskeyReplyTo(Json note) {
  final user = note['reply']['user'];
  final username = (user['username'].string ?? '').trim();
  if (username.isEmpty) return null;
  final host = (user['host'].string ?? '').trim();
  return host.isEmpty ? username : '$username@$host';
}

int _misskeyReactionCount(Json note) {
  var total = 0;
  final reactions = note['reactions'].raw;
  if (reactions is Map) {
    for (final value in reactions.values) {
      if (value is num) total += value.toInt();
    }
  }
  return total;
}

List<MastodonPost> parseMisskeyNotes(Object? json, {required String instance}) {
  final root = Json(json);
  final items = root.raw is List ? root.list : const <Json>[];
  return [
    for (final item in items)
      ?mastodonPostFromMisskeyNote(item.raw, instance: instance),
  ];
}

/// Pure parse of `GET /accounts/:id/statuses` JSON array.
List<MastodonPost> parseMastodonStatuses(Object? json, {String? homeDomain}) {
  final root = Json(json);
  final items = root.raw is List ? root.list : const <Json>[];
  return [
    for (final item in items)
      ?mastodonPostFromStatus(item.raw, homeDomain: homeDomain),
  ];
}

/// A status plus the public conversation around it.
class MastodonThread {
  final MastodonPost status;
  final List<MastodonPost> ancestors;
  final List<MastodonPost> descendants;
  final String? homeDomain;

  const MastodonThread({
    required this.status,
    this.ancestors = const [],
    this.descendants = const [],
    this.homeDomain,
  });
}

/// A trending hashtag from `GET /api/v1/trends/tags`.
class MastodonTrendingTag {
  final String name;
  final String? url;
  final int uses;

  const MastodonTrendingTag({required this.name, this.url, this.uses = 0});
}

/// Pure parse of a trends/tags JSON array.
List<MastodonTrendingTag> parseMastodonTrendingTags(Object? json) {
  final root = Json(json);
  final items = root.raw is List ? root.list : const <Json>[];
  final tags = <MastodonTrendingTag>[];
  for (final item in items) {
    final name = (item['name'].string ?? '').trim();
    if (name.isEmpty) continue;
    var uses = 0;
    for (final day in item['history'].list) {
      uses +=
          int.tryParse(day['uses'].string ?? '') ?? day['uses'].integer ?? 0;
    }
    tags.add(
      MastodonTrendingTag(name: name, url: item['url'].string, uses: uses),
    );
  }
  return tags;
}

/// Pinned statuses first, then the rest without repeating an id.
List<MastodonPost> mergeMastodonPinned(
  List<MastodonPost> pinned,
  List<MastodonPost> posts,
) {
  final ids = pinned.map((post) => post.id).toSet();
  return [
    ...pinned,
    for (final post in posts)
      if (!ids.contains(post.id)) post,
  ];
}

/// Pure parse of `GET /api/v2/search` JSON.
MastodonSearchPage parseMastodonSearch(Object? json, {String? homeDomain}) {
  final root = Json(json);
  return MastodonSearchPage(
    accounts: [
      for (final account in root['accounts'].list)
        MastodonProfile.fromJson(account.raw, homeDomain: homeDomain),
    ],
    posts: parseMastodonStatuses(root['statuses'].raw, homeDomain: homeDomain),
    tags: parseMastodonTrendingTags(root['hashtags'].raw),
  );
}
