import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_media_urls.dart';

void main() {
  group('what a URL points at', () {
    test('a picture host serves a picture whatever the path looks like', () {
      expect(redditImageUrl('https://i.redd.it/abc'), 'https://i.redd.it/abc');
      expect(redditImageUrl('https://preview.redd.it/x?width=640'), isNotNull);
    });

    test('an image extension is enough on any host', () {
      expect(
        redditImageUrl('https://example.com/photo.PNG'),
        'https://example.com/photo.PNG',
      );
      expect(redditImageUrl('https://example.com/anim.gif'), isNotNull);
    });

    test(
      "imgur's gifv is a video player; the gif beside it is the animation",
      () {
        expect(
          redditImageUrl('https://i.imgur.com/abc.gifv'),
          'https://i.imgur.com/abc.gif',
        );
      },
    );

    test('a page is not a picture', () {
      expect(redditImageUrl('https://www.reddit.com/gallery/abc'), isNull);
      expect(redditImageUrl('https://v.redd.it/abc'), isNull);
      expect(redditImageUrl(null), isNull);
      expect(redditImageUrl('not a url at all'), isNull);
      expect(
        redditImageUrl('javascript:alert(1)'),
        isNull,
        reason: 'only http(s) is ever loaded',
      );
    });

    test('a giphy share link resolves to the file behind it', () {
      expect(
        redditEmbeddableImage(
          'https://giphy.com/gifs/funny-cat-l0HlvtIPzPdt2usKs',
        ),
        'https://media.giphy.com/media/l0HlvtIPzPdt2usKs/giphy.gif',
      );
      expect(
        redditEmbeddableImage('https://giphy.com/gifs/l0HlvtIPzPdt2usKs'),
        isNotNull,
        reason: 'a slug without words is the id on its own',
      );
      expect(redditEmbeddableImage('https://giphy.com/explore/cats'), isNull);
    });

    test('a single imgur page resolves; an album does not', () {
      expect(
        redditEmbeddableImage('https://imgur.com/AbCd123'),
        'https://i.imgur.com/AbCd123.jpeg',
      );
      expect(redditEmbeddableImage('https://imgur.com/a/AbCd123'), isNull);
      expect(
        redditEmbeddableImage('https://imgur.com/gallery/AbCd123'),
        isNull,
      );
    });

    test('a host that needs a key or a round trip is left alone', () {
      expect(
        redditEmbeddableImage('https://redgifs.com/watch/somename'),
        isNull,
      );
      expect(
        redditEmbeddableImage('https://tenor.com/view/thing-12345'),
        isNull,
      );
    });

    test(
      "Reddit's own media token names the file the old site never rendered",
      () {
        final match = redditMediaToken.firstMatch(
          '![gif](giphy|l0HlvtIPzPdt2usKs|downsized)',
        );

        expect(match, isNotNull);
        expect(
          redditTokenImage(match!),
          'https://media.giphy.com/media/l0HlvtIPzPdt2usKs/giphy.gif',
        );
      },
    );

    test('an emote token names no public file, so it resolves to nothing', () {
      final match = redditMediaToken.firstMatch(
        '![img](emote|free_emotes_pack|joy)',
      );

      expect(match, isNotNull);
      expect(redditTokenImage(match!), isNull);
    });

    test('video hosts are known regardless of www', () {
      expect(isRedditVideoHost('v.redd.it'), isTrue);
      expect(isRedditVideoHost('www.youtube.com'), isTrue);
      expect(isRedditVideoHost('example.com'), isFalse);
      expect(isRedditVideoHost(null), isFalse);
    });
  });

  group('collapseRedditImageUrls', () {
    test('different widths of the same path collapse to the larger width', () {
      expect(
        collapseRedditImageUrls([
          'https://preview.redd.it/abc.jpg?width=320&s=a',
          'https://preview.redd.it/abc.jpg?width=1080&s=b',
        ]),
        ['https://preview.redd.it/abc.jpg?width=1080&s=b'],
      );
    });

    test('preview and i.redd.it of the same file collapse to i.redd.it', () {
      expect(
        collapseRedditImageUrls([
          'https://preview.redd.it/abc.jpg?width=1080&s=a',
          'https://i.redd.it/abc.jpg',
        ]),
        ['https://i.redd.it/abc.jpg'],
      );
    });

    test('different filenames stay separate, in first-seen order', () {
      expect(
        collapseRedditImageUrls([
          'https://preview.redd.it/one.jpg?width=640',
          'https://preview.redd.it/two.jpg?width=640',
        ]),
        [
          'https://preview.redd.it/one.jpg?width=640',
          'https://preview.redd.it/two.jpg?width=640',
        ],
      );
    });

    test('exact duplicate URLs collapse to one', () {
      expect(
        collapseRedditImageUrls([
          'https://i.redd.it/a.png',
          'https://i.redd.it/a.png',
        ]),
        ['https://i.redd.it/a.png'],
      );
    });
  });

  group('redditVRedditDashUrl', () {
    test('builds the DASH playlist from a bare v.redd.it id', () {
      expect(
        redditVRedditDashUrl('https://v.redd.it/abc123xyz'),
        'https://v.redd.it/abc123xyz/DASHPlaylist.mpd',
      );
      expect(
        redditVRedditDashUrl('https://www.v.redd.it/abc123xyz/'),
        'https://v.redd.it/abc123xyz/DASHPlaylist.mpd',
      );
    });

    test('ignores non-video hosts and file paths', () {
      expect(redditVRedditDashUrl('https://i.redd.it/abc.jpg'), isNull);
      expect(redditVRedditDashUrl('https://v.redd.it/abc.mp4'), isNull);
      expect(redditVRedditDashUrl(null), isNull);
      expect(redditVRedditDashUrl('not a url'), isNull);
    });
  });
}
