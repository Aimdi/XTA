import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/boost_runs.dart';

TweetChain _boost(String id, {String booster = 'alice'}) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..user = (User()
      ..screenName = booster
      ..name = booster)
    ..retweetedStatusWithCard = (TweetWithCard()..text = 'boosted $id');
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

TweetChain _plain(String id) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..user = (User()..screenName = 'bob');
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

void main() {
  group('chainIsBoost', () {
    test('detects retweet chains', () {
      expect(chainIsBoost(_boost('1')), isTrue);
      expect(chainIsBoost(_plain('2')), isFalse);
    });
  });

  group('collapseBoostRuns', () {
    test('groups consecutive boosts of length >= 2', () {
      final chains = [_boost('1'), _boost('2'), _plain('3'), _boost('4')];
      final collapsed = collapseBoostRuns(chains);

      expect(collapsed, hasLength(3));
      expect(collapsed[0], isA<BoostRun>());
      expect((collapsed[0] as BoostRun).chains, hasLength(2));
      expect(collapsed[1], isA<SingleChain>());
      expect((collapsed[1] as SingleChain).chain.id, '3');
      expect(collapsed[2], isA<SingleChain>());
      expect((collapsed[2] as SingleChain).chain.id, '4');
    });

    test('leaves isolated boosts as single chains', () {
      final collapsed = collapseBoostRuns([_plain('1'), _boost('2'), _plain('3')]);
      expect(collapsed, hasLength(3));
      expect(collapsed.every((e) => e is SingleChain), isTrue);
    });
  });

  group('boostRunLengthAt', () {
    test('returns run length only at the first chain of a run', () {
      final chains = [_boost('1'), _boost('2'), _boost('3')];
      expect(boostRunLengthAt(chains, 0), 3);
      expect(boostRunLengthAt(chains, 1), 0);
      expect(isContinuationOfBoostRun(chains, 1), isTrue);
    });
  });
}
