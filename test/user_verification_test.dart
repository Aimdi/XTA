import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/user.dart';
import 'package:xta/user_verification.dart';
import 'package:xta/utils/urls.dart';

Map<String, dynamic> _fixture(String path) =>
    jsonDecode(File('test/fixtures/$path').readAsStringSync()) as Map<String, dynamic>;

/// The affiliate label exactly as live `UserTweets` carried it for @X's media
/// source user (see `test/fixtures/UserTweets/add_entries.json`).
const _xLabel = {
  'badge': {'url': 'https://pbs.twimg.com/profile_images/1955359038532653056/OSHY3ewP_bigger.jpg'},
  'description': 'X',
  'url': {'url': 'https://twitter.com/X', 'urlType': 'DeepLink'},
  'userLabelDisplayType': 'Badge',
  'userLabelType': 'BusinessLabel',
};

Map<String, dynamic> _graphqlUser({
  bool? blue,
  Map<String, dynamic> legacy = const {},
  Map<String, dynamic> extra = const {},
}) => {
  '__typename': 'User',
  'rest_id': '44196397',
  'affiliates_highlighted_label': <String, dynamic>{},
  'is_blue_verified': ?blue,
  'core': {'name': 'Someone', 'screen_name': 'someone', 'created_at': 'Tue Jun 02 20:12:29 +0000 2009'},
  'legacy': {'verified': false, 'followers_count': 10, ...legacy},
  ...extra,
};

/// The first user node under a `source_user` key anywhere in [node].
Map<String, dynamic>? _sourceUser(Object? node) {
  if (node is Map) {
    final found = node['source_user']?['user_results']?['result'];
    if (found is Map<String, dynamic>) return found;
    return node.values.map(_sourceUser).nonNulls.firstOrNull;
  }
  if (node is List) return node.map(_sourceUser).nonNulls.firstOrNull;
  return null;
}

