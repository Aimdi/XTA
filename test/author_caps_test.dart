import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/group/author_caps.dart';
import 'package:xta/user.dart';

TweetChain _chain(String id, String authorId) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..user = (UserWithExtra()..idStr = authorId);
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

void main() {
  test('caps chains per author, keeping newest first', () {
    final chains = [_chain('1', 'a'), _chain('2', 'a'), _chain('3', 'a'), _chain('4', 'b')];

    final kept = capChainsPerAuthor(chains, {'a': 2});

    expect(kept.map((c) => c.id), ['1', '2', '4']);
  });

  test('uncapped authors pass through', () {
    final chains = [_chain('1', 'a'), _chain('2', 'b')];

    expect(capChainsPerAuthor(chains, {'a': 0}), chains);
    expect(capChainsPerAuthor(chains, {}), chains);
  });
}
