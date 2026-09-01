import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_html.dart';

/// A listing page shaped like old.reddit's: the `data-*` attributes carry
/// everything, which is why the parser reads those rather than the layout.
String _page(String things, {String next = ''}) =>
    '''
<!doctype html><html><body><div class="content" role="main">
  <div id="siteTable">$things</div>
  <div class="nav-buttons"><span class="next-button">$next</span></div>
</div></body></html>
''';

const _link = '''
<div class=" thing id-t3_abc123 link " id="thing_t3_abc123"
     data-fullname="t3_abc123" data-type="link" data-subreddit="dartlang"
     data-author="someone" data-score="42" data-comments-count="7"
     data-timestamp="1767225600000" data-permalink="/r/dartlang/comments/abc123/a_title/"
     data-url="https://example.com/article" data-domain="example.com" data-nsfw="false">
  <a class="thumbnail may-blank" href="https://example.com/article">
    <img src="//b.thumbs.redditmedia.com/x.jpg">
  </a>
  <a class="title may-blank outbound" href="https://example.com/article">A title</a>
</div>
''';

const _selfPost = '''
<div class=" thing id-t3_self1 link self " data-fullname="t3_self1"
     data-subreddit="dartlang" data-author="writer" data-score="5"
     data-comments-count="0" data-timestamp="1767312000000"
     data-permalink="/r/dartlang/comments/self1/hello/"
     data-url="/r/dartlang/comments/self1/hello/" data-domain="self.dartlang" data-nsfw="false">
  <a class="title" href="/r/dartlang/comments/self1/hello/">Hello</a>
  <div class="expando"><div class="md"><p>Body text</p></div></div>
</div>
''';