void main() {
  group('UserVerification.fromJson', () {
    test('is_blue_verified alone is a blue check', () {
      final badges = UserVerification.fromJson(_graphqlUser(blue: true));
      expect(badges.type, VerifiedType.blue);
      expect(badges.affiliation, isNull);
    });

    test('legacy.verified_type Business is gold, even when also blue', () {
      final user = _graphqlUser(blue: true, legacy: {'verified_type': 'Business'});
      expect(UserVerification.fromJson(user).type, VerifiedType.business);
    });

    test('verification.verified_type Government is grey without legacy', () {
      final user = _graphqlUser(blue: false)
        ..remove('legacy')
        ..['verification'] = {'verified': false, 'verified_type': 'Government'};
      expect(UserVerification.fromJson(user).type, VerifiedType.government);
    });

    test('the newer verification object wins over a stale legacy type', () {
      final user = _graphqlUser(
        legacy: {'verified_type': 'Business'},
        extra: {
          'verification': {'verified_type': 'Government'},
        },
      );
      expect(UserVerification.fromJson(user).type, VerifiedType.government);
    });

    test('a legacy verified flag with no type is drawn blue', () {
      final user = _graphqlUser(legacy: {'verified': true});
      expect(UserVerification.fromJson(user).type, VerifiedType.blue);
    });

    test('reads the affiliate label at the result level', () {
      final user = _graphqlUser(
        blue: true,
        extra: {
          'affiliates_highlighted_label': {'label': _xLabel},
        },
      );
      final affiliation = UserVerification.fromJson(user).affiliation!;
      expect(affiliation.badgeUrl, endsWith('OSHY3ewP_bigger.jpg'));
      expect(affiliation.name, 'X');
      expect(affiliation.profileUrl, 'https://twitter.com/X');
      expect(affiliation.labelType, 'BusinessLabel');
    });

    test('reads the affiliate label under legacy', () {
      final user = _graphqlUser(
        legacy: {
          'affiliates_highlighted_label': {'label': _xLabel},
        },
      );
      expect(UserVerification.fromJson(user).affiliation?.name, 'X');
    });

    test('ignores the Automated bot label and labels without a badge', () {
      final automated = {..._xLabel, 'userLabelType': 'AutomatedLabel'};
      final noBadge = Map<String, dynamic>.of(_xLabel)..remove('badge');
      for (final label in [automated, noBadge]) {
        final user = _graphqlUser(
          extra: {
            'affiliates_highlighted_label': {'label': label},
          },
        );
        expect(UserVerification.fromJson(user).affiliation, isNull);
      }
    });

    test('missing and misshapen fields read as no badges, never a throw', () {
      final shapes = <Object?>[
        null,
        const <String, dynamic>{},
        'User',
        {'is_blue_verified': 'yes', 'verified_type': 5, 'legacy': 'gone'},
        {'verification': [], 'affiliates_highlighted_label': 'label'},
        {
          'affiliates_highlighted_label': {
            'label': {'badge': 'url', 'description': 3, 'url': null},
          },
        },
      ];
      for (final shape in shapes) {
        final badges = UserVerification.fromJson(shape);
        expect(badges.type, VerifiedType.none, reason: '$shape');
        expect(badges.affiliation, isNull, reason: '$shape');
      }
    });
  });

  group('live fixtures', () {
    test('UserByScreenName @X is a gold check', () {
      final result = _fixture('UserByScreenName/ok.json')['data']['user']['result'] as Map<String, dynamic>;
      expect(UserWithExtra.fromNonLegacyJson(result).badges.type, VerifiedType.business);
    });

    test('a tweet author keeps its gold check through fromGraphqlJson', () {
      final tweet = TweetWithCard.fromGraphqlJson(_fixture('TweetDetail/tweet_result.json'));
      expect(tweet.user!.badges.type, VerifiedType.business);
    });

    test('the live affiliate label parses to an openable X profile', () {
      final source = _sourceUser(_fixture('UserTweets/add_entries.json'))!;
      final affiliation = UserWithExtra.fromNonLegacyJson(source).badges.affiliation!;
      expect(affiliation.name, 'X');
      expect(xProfileScreenName(affiliation.profileUrl), 'X');
    });
  });

  group('round trips', () {
    test('a cached tweet keeps its author badges and affiliation', () {
      final author = _graphqlUser(
        blue: false,
        legacy: {'verified_type': 'Government'},
        extra: {
          'affiliates_highlighted_label': {'label': _xLabel},
        },
      );
      final tweet = TweetWithCard.fromGraphqlJson(_fixture('TweetDetail/tweet_result.json'));
      tweet.user = UserWithExtra.fromNonLegacyJson(author);

      final restored = TweetWithCard.fromJson(jsonDecode(jsonEncode(tweet.toJson())) as Map<String, dynamic>);
      final badges = restored.user!.badges;
      expect(badges.type, VerifiedType.government);
      expect(badges.affiliation?.name, 'X');
      expect(badges.affiliation?.profileUrl, 'https://twitter.com/X');
    });

    test('a user cached before badges existed falls back to its verified flag', () {
      final old = {'id_str': '1', 'screen_name': 'old', 'name': 'Old', 'verified': true};
      expect(UserWithExtra.fromJson(old).badges.type, VerifiedType.blue);
      expect(TweetWithCard.fromJson({'id_str': '2', 'user': old}).user!.badges.type, VerifiedType.blue);
    });

    test('a user list round trip keeps the badges', () {
      final user = UserWithExtra.fromNonLegacyJson(_graphqlUser(legacy: {'verified_type': 'Business'}));
      expect(UserWithExtra.fromJson(user.toJson()).badges.type, VerifiedType.business);
    });
  });

  group('xProfileScreenName', () {
    test('accepts x.com and twitter.com profile links only', () {
      expect(xProfileScreenName('https://x.com/SpaceX'), 'SpaceX');
      expect(xProfileScreenName('https://twitter.com/X'), 'X');
      expect(xProfileScreenName('https://x.com/X/status/1'), isNull);
      expect(xProfileScreenName('https://example.com/X'), isNull);
      expect(xProfileScreenName(null), isNull);
      expect(xProfileScreenName('not a url'), isNull);
    });
  });
}
