import 'package:flutter_test/flutter_test.dart';
import 'package:xta/antenna/antenna_query.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/user.dart';

void main() {
  test('builds OR include query with excludes', () {
    final antenna = Antenna(
      id: '1',
      name: 'Tech',
      includeTerms: const ['flutter', 'dart lang'],
      excludeTerms: const ['job'],
      createdAt: DateTime.utc(2024, 1, 1),
    );

    expect(buildAntennaSearchQuery(antenna), '(flutter OR "dart lang") -job');
  });

  test('following scope keeps only followed authors', () {
    TweetChain chain(String id, String author) {
      final tweet = TweetWithCard()
        ..idStr = id
        ..user = (UserWithExtra()..idStr = author);
      return TweetChain(id: id, tweets: [tweet], isPinned: false);
    }

    final kept = filterAntennaFollowingScope([chain('1', 'a'), chain('2', 'b')], {'a'});

    expect(kept.map((c) => c.id), ['1']);
  });
}
