import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/broadcasts.dart';

TweetWithCard _tweet({
  String? broadcastUrl,
  Map<String, dynamic>? card,
  bool video = false,
}) {
  final tweet = TweetWithCard();
  tweet.idStr = '1';
  tweet.user = User.fromJson({'id_str': 'u1', 'screen_name': 'someone'});
  if (broadcastUrl != null) {
    tweet.entities = Entities.fromJson({
      'urls': [
        {
          'url': 'https://t.co/b',
          'expanded_url': broadcastUrl,
          'display_url': 'x.com/i/broadcasts/1…',
          'indices': [0, 23],
        },
      ],
    });
  }
  tweet.card = card;
  if (video) {
    tweet.extendedEntities = Entities.fromJson({
      'media': [
        {'type': 'video', 'media_url_https': 'https://pbs.example/1.jpg'},
      ],
    });
  }
  return tweet;
}

void main() {
  test('a broadcasts URL marks the tweet', () {
    expect(
      tweetHasBroadcast(
        _tweet(broadcastUrl: 'https://x.com/i/broadcasts/1abc'),
      ),
      isTrue,
    );
  });

  test('the broadcast card name marks the tweet', () {
    expect(
      tweetHasBroadcast(_tweet(card: {'name': '745291183405076480:broadcast'})),
      isTrue,
    );
    expect(isBroadcastCard({'name': 'player'}), isFalse);
  });

  test('a broadcast_url on the card is enough, even without the name', () {
    final tweet = _tweet(
      card: {
        'binding_values': {
          'broadcast_url': {
            'string_value': 'https://x.com/i/broadcasts/1card',
          },
          'broadcast_thumbnail': {
            'image_value': {'url': 'https://pbs.example/thumb.jpg'},
          },
        },
      },
    );
    expect(broadcastIdOf(tweet), '1card');
    expect(tweetHasBroadcast(tweet), isTrue);
    expect(broadcastUrlOf(tweet), 'https://x.com/i/broadcasts/1card');
    expect(broadcastThumbnailFromCard(tweet.card), 'https://pbs.example/thumb.jpg');
  });

  test('the link in the post body counts when entities omit it', () {
    final tweet = TweetWithCard();
    tweet.idStr = '1';
    tweet.fullText = 'live at https://x.com/i/broadcast/1txt';
    expect(broadcastIdOf(tweet), '1txt');
  });

  test('a video whose expanded_url is the broadcast link is a broadcast', () {
    final tweet = TweetWithCard();
    tweet.idStr = '1';
    tweet.extendedEntities = Entities.fromJson({
      'media': [
        {
          'type': 'video',
          'media_url_https': 'https://pbs.example/1.jpg',
          'expanded_url': 'https://x.com/i/broadcasts/1vod',
          'display_url': 'x.com/i/broadcasts/1vod',
        },
      ],
    });
    expect(broadcastIdOf(tweet), '1vod');
    expect(tweetHasBroadcast(tweet), isTrue);
  });

  test('GraphQL list-shaped card bindings still yield the id', () {
    final tweet = _tweet(
      card: {
        'binding_values': [
          {
            'key': 'broadcast_url',
            'value': {
              'string_value': 'https://x.com/i/broadcasts/1list',
            },
          },
        ],
      },
    );
    expect(broadcastIdOf(tweet), '1list');
  });

  test('an ordinary post is not a broadcast', () {
    expect(tweetHasBroadcast(_tweet()), isFalse);
  });

  test('video media is detected separately from the link', () {
    expect(tweetHasVideoMedia(_tweet(video: true)), isTrue);
    expect(tweetHasVideoMedia(_tweet()), isFalse);
  });

  test('a spaces URL marks the tweet as a Space', () {
    final tweet = _tweet(broadcastUrl: 'https://x.com/i/spaces/1room');
    expect(tweetHasSpace(tweet), isTrue);
    expect(tweetHasBroadcast(tweet), isFalse);
    expect(tweetIsLive(tweet), isTrue);
    expect(spaceIdOf(tweet), '1room');
    expect(spaceUrlOf(tweet), 'https://x.com/i/spaces/1room');
    expect(liveUrlOf(tweet), 'https://x.com/i/spaces/1room');
  });

  test('the audiospace card name and id mark the tweet', () {
    final tweet = _tweet(
      card: {
        'name': '326813241626781184:audiospace',
        'binding_values': {
          'id': {'string_value': '1cardspace'},
          'title': {'string_value': 'late night'},
          'thumbnail_image': {
            'image_value': {'url': 'https://pbs.example/space.jpg'},
          },
        },
      },
    );
    expect(isAudioSpaceCard(tweet.card), isTrue);
    expect(spaceIdOf(tweet), '1cardspace');
    expect(tweetHasSpace(tweet), isTrue);
    expect(
      broadcastThumbnailFromCard(tweet.card),
      'https://pbs.example/space.jpg',
    );
  });

  test('broadcastMediaKeyOf reads the card binding', () {
    final tweet = _tweet(
      card: {
        'name': '745291183405076480:broadcast',
        'binding_values': {
          'broadcast_media_key': {'string_value': '28_1abc'},
        },
      },
    );
    expect(broadcastMediaKeyOf(tweet), '28_1abc');
    expect(broadcastMediaKeyOf(_tweet()), isNull);
  });

  test('playbackUrlFromBroadcastStatus reads HLS and ignores junk', () {
    expect(
      playbackUrlFromBroadcastStatus({
        'source': {'noRedirectPlaybackUrl': 'https://video.example/live.m3u8'},
      }),
      'https://video.example/live.m3u8',
    );
    expect(playbackUrlFromBroadcastStatus({'source': {}}), isNull);
    expect(playbackUrlFromBroadcastStatus(null), isNull);
    expect(playbackUrlFromBroadcastStatus([]), isNull);
  });

  test('mediaKeyFromAudioSpace and broadcasts/show parsers', () {
    expect(
      mediaKeyFromAudioSpace({
        'data': {
          'audioSpace': {
            'metadata': {'media_key': '28_space'},
          },
        },
      }),
      '28_space',
    );
    expect(mediaKeyFromAudioSpace({'data': {}}), isNull);
    expect(
      mediaKeyFromBroadcastsShow({
        'broadcasts': {
          '1abc': {'media_key': '28_1abc'},
        },
      }, '1abc'),
      '28_1abc',
    );
    expect(mediaKeyFromBroadcastsShow({'broadcasts': {}}, '1abc'), isNull);
  });

  test('LivePlayRequest.fromUrl distinguishes Spaces from broadcasts', () {
    final space = LivePlayRequest.fromUrl('https://x.com/i/spaces/1room');
    expect(space.isSpace, isTrue);
    expect(space.spaceId, '1room');
    expect(space.canResolve, isTrue);
    expect(space.watchUrl, 'https://x.com/i/spaces/1room');

    final live = LivePlayRequest.fromUrl('https://x.com/i/broadcasts/1abc');
    expect(live.isSpace, isFalse);
    expect(live.broadcastId, '1abc');
    expect(live.canResolve, isTrue);

    expect(LivePlayRequest.fromUrl('https://x.com/someone/status/1').canResolve, isFalse);
  });

  test('LivePlayRequest.fromTweet carries the card media_key', () {
    final tweet = _tweet(
      card: {
        'name': '745291183405076480:broadcast',
        'binding_values': {
          'broadcast_media_key': {'string_value': '28_fromcard'},
          'broadcast_url': {
            'string_value': 'https://x.com/i/broadcasts/1card',
          },
        },
      },
    );
    final request = LivePlayRequest.fromTweet(tweet);
    expect(request.mediaKey, '28_fromcard');
    expect(request.broadcastId, '1card');
    expect(request.canResolve, isTrue);
  });
}
