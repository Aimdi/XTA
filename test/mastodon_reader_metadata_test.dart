import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_snapshot.dart';

Map<String, Object?> status(String id, {String? url}) => {
  'id': id,
  'url': url ?? 'https://social.example/@reader/$id',
  'account': {'id': '1', 'acct': 'reader', 'username': 'reader'},
  'created_at': '2026-09-01T10:00:00Z',
  'content': '<p>Reading</p>',
};

void main() {
  test('Misskey renotes retain followed booster identity across reading snapshots', () {
    final post = mastodonPostFromMisskeyNote({
      'id': 'renote',
      'user': {'username': 'reader', 'host': 'followed.example'},
      'renote': {
        'id': 'original',
        'text': 'A remote post',
        'user': {'username': 'author', 'host': 'other.example'},
      },
    }, instance: 'https://reader.example')!;
    final restored = mastodonPostFromSnapshot(mastodonPostSnapshot(post))!;
    expect(restored.boostedByAcct, 'reader@followed.example');
    expect(restored.pagingId, 'renote');
  });

  test('boost preserves wrapper cursor/date independently of original identity', () {
    final raw = status('200')..['created_at'] = '2026-09-20T10:00:00Z';
    raw['reblog'] = status('10');
    final post = mastodonPostFromStatus(raw, homeDomain: 'social.example')!;
    expect(post.id, '10');
    expect(post.pagingId, '200');
    expect(post.publishedAt!.toUtc(), DateTime.utc(2026, 9, 1, 10));
    expect(post.timelineDate!.toUtc(), DateTime.utc(2026, 9, 20, 10));
    final restored = mastodonPostFromSnapshot(mastodonPostSnapshot(post))!;
    expect(restored.pagingId, '200');
    expect(restored.timelineDate, post.timelineDate);
  });

  test('old snapshots retain the original cursor and publication date fallback', () {
    final raw = mastodonPostSnapshot(mastodonPostFromStatus(status('10'))!)
      ..remove('timelineId')
      ..remove('timelineAt');
    final post = mastodonPostFromSnapshot(raw)!;
    expect(post.pagingId, '10');
    expect(post.timelineDate, post.publishedAt);
  });

  test('quote warnings survive parsing, snapshots and opening the quote', () {
    final raw = status('20');
    raw['quote'] = {
      'quoted_status': status('10')
        ..['spoiler_text'] = 'Spoilers'
        ..['sensitive'] = true,
    };
    final post = mastodonPostFromSnapshot(mastodonPostSnapshot(mastodonPostFromStatus(raw)!))!;
    expect(post.quote!.spoilerText, 'Spoilers');
    expect(post.quote!.asPost.hasSpoiler, true);
    expect(post.quote!.asPost.sensitive, true);
  });

  test('poll does not present unpublished option totals as zero votes', () {
    final raw = status('20');
    raw['poll'] = {
      'multiple': true,
      'votes_count': 12,
      'voters_count': 8,
      'expires_at': '2026-10-01T10:00:00Z',
      'options': [
        {'title': 'A', 'votes_count': null},
        {'title': 'B', 'votes_count': 5},
      ],
    };
    final poll = mastodonPostFromSnapshot(mastodonPostSnapshot(mastodonPostFromStatus(raw)!))!.poll!;
    expect(poll.resultsAvailable, false);
    expect(poll.votersCount, 8);
    expect(poll.expiresAt, DateTime.utc(2026, 10, 1, 10));
  });

  test('profile fields retain exactly one safe link and server verification', () {
    final profile = MastodonProfile.fromJson({
      'header_static': 'https://media.example/banner.png',
      'created_at': '2020-01-01T00:00:00Z',
      'fields': [
        {'name': 'Site', 'value': '<a href="https://example.org">My site</a>', 'verified_at': '2025-01-01T00:00:00Z'},
        {'name': 'Unsafe', 'value': '<a href="javascript:alert(1)">Do not open</a>'},
        {'name': 'Ambiguous', 'value': '<a href="https://a.example">A</a><a href="https://b.example">B</a>'},
        {'name': 'Credentials', 'value': '<a href="https://secret@example.org">No</a>'},
      ],
    });
    expect(profile.headerUrl, 'https://media.example/banner.png');
    expect(profile.createdAt, DateTime.utc(2020));
    expect(profile.fields.first.value, 'My site');
    expect(profile.fields.first.url, 'https://example.org');
    expect(profile.fields.first.verifiedAt, DateTime.utc(2025));
    expect(profile.fields.skip(1).every((field) => field.url == null), true);
  });

  test('malformed optional metadata never blocks a profile or post', () {
    final profile = MastodonProfile.fromJson({
      'header': [],
      'created_at': 7,
      'fields': [null, false],
    });
    expect(profile.headerUrl, isNull);
    expect(profile.createdAt, isNull);
    expect(profile.fields, isEmpty);
    final raw = status('20')
      ..['poll'] = {
        'options': [
          false,
          {'title': 'Option', 'votes_count': []},
        ],
      };
    expect(mastodonPostFromStatus(raw)!.poll!.resultsAvailable, false);
  });

  test('canonical identity preserves path case and ports but ignores query/fragment', () {
    MastodonPost post(String url) => MastodonPost(id: '1', acct: 'a', authorName: 'A', text: '', url: url);
    expect(
      canonicalMastodonPostKey(post('https://SOCIAL.example/@User/1/?tracking=1#part')),
      canonicalMastodonPostKey(post('http://social.example/@User/1')),
    );
    expect(
      canonicalMastodonPostKey(post('https://social.example/@User/1')),
      isNot(canonicalMastodonPostKey(post('https://social.example/@user/1'))),
    );
    expect(
      canonicalMastodonPostKey(post('https://social.example:8443/@User/1')),
      isNot(canonicalMastodonPostKey(post('https://social.example/@User/1'))),
    );
  });
}
