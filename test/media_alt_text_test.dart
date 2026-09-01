import 'package:flutter_test/flutter_test.dart';
import 'package:dart_twitter_api/twitter_api.dart';
import 'package:xta/client/tweet_models.dart';

void main() {
  test('extracts ext_alt_text keyed by media id_str', () {
    final alt = extractMediaAltText({
      'media': [
        {'id_str': '123', 'type': 'photo', 'ext_alt_text': 'A cat sitting on a keyboard'},
        {'id_str': '456', 'type': 'photo'},
      ],
    });

    expect(alt['123'], 'A cat sitting on a keyboard');
    expect(alt.containsKey('456'), isFalse);
  });

  test('fromData plumbs mediaAltText from extended_entities', () {
    final tweet = TweetWithCard.fromData(
      {
        'id_str': '1',
        'full_text': 'photo',
        'extended_entities': {
          'media': [
            {'id_str': '99', 'ext_alt_text': 'Description here'},
          ],
        },
      },
      null,
      null,
      null,
      null,
      null,
      null,
    );

    expect(tweet.mediaAltText['99'], 'Description here');
    expect(tweet.altTextForMedia(Media()..idStr = '99'), 'Description here');
  });
}
