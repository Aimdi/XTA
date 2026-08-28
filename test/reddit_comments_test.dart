import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_comments_json.dart';
import 'package:xta/utils/json.dart';

/// Shaped like old.reddit's comment area: `div.thing.comment` carrying its own
/// `entry`, with replies inside a `.child > .sitetable`.
String _comment(
  String id,
  String author,
  String body, {
  String score = '42 points',
  String replies = '',
}) =>
    '''
<div class="thing id-t1_$id comment" data-fullname="t1_$id" data-author="$author">
  <div class="entry unvoted">
    <p class="tagline">
      <a href="/user/$author" class="author">$author</a>
      <span class="score unvoted">$score</span>
      <time datetime="2026-07-01T10:00:00+00:00">1 hour ago</time>
    </p>
    <form class="usertext"><div class="usertext-body"><div class="md"><p>$body</p></div></div></form>
  </div>
  <div class="child">${replies.isEmpty ? '' : '<div class="sitetable listing">$replies</div>'}</div>
</div>
''';

String _page(String comments, {String post = ''}) =>
    '''
<!doctype html><html><body>
  <div id="siteTable">$post</div>
  <div class="commentarea"><div class="sitetable nestedlisting">$comments</div></div>
</body></html>
''';

Map<String, dynamic> _t1({
  required String id,
  String author = 'someone',
  String body = 'Well said',
  int score = 42,
  double created = 1769000000,
  bool isSubmitter = false,
  String? permalink,
  Object? replies = '',
}) => {
  'kind': 't1',
  'data': {
    'id': id,
    'author': author,
    'body': body,
    'score': score,
    'created_utc': created,
    'is_submitter': isSubmitter,
    'permalink': permalink ?? '/r/dartlang/comments/abc/$id/',
    'replies': replies,
  },
};

Map<String, dynamic> _listing(List<Map<String, dynamic>> children) => {
  'kind': 'Listing',
  'data': {'children': children},
};

