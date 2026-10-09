import 'package:dart_twitter_api/twitter_api.dart' show Entities, User, UserMention;
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_feed.dart';

User _user(String id, {String? name}) => User()
  ..idStr = id
  ..screenName = id
  ..name = name ?? id
  ..profileImageUrlHttps = 'https://img.test/$id.png';

TweetWithCard _tweet(String id, String by, {String? text, DateTime? at}) => TweetWithCard()
  ..idStr = id
  ..fullText = text ?? 'post $id'
  ..createdAt = at ?? DateTime(2026, 10, 1)
  ..user = _user(by);

UserMention _mention(String id) => UserMention()
  ..idStr = id
  ..screenName = id
  ..name = 'Mentioned $id';

TweetChain _chain(TweetWithCard tweet) => TweetChain(id: tweet.idStr!, tweets: [tweet], isPinned: false);

void main() {
  const members = {'m1', 'm2'};
  final shared = _tweet('r1', 'c1', text: 'the shared post', at: DateTime(2026, 9, 30));
  final chains = [
    _chain(_tweet('t1', 'm1')..retweetedStatusWithCard = shared),
    _chain(
      _tweet('t2', 'm2')..retweetedStatusWithCard = (_tweet('r2', 'c2')..quotedStatusWithCard = _tweet('q1', 'c3')),
    ),
    _chain(
      _tweet('t3', 'm1', text: 'replying')
        ..inReplyToUserIdStr = 'c4'
        ..inReplyToScreenName = 'c4',
    ),
    _chain(_tweet('t4', 'm2')..entities = (Entities()..userMentions = [_mention('c5'), _mention('m1')])),
    _chain(_tweet('t5', 'stranger')..retweetedStatusWithCard = _tweet('r3', 'c6')),
    _chain(_tweet('t6', 'm1')..quotedStatusWithCard = _tweet('q2', 'm2')),
  ];

  test('every repost, quote, reply and mention by a member points at a candidate the member vouches for', () {
    final accounts = feedDiscoveryAccounts(chains, members: members, includeReplies: true);
    expect(
      {for (final account in accounts) account.id: account.supporters.single.kind},
      {
        'c1': DiscoverySignal.reposted,
        'c2': DiscoverySignal.reposted,
        'c3': DiscoverySignal.quoted,
        'c4': DiscoverySignal.replied,
        'c5': DiscoverySignal.mentioned,
      },
    );
    final byId = {for (final account in accounts) account.id: account};
    expect(byId['c1']!.supporters.single.memberId, 'm1');
    expect(byId['c1']!.supportingPost, same(shared));
    expect(byId['c1']!.text, 'the shared post');
    expect(byId['c1']!.date, DateTime(2026, 9, 30));
    expect(byId['c1']!.avatarUrl, 'https://img.test/c1.png');
    expect(byId['c3']!.supporters.single.memberId, 'm2');
    expect(byId['c4']!.avatarUrl, isNull);
    expect(byId['c4']!.text, 'replying');
    expect(byId['c5']!.name, 'Mentioned c5');
  });

  test('replies are only mined when the group shows them', () {
    final accounts = feedDiscoveryAccounts(chains, members: members, includeReplies: false);
    expect(accounts.map((account) => account.id), isNot(contains('c4')));
    expect(accounts.map((account) => account.id), contains('c5'));
  });

  test('mined candidates merge across members into one ranked row', () {
    final again = _chain(_tweet('t7', 'm2')..retweetedStatusWithCard = _tweet('r4', 'c1', at: DateTime(2026, 10, 2)));
    final ranked = rankDiscoveryAccounts(
      feedDiscoveryAccounts([...chains, again], members: members, includeReplies: true),
      followed: {},
      groupName: '🍫',
    );
    expect(ranked.first.id, 'c1');
    expect(ranked.first.memberCount, 2);
    expect(ranked.first.date, DateTime(2026, 10, 2));
  });
}