void main() {
  group('reading a listing', () {
    test('takes a link post out of its data attributes', () {
      final post = parseListing(_page(_link)).posts.single;

      expect(
        post.id,
        'abc123',
        reason: 'the t3_ prefix is the fullname, not the id',
      );
      expect(post.title, 'A title');
      expect(post.subreddit, 'dartlang');
      expect(post.author, 'someone');
      expect(post.score, 42);
      expect(post.commentCount, 7);
      expect(post.permalink, '/r/dartlang/comments/abc123/a_title/');
      expect(post.url, 'https://example.com/article');
      expect(post.isSelf, isFalse);
      expect(
        post.createdAt,
        DateTime.fromMillisecondsSinceEpoch(
          1767225600000,
          isUtc: true,
        ).toLocal(),
      );
    });

    test('a protocol-relative thumbnail is given a scheme', () {
      expect(
        parseListing(_page(_link)).posts.single.thumbnail,
        'https://b.thumbs.redditmedia.com/x.jpg',
      );
    });

    test('a self post is recognised by its domain and keeps its body', () {
      final post = parseListing(_page(_selfPost)).posts.single;

      expect(post.isSelf, isTrue);
      expect(
        post.url,
        isNull,
        reason: 'there is no article behind a self post',
      );
      expect(post.selfText, 'Body text');
    });

    test('several posts keep their order', () {
      final posts = parseListing(_page('$_link$_selfPost')).posts;

      expect(posts.map((p) => p.id), ['abc123', 'self1']);
    });

    test('an advert is left out', () {
      const promoted = '''
<div class=" thing " data-fullname="t3_ad" data-promoted="true" data-subreddit="x"
     data-permalink="/r/x/comments/ad/"><a class="title">Buy this</a></div>
''';

      expect(parseListing(_page('$promoted$_link')).posts.map((p) => p.id), [
        'abc123',
      ]);
    });

    test('a pinned post is marked', () {
      const stickied = '''
<div class="thing stickied" data-fullname="t3_pin" data-subreddit="x"
     data-permalink="/r/x/comments/pin/"><a class="title">Read this first</a></div>
''';

      expect(parseListing(_page(stickied)).posts.single.stickied, isTrue);
      expect(parseListing(_page(_link)).posts.single.stickied, isFalse);
    });

    test('the domain comes through so the card can say where a link leads', () {
      expect(parseListing(_page(_link)).posts.single.domain, 'example.com');
    });

    test(
      'flair is read, preferring the full label over the abbreviated one',
      () {
        const flaired = '''
<div class="thing" data-fullname="t3_f" data-subreddit="x"
     data-permalink="/r/x/comments/f/">
  <a class="title">Titled</a>
  <span class="linkflairlabel" title="Elon Criticism">Elon Crit…</span>
</div>
''';
        const bare = '''
<div class="thing" data-fullname="t3_b" data-subreddit="x"
     data-permalink="/r/x/comments/b/">
  <a class="title">Titled</a>
  <span class="linkflairlabel">Discussion</span>
</div>
''';

        expect(
          parseListing(_page(flaired)).posts.single.flair,
          'Elon Criticism',
        );
        expect(parseListing(_page(bare)).posts.single.flair, 'Discussion');
        expect(parseListing(_page(_link)).posts.single.flair, isNull);
      },
    );

    test('an over-18 post carries the flag', () {
      const nsfw = '''
<div class="thing" data-fullname="t3_n" data-subreddit="x" data-nsfw="true"
     data-permalink="/r/x/comments/n/"><a class="title">Grown up</a></div>
''';

      expect(parseListing(_page(nsfw)).posts.single.over18, isTrue);
    });

    test('a spoiler post carries the flag', () {
      const spoiler = '''
<div class="thing spoiler" data-fullname="t3_s" data-subreddit="x" data-spoiler="true"
     data-permalink="/r/x/comments/s/"><a class="title">The ending</a></div>
''';

      expect(parseListing(_page(spoiler)).posts.single.spoiler, isTrue);
    });
  });

  group('pagination', () {
    test('takes the after cursor off the next link', () {
      final page = _page(
        _link,
        next:
            '<a href="https://old.reddit.com/r/dartlang/?count=25&amp;after=t3_abc123" rel="nofollow next">next</a>',
      );

      expect(parseListing(page).after, 't3_abc123');
    });

    test('the last page has no cursor', () {
      expect(parseListing(_page(_link)).after, isNull);
    });
  });

  group('markup that cannot be read', () {
    test('a post with no title or permalink is skipped, not guessed at', () {
      const broken = '<div class="thing" data-fullname="t3_x"></div>';

      expect(parseListing(_page(broken)).posts, isEmpty);
    });

    test('a page with no posts at all yields none rather than throwing', () {
      expect(parseListing(_page('')).posts, isEmpty);
      expect(parseListing('not html at all').posts, isEmpty);
      expect(parseListing('').posts, isEmpty);
    });

    test('an unreadable score is zero rather than fatal', () {
      const odd = '''
<div class="thing" data-fullname="t3_o" data-subreddit="x" data-score="•"
     data-comments-count="" data-permalink="/r/x/comments/o/"><a class="title">Odd</a></div>
''';

      final post = parseListing(_page(odd)).posts.single;
      expect(post.score, 0);
      expect(post.commentCount, 0);
      expect(post.createdAt, isNull);
    });
  });

  group('a subreddit picture', () {
    test('comes from the header image the old site does have', () {
      const page = '''
<html><head><meta property="og:image" content="https://www.redditstatic.com/icon.png"></head>
<body><a id="header-img" href="/r/x/"><img src="//b.thumbs.redditmedia.com/logo.png"></a></body></html>
''';

      expect(
        parseSubredditIcon(page),
        'https://b.thumbs.redditmedia.com/logo.png',
        reason:
            "the site's own logo in og:image is not the subreddit's picture",
      );
    });

    test('falls back to og:image when it is community artwork', () {
      const page = '''
<html><head>
  <meta property="og:image" content="https://styles.redditmedia.com/t5_2qh0u/styles/communityIcon.png">
</head><body></body></html>
''';

      expect(
        parseSubredditIcon(page),
        'https://styles.redditmedia.com/t5_2qh0u/styles/communityIcon.png',
      );
    });

    test('a subreddit with no artwork has none to find', () {
      expect(
        parseSubredditIcon('<html><body>nothing here</body></html>'),
        isNull,
      );
      expect(parseSubredditIcon(''), isNull);
    });
  });

  group('the over-18 gate', () {
    test('is recognised so it can be answered with a cookie', () {
      const gate = '''
<html><body><div class="content"><form action="/over18?dest=%2Fr%2Fx" method="post">
  <input type="hidden" name="over18" value="yes"><button>Continue</button>
</form></div></body></html>
''';

      expect(isOver18Gate(gate), isTrue);
    });

    test('an ordinary listing is not the gate', () {
      expect(isOver18Gate(_page(_link)), isFalse);
    });
  });
}