void main() {
  group('JSON comment tree', () {
    test('parses a nested t1 tree', () {
      final listing = _listing([
        _t1(
          id: 'a',
          author: 'first',
          body: 'Question',
          replies: _listing([
            _t1(id: 'b', author: 'second', body: 'Answer', score: 7),
          ]),
        ),
      ]);

      final root = commentsFromListing(Json(listing)).single;

      expect(root.id, 'a');
      expect(root.author, 'first');
      expect(root.body, 'Question');
      expect(root.score, 42);
      expect(
        root.createdAt,
        DateTime.fromMillisecondsSinceEpoch(
          1769000000 * 1000,
          isUtc: true,
        ).toLocal(),
      );
      expect(root.permalink, '/r/dartlang/comments/abc/a/');
      expect(root.replies.single.id, 'b');
      expect(root.replies.single.body, 'Answer');
      expect(root.replies.single.score, 7);
    });

    test('a more child becomes a stub with the held-back count', () {
      final listing = _listing([
        {
          'kind': 'more',
          'data': {
            'count': 34,
            'id': 'xyz',
            'children': ['c1', 'c2'],
          },
        },
      ]);

      final stub = commentsFromListing(Json(listing)).single;

      expect(stub.isStub, isTrue);
      expect(stub.moreCount, 34);
      expect(stub.id, 'xyz');
      expect(stub.body, isEmpty);
      expect(stub.permalink, isNull);
    });

    // The bug: a `more` under a comment had no permalink, so the row rendered
    // un-tappable — "more replies · 47" that did nothing. The held-back
    // replies live on the parent's own page, so that is where the stub points.
    test('a nested more stub points at its parent comment', () {
      final listing = _listing([
        _t1(
          id: 'a',
          replies: _listing([
            {
              'kind': 'more',
              'data': {
                'count': 47,
                'id': 'm1',
                'children': ['x'],
              },
            },
          ]),
        ),
      ]);

      final stub = commentsFromListing(Json(listing)).single.replies.single;

      expect(stub.isStub, isTrue);
      expect(stub.permalink, '/r/dartlang/comments/abc/a/');
    });

    test('a top-level more stub points at the post itself', () {
      final listing = _listing([
        {
          'kind': 'more',
          'data': {
            'count': 500,
            'id': 'm2',
            'children': ['y'],
          },
        },
      ]);

      final stub = commentsFromListing(
        Json(listing),
        parentPermalink: '/r/dartlang/comments/abc/',
      ).single;

      expect(stub.permalink, '/r/dartlang/comments/abc/');
    });

    test('more without id uses the first child id', () {
      final listing = _listing([
        {
          'kind': 'more',
          'data': {
            'count': 2,
            'children': ['first_child', 'second'],
          },
        },
      ]);

      expect(commentsFromListing(Json(listing)).single.id, 'first_child');
    });

    test('empty replies string is no replies', () {
      final listing = _listing([_t1(id: 'a', replies: '')]);

      expect(commentsFromListing(Json(listing)).single.replies, isEmpty);
    });

    test('the submitter flag and media tokens are read', () {
      final listing = _listing([
        _t1(
          id: 'op',
          isSubmitter: true,
          body: '![gif](giphy|l0HlvtIPzPdt2usKs|downsized)',
        ),
      ]);
      final comment = commentsFromListing(Json(listing)).single;

      expect(comment.isSubmitter, isTrue);
      expect(comment.mediaUrls, [
        'https://media.giphy.com/media/l0HlvtIPzPdt2usKs/giphy.gif',
      ]);
      expect(comment.body, isEmpty);
    });

    test('a bare image URL becomes media and leaves no body', () {
      final listing = _listing([
        _t1(id: 'pic', body: 'https://i.redd.it/abc.gif'),
      ]);
      final comment = commentsFromListing(Json(listing)).single;

      expect(comment.mediaUrls, ['https://i.redd.it/abc.gif']);
      expect(comment.body, isEmpty);
    });

    test('a >image> leftover next to a picture is dropped', () {
      final listing = _listing([
        _t1(id: 'pic', body: '>image> https://i.redd.it/abc.jpg'),
      ]);
      final comment = commentsFromListing(Json(listing)).single;

      expect(comment.mediaUrls, ['https://i.redd.it/abc.jpg']);
      expect(comment.body, isEmpty);
    });

    test('a caption next to a picture keeps the words, not the URL', () {
      final listing = _listing([
        _t1(id: 'pic', body: 'nice one https://i.redd.it/abc.jpg'),
      ]);
      final comment = commentsFromListing(Json(listing)).single;

      expect(comment.mediaUrls, ['https://i.redd.it/abc.jpg']);
      expect(comment.body, 'nice one');
    });

    test('markdown links to pages stay in the body so they can be opened', () {
      final listing = _listing([
        _t1(
          id: 'link',
          body:
              'Download>> [OTs-14](https://drive.google.com/drive/folders/abc) [Source](https://x.com/foo/status/1)',
        ),
      ]);
      final comment = commentsFromListing(Json(listing)).single;

      expect(comment.mediaUrls, isEmpty);
      expect(comment.body, contains('[OTs-14]('));
      expect(comment.body, contains('[Source]('));
    });
  });

  group('reading a thread', () {
    test('takes the author, body, score and time', () {
      final comment = parseComments(
        _page(_comment('a', 'someone', 'Well said')),
      ).single;

      expect(comment.id, 'a');
      expect(comment.author, 'someone');
      expect(comment.body, 'Well said');
      expect(comment.score, 42);
      expect(
        comment.createdAt,
        DateTime.parse('2026-07-01T10:00:00Z').toLocal(),
      );
    });

    test('a picture comment shows the picture, not the URL that made it', () {
      const link =
          '<a href="https://i.redd.it/abc.gif">https://i.redd.it/abc.gif</a>';
      final comment = parseComments(
        _page(_comment('a', 'someone', link)),
      ).single;

      expect(comment.mediaUrls, ['https://i.redd.it/abc.gif']);
      expect(
        comment.body,
        isEmpty,
        reason: 'the text was only the link the picture came from',
      );
    });

    test('a picture-only comment is kept rather than skipped as empty', () {
      const link =
          '<a href="https://i.redd.it/abc.png">https://i.redd.it/abc.png</a>';

      expect(
        parseComments(_page(_comment('a', 'someone', link))),
        hasLength(1),
      );
    });

    test('words around a link survive', () {
      const body =
          'look at <a href="https://i.redd.it/abc.jpg">this</a> please';
      final comment = parseComments(
        _page(_comment('a', 'someone', body)),
      ).single;

      expect(comment.mediaUrls, ['https://i.redd.it/abc.jpg']);
      expect(comment.body, 'look at this please');
    });

    test('a labelled page link is kept as markdown, not dumped as the URL', () {
      const body =
          'Download>> <a href="https://drive.google.com/drive/folders/abc">OTs-14</a> <a href="https://x.com/foo/status/1">Source</a>';
      final comment = parseComments(
        _page(_comment('a', 'someone', body)),
      ).single;

      expect(comment.mediaUrls, isEmpty);
      expect(
        comment.body,
        'Download>> [OTs-14](https://drive.google.com/drive/folders/abc) [Source](https://x.com/foo/status/1)',
      );
    });

    test('a link to a page is left as text', () {
      const body =
          '<a href="https://example.com/story">https://example.com/story</a>';
      final comment = parseComments(
        _page(_comment('a', 'someone', body)),
      ).single;

      expect(comment.mediaUrls, isEmpty);
      expect(comment.body, 'https://example.com/story');
    });

    test('the same picture linked twice is shown once', () {
      const body =
          '<a href="https://i.redd.it/x.gif">a</a> <a href="https://i.redd.it/x.gif">b</a>';

      expect(
        parseComments(_page(_comment('a', 'someone', body))).single.mediaUrls,
        ['https://i.redd.it/x.gif'],
      );
    });

    test('a Reddit GIF token becomes the GIF, and never shows as raw text', () {
      const body = '![gif](giphy|l0HlvtIPzPdt2usKs|downsized)';
      final comment = parseComments(
        _page(_comment('a', 'someone', body)),
      ).single;

      expect(comment.mediaUrls, [
        'https://media.giphy.com/media/l0HlvtIPzPdt2usKs/giphy.gif',
      ]);
      expect(comment.body, isEmpty);
    });

    test('a token beside words takes only itself out of the text', () {
      const body = 'this exactly ![gif](giphy|abc123XYZ|downsized)';
      final comment = parseComments(
        _page(_comment('a', 'someone', body)),
      ).single;

      expect(comment.mediaUrls, hasLength(1));
      expect(comment.body, 'this exactly');
    });

    test('a >image> leftover next to an inlined picture is dropped', () {
      const body = '>image> <img src="https://i.redd.it/inline.png">';
      final comment = parseComments(
        _page(_comment('a', 'someone', body)),
      ).single;

      expect(comment.mediaUrls, ['https://i.redd.it/inline.png']);
      expect(comment.body, isEmpty);
    });

    test('an inlined img is picked up as well as a link', () {
      const body = '<img src="//i.redd.it/inline.png">';

      expect(
        parseComments(_page(_comment('a', 'someone', body))).single.mediaUrls,
        ['https://i.redd.it/inline.png'],
      );
    });

    test('replies hang off the comment they answer', () {
      final page = _page(
        _comment(
          'a',
          'first',
          'Question',
          replies: _comment('b', 'second', 'Answer'),
        ),
      );

      final root = parseComments(page).single;
      expect(root.replies.single.id, 'b');
      expect(root.replies.single.body, 'Answer');
    });

    test('nesting goes as deep as the page does', () {
      final deep = _comment(
        'a',
        'x',
        'one',
        replies: _comment(
          'b',
          'y',
          'two',
          replies: _comment('c', 'z', 'three'),
        ),
      );

      final root = parseComments(_page(deep)).single;
      expect(root.replies.single.replies.single.body, 'three');
      expect(root.totalCount, 3);
    });

    test('a comment does not swallow its replies\' text', () {
      // The entry has to be read off the comment itself, not the whole subtree.
      final page = _page(
        _comment('a', 'x', 'parent', replies: _comment('b', 'y', 'child')),
      );

      expect(parseComments(page).single.body, 'parent');
    });

    test('siblings keep their order', () {
      final page = _page(
        '${_comment('a', 'x', 'first')}${_comment('b', 'y', 'second')}',
      );

      expect(parseComments(page).map((c) => c.id), ['a', 'b']);
    });

    test('a hidden score is absent rather than zero', () {
      const noScore = '''
<div class="thing comment" data-fullname="t1_n" data-author="x">
  <div class="entry"><div class="usertext-body"><div class="md">New here</div></div></div>
</div>
''';

      expect(parseComments(_page(noScore)).single.score, isNull);
    });

    test('the submitter is marked', () {
      const op = '''
<div class="thing comment" data-fullname="t1_o" data-author="poster">
  <div class="entry"><p class="tagline"><a class="author submitter">poster</a></p>
  <div class="usertext-body"><div class="md">Mine</div></div></div>
</div>
''';

      expect(parseComments(_page(op)).single.isSubmitter, isTrue);
    });
  });

  group('rows that are not comments', () {
    test('a "load more" control is skipped', () {
      const more =
          '<div class="thing morechildren" data-fullname="t1_more_x"></div>';

      expect(
        parseComments(
          _page('$more${_comment('a', 'x', 'real')}'),
        ).map((c) => c.id),
        ['a'],
      );
    });

    test('a deleted comment with no body is left out', () {
      const empty =
          '<div class="thing comment" data-fullname="t1_d"><div class="entry"></div></div>';

      expect(parseComments(_page(empty)), isEmpty);
    });

    test('a page with no comment area yields none rather than throwing', () {
      expect(parseComments('<html><body>nothing</body></html>'), isEmpty);
      expect(parseComments(''), isEmpty);
    });
  });

  group('flattening for display', () {
    test('depth-first, with the depth carried alongside', () {
      final page = _page(
        _comment(
          'a',
          'x',
          'one',
          replies:
              '${_comment('b', 'y', 'two', replies: _comment('c', 'z', 'three'))}${_comment('d', 'w', 'four')}',
        ),
      );

      final flat = flattenComments(parseComments(page));

      expect(flat.map((e) => e.comment.id), ['a', 'b', 'c', 'd']);
      expect(flat.map((e) => e.depth), [0, 1, 2, 1]);
    });

    test('nothing to flatten is an empty list', () {
      expect(flattenComments(const []), isEmpty);
    });
  });

  group('the post body on a thread page', () {
    test('is read when the listing did not carry it', () {
      const post = '''
<div class="thing" data-fullname="t3_p">
  <div class="expando"><form class="usertext"><div class="usertext-body"><div class="md">
    <p>The full text</p>
  </div></div></form></div>
</div>
''';

      expect(parseSelfText(_page('', post: post)), 'The full text');
    });

    test('a link post has none', () {
      expect(parseSelfText(_page(_comment('a', 'x', 'hi'))), isNull);
    });
  });

  group('what the post page says the post points at', () {
    String postThing({String? dataUrl, String expando = ''}) =>
        '''
<div class="thing id-t3_p1 link" data-fullname="t3_p1"${dataUrl == null ? '' : ' data-url="$dataUrl"'}>
  <div class="entry"><p class="title"><a class="title" href="/r/x/comments/p1/t/">A post</a></p></div>
  <div class="expando">$expando</div>
</div>
''';

    test('the outbound link is read off the thing row', () {
      final media = parsePostMedia(
        _page('', post: postThing(dataUrl: 'https://i.redd.it/abc.jpg')),
      );

      expect(media.url, 'https://i.redd.it/abc.jpg');
      expect(media.images, isEmpty);
    });

    test(
      'a relative link — a self post pointing at itself — is not a link',
      () {
        expect(
          parsePostMedia(
            _page('', post: postThing(dataUrl: '/r/x/comments/p1/t/')),
          ).url,
          isNull,
        );
      },
    );

    test(
      'an expanded gallery leaves its files on the page, in order and unescaped',
      () {
        final media = parsePostMedia(
          _page(
            '',
            post: postThing(
              dataUrl: 'https://www.reddit.com/gallery/p1',
              expando:
                  '<div class="media-gallery">'
                  '<img src="https://preview.redd.it/one.jpg?width=640&amp;s=a">'
                  '<img src="https://preview.redd.it/two.jpg?width=640&amp;s=b">'
                  '</div>',
            ),
          ),
        );

        expect(media.images, [
          'https://preview.redd.it/one.jpg?width=640&s=a',
          'https://preview.redd.it/two.jpg?width=640&s=b',
        ]);
      },
    );

    test(
      'only Reddit-hosted files count; tracking pixels and avatars do not',
      () {
        final media = parsePostMedia(
          _page(
            '',
            post: postThing(
              dataUrl: 'https://example.com/story',
              expando:
                  '<img src="https://example.com/pixel.gif"><img src="https://i.redd.it/real.png">',
            ),
          ),
        );

        expect(media.images, ['https://i.redd.it/real.png']);
      },
    );

    test('the same file twice is one file', () {
      final media = parsePostMedia(
        _page(
          '',
          post: postThing(
            expando:
                '<img src="https://i.redd.it/a.png"><img src="https://i.redd.it/a.png">',
          ),
        ),
      );

      expect(media.images, ['https://i.redd.it/a.png']);
    });

    test(
      'low and high quality of the same picture collapse to the better one',
      () {
        final media = parsePostMedia(
          _page(
            '',
            post: postThing(
              dataUrl: 'https://www.reddit.com/gallery/p1',
              expando:
                  '<div class="media-gallery">'
                  '<img src="https://preview.redd.it/a.jpg?width=320&amp;s=lo">'
                  '<img src="https://preview.redd.it/a.jpg?width=1080&amp;s=hi">'
                  '<img src="https://i.redd.it/a.jpg">'
                  '</div>',
            ),
          ),
        );

        expect(media.images, ['https://i.redd.it/a.jpg']);
      },
    );

    test(
      'the same picture at two widths is one picture, preferring the larger',
      () {
        final media = parsePostMedia(
          _page(
            '',
            post: postThing(
              expando:
                  '<img src="https://preview.redd.it/abc.jpg?width=320&amp;s=a">'
                  '<img src="https://preview.redd.it/abc.jpg?width=1080&amp;s=b">',
            ),
          ),
        );

        expect(media.images, [
          'https://preview.redd.it/abc.jpg?width=1080&s=b',
        ]);
      },
    );

    test(
      'a preview and an i.redd.it of the same file collapse to i.redd.it',
      () {
        final media = parsePostMedia(
          _page(
            '',
            post: postThing(
              expando:
                  '<img src="https://preview.redd.it/abc.jpg?width=640&amp;s=a">'
                  '<img src="https://i.redd.it/abc.jpg">',
            ),
          ),
        );

        expect(media.images, ['https://i.redd.it/abc.jpg']);
      },
    );

    test('a page with no post is nothing, not a throw', () {
      final media = parsePostMedia('<html><body></body></html>');

      expect(media.url, isNull);
      expect(media.images, isEmpty);
    });
  });

  group('what a page holds back', () {
    test('a load-more control becomes a stub pointing at its parent', () {
      final page = _page(
        _comment(
          'a',
          'ann',
          'Parent',
          replies: '''
<div class="thing morechildren"><a href="javascript:void(0)">load more comments</a> (34 replies)</div>
''',
        ),
      );
      // The parent needs a permalink for the stub to point at.
      final withPermalink = page.replaceFirst(
        'data-author="ann"',
        'data-author="ann" data-permalink="/r/x/comments/p/t/a/"',
      );

      final flat = flattenComments(parseComments(withPermalink));

      expect(flat, hasLength(2));
      expect(flat[1].comment.isStub, isTrue);
      expect(flat[1].comment.moreCount, 34);
      expect(flat[1].comment.permalink, '/r/x/comments/p/t/a/');
      expect(
        flat[1].depth,
        1,
        reason: 'the held-back replies sit under their parent',
      );
    });

    test('a deep-thread continuation carries its own target', () {
      final page = _page(
        _comment(
          'a',
          'ann',
          'Deep',
          replies: '''
<div class="thing morerecursion"><a href="/r/x/comments/p/t/deep/">continue this thread</a></div>
''',
        ),
      );

      final flat = flattenComments(parseComments(page));

      expect(flat[1].comment.isStub, isTrue);
      expect(flat[1].comment.permalink, '/r/x/comments/p/t/deep/');
    });

    test(
      'a control with nowhere to go is dropped rather than dead on screen',
      () {
        final page = _page(
          _comment(
            'a',
            'ann',
            'Parent',
            replies: '''
<div class="thing morechildren"><a href="javascript:void(0)">load more comments</a></div>
''',
          ),
        );

        expect(flattenComments(parseComments(page)), hasLength(1));
      },
    );
  });

  group('folding a thread', () {
    List<FlatComment> flat() => [
      (comment: RedditComment(id: 'a', body: 'top'), depth: 0),
      (comment: RedditComment(id: 'b', body: 'reply'), depth: 1),
      (comment: RedditComment(id: 'c', body: 'reply to reply'), depth: 2),
      (comment: RedditComment(id: 'd', body: 'second top'), depth: 0),
    ];

    test('nothing collapsed shows everything, hiding nothing', () {
      final rows = visibleComments(flat(), const {});

      expect(rows, hasLength(4));
      expect(rows.every((r) => r.hidden == 0), isTrue);
    });

    test('a collapsed comment keeps its row, its subtree does not', () {
      final rows = visibleComments(flat(), {'a'});

      expect(rows.map((r) => r.entry.comment.id), ['a', 'd']);
      expect(rows.first.hidden, 2);
    });

    test('collapsing a leaf hides nothing but still marks the row', () {
      final rows = visibleComments(flat(), {'c'});

      expect(rows, hasLength(4));
      expect(rows[2].hidden, 0);
    });
  });

  group('top-level load-more stubs', () {
    // The bug: the root listing was parsed with no parent, so a bottom-of-page
    // "load more comments (500 replies)" vanished — the thread just ended.
    test('a root morechildren row survives, pointing at the post page', () {
      final html = _page(
        '${_comment('a', 'someone', 'First')}'
        '<div class="thing morechildren"><a href="#">load more comments</a> (512 replies)</div>',
      );

      final comments = parseComments(
        html,
        postPermalink: '/r/dartlang/comments/abc/',
      );

      expect(comments, hasLength(2));
      expect(comments.last.isStub, isTrue);
      expect(comments.last.moreCount, 512);
      expect(comments.last.permalink, '/r/dartlang/comments/abc/');
    });
  });
}
