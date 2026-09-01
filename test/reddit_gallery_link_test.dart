import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_media_urls.dart';

void main() {
  group('recognising a gallery a listing did not carry the pictures for', () {
    // Scraped from old.reddit, a gallery post arrives as a link to
    // reddit.com/gallery/<id> and a 70px thumbnail — the pictures are not in
    // that HTML at all. Spotting the link is what lets the card go and get them.
    test('a gallery link is one', () {
      expect(isRedditGalleryUrl('https://www.reddit.com/gallery/abc123'), isTrue);
    });

    test('old.reddit spells it the same way', () {
      expect(isRedditGalleryUrl('https://old.reddit.com/gallery/abc123'), isTrue);
    });

    test('a trailing slash makes no difference', () {
      expect(isRedditGalleryUrl('https://www.reddit.com/gallery/abc123/'), isTrue);
    });

    test('an ordinary post is not one', () {
      expect(isRedditGalleryUrl('https://www.reddit.com/r/pics/comments/abc/title/'), isFalse);
    });

    test('a direct image is not one', () {
      expect(isRedditGalleryUrl('https://i.redd.it/abc.jpg'), isFalse);
    });

    test('somewhere else entirely is not one, whatever its path says', () {
      expect(isRedditGalleryUrl('https://example.test/gallery/abc'), isFalse);
    });

    test('nothing is not one', () {
      expect(isRedditGalleryUrl(null), isFalse);
      expect(isRedditGalleryUrl(''), isFalse);
    });
  });
}
