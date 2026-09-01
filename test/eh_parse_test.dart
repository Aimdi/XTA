import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_parse.dart';

void main() {
  group('EhGallery.titleFor', () {
    test('prefers Japanese only when asked', () {
      const gallery = EhGallery(
        gid: 1,
        token: 't',
        title: 'English',
        titleJpn: '日本語',
      );
      expect(gallery.titleFor(preferJapanese: true), '日本語');
      expect(gallery.titleFor(preferJapanese: false), 'English');
    });
  });

  group('EhCategory', () {
    test('parses labels and builds exclude mask', () {
      expect(EhCategory.tryParse('Doujinshi'), EhCategory.doujinshi);
      expect(EhCategory.tryParse('Artist CG'), EhCategory.artistCg);
      expect(
        EhCategory.excludeMask({EhCategory.doujinshi, EhCategory.manga}),
        isNonZero,
      );
      expect(EhCategory.excludeMask(EhCategory.values.toSet()), 0);
    });
  });

  group('parseEhGalleryList', () {
    test('extracts galleries from compact list HTML', () {
      const html = '''
<table>
<tr><td class="gl1c glcat"><div class="cn ct2">Doujinshi</div></td>
<td class="gl2c"><div class="glthumb"><div><img src="https://ehgt.org/a.webp" /></div>
<div><div>12 pages</div></div></div>
<div id="posted_1">2026-08-11 21:36</div></td>
<td class="gl3c glname"><a href="https://e-hentai.org/g/4114065/e6e34036d1/">
<div class="glink">Sample Title</div></a></td>
<td class="gl4c glhide"><div><a href="#">Uploader</a></div></td></tr>
</table>
<a id="unext" href="https://e-hentai.org/?next=1">&gt;</a>
''';
      final page = parseEhGalleryList(html);
      expect(page.galleries, hasLength(1));
      final g = page.galleries.single;
      expect(g.gid, 4114065);
      expect(g.token, 'e6e34036d1');
      expect(g.title, 'Sample Title');
      expect(g.category, EhCategory.doujinshi);
      expect(g.pageCount, 12);
      expect(page.hasMore, isTrue);
    });
  });

  group('parseEhGalleryDetail', () {
    test('reads title tags and previews', () {
      const html = '''
<h1 id="gn">English Title</h1>
<h1 id="gj">日本語</h1>
<div id="gdc"><div>Manga</div></div>
<div id="gdn"><a>Alice</a></div>
<table><tr><td>Posted:</td><td>2026-01-02 03:04</td></tr>
<tr><td>Length:</td><td>3 pages</td></tr></table>
<div id="rating_label">Average: 4.50</div>
<div id="gd1" style="background:url(https://ehgt.org/c.webp)"></div>
<div id="td_artist:bob"></div>
<div id="td_female:solo"></div>
<table class="ptt"><tr>
<td class="ptds"><a>1</a></td>
<td><a href="https://e-hentai.org/g/9/tok/?p=1">2</a></td>
<td><a href="https://e-hentai.org/g/9/tok/?p=2">3</a></td>
</tr></table>
<div id="gdt">
<a href="https://e-hentai.org/s/aaa111/9-1"><div style="background:url(https://ehgt.org/s.webp) -0px 0"></div></a>
<a href="https://e-hentai.org/s/bbb222/9-2"><div style="background:url(https://ehgt.org/s.webp) -200px 0"></div></a>
</div>
''';
      final detail = parseEhGalleryDetail(html, gid: 9, token: 'tok');
      expect(detail, isNotNull);
      expect(detail!.title, 'English Title');
      expect(detail.titleJpn, '日本語');
      expect(detail.category, EhCategory.manga);
      expect(detail.uploader, 'Alice');
      expect(detail.pageCount, 3);
      expect(detail.rating, 4.5);
      expect(detail.tags, containsAll(['artist:bob', 'female:solo']));
      expect(detail.previews.map((p) => p.page), [1, 2]);
      expect(detail.previews.first.thumbUrl, 'https://ehgt.org/s.webp');
      expect(detail.previews[1].thumbOffsetX, -200);
      expect(detail.previewSheetIndex, 0);
      expect(detail.previewSheetCount, 3);
    });
  });

  group('parseEhImagePage', () {
    test('reads image and navigation', () {
      const html = '''
<img id="img" src="https://cdn.example/page.webp" />
<a id="prev" href="https://e-hentai.org/s/aaa/1-1"></a>
<a id="next" href="https://e-hentai.org/s/ccc/1-3"></a>
''';
      final page = parseEhImagePage(html, page: 2);
      expect(page!.imageUrl, 'https://cdn.example/page.webp');
      expect(page.originalImageUrl, isNull);
      expect(page.displayUrl(signedIn: false), 'https://cdn.example/page.webp');
      expect(page.nextPageUrl, contains('1-3'));
      expect(page.prevPageUrl, contains('1-1'));
    });

    test('reads fullimg.php and uses it only when signed in', () {
      const html = '''
<img src="https://hath.example/h/abc/keystamp=1;xres=1280/page.jpg" id="img" />
<div id="i7">
<a href="https://e-hentai.org/fullimg.php?gid=9&amp;page=2&amp;key=abc">
Download original 2000 x 3000 :: 1.2 MB</a>
</div>
''';
      final page = parseEhImagePage(html, page: 2);
      expect(
        page!.imageUrl,
        'https://hath.example/h/abc/keystamp=1;xres=1280/page.jpg',
      );
      expect(
        page.originalImageUrl,
        'https://e-hentai.org/fullimg.php?gid=9&page=2&key=abc',
      );
      expect(page.displayUrl(signedIn: true), page.originalImageUrl);
      expect(page.displayUrl(signedIn: false), page.imageUrl);
    });

    test('reads the current /fullimg/gid/page/key/file href', () {
      const html = '''
<img id="img" src="https://hath.example/h/abc/keystamp=1;xres=800/0001.webp" />
<a href="https://e-hentai.org/fullimg/4116360/1/g8l82w4amxz/0001.png">Download original 1536 x 2040 2.33 MiB</a>
''';
      final page = parseEhImagePage(html, page: 1);
      expect(
        page!.originalImageUrl,
        'https://e-hentai.org/fullimg/4116360/1/g8l82w4amxz/0001.png',
      );
      expect(page.displayUrl(signedIn: true), page.originalImageUrl);
    });
  });

  group('ehRequestCookies', () {
    test('adds a 2400px uconfig when none is present', () {
      expect(ehRequestCookies(''), 'uconfig=xr_2400-ts_l-nw_1');
      expect(
        ehRequestCookies('ipb_member_id=1; ipb_pass_hash=abc'),
        'ipb_member_id=1; ipb_pass_hash=abc; uconfig=xr_2400-ts_l-nw_1',
      );
    });

    test('upgrades a mobile-sized uconfig without dropping other flags', () {
      expect(
        ehRequestCookies('uconfig=dm_t-xr_780-uh_y'),
        'uconfig=dm_t-xr_2400-uh_y-ts_l-nw_1',
      );
    });
  });

  group('parseEhComments', () {
    test('reads author body score and uploader flag', () {
      const html = '''
<div class="c1"><div class="c2"><div class="c3">Posted on 13 August 2026, 16:44 by: &nbsp; <a href="#">Alice</a></div>
<div class="c4 nosel">Uploader Comment</div></div>
<div class="c6" id="comment_0">Hello<br />world</div></div>
<div class="c1"><div class="c2"><div class="c3">Posted on 13 August 2026, 16:52 by: &nbsp; <a href="#">Bob</a></div>
<div class="c5 nosel">Score <span id="comment_score_1">+30</span></div></div>
<div class="c6" id="comment_1">Nice</div></div>
''';
      final comments = parseEhComments(html);
      expect(comments, hasLength(2));
      expect(comments.first.author, 'Alice');
      expect(comments.first.uploader, isTrue);
      expect(comments.first.body, contains('Hello'));
      expect(comments[1].author, 'Bob');
      expect(comments[1].score, '+30');
      expect(comments[1].uploader, isFalse);
    });
  });

  group('ehBuildSearch', () {
    test('adds language tag and min rating', () {
      final built = ehBuildSearch(
        query: 'flan',
        catMask: 0,
        minRating: 4,
        language: 'english',
      );
      expect(built.query, 'flan language:english');
      expect(built.params['f_sr'], 'on');
      expect(built.params['f_srdd'], '4');
    });
  });

  group('ehImagePageUri', () {
    test('appends nl for a broken-image reload', () {
      expect(
        parseEhReloadKey('onerror="nl(\'41173-496295\')"'),
        '41173-496295',
      );
      final uri = ehImagePageUri(
        host: 'https://e-hentai.org',
        pageToken: 'abc',
        gid: 9,
        page: 2,
        reloadKey: '41173-496295',
      );
      expect(uri.queryParameters['nl'], '41173-496295');
    });
  });

  group('parseEhPageLink', () {
    test('reads token gid and page', () {
      final link = parseEhPageLink(
        'https://e-hentai.org/s/d9987f2de6/4113416-2',
      );
      expect(link?.pageToken, 'd9987f2de6');
      expect(link?.gid, 4113416);
      expect(link?.page, 2);
      expect(parseEhPageLink(null), isNull);
    });
  });

  group('parseEhGdata', () {
    test('maps gmetadata JSON', () {
      final galleries = parseEhGdata({
        'gmetadata': [
          {
            'gid': 1,
            'token': 'abc',
            'title': 'T',
            'title_jpn': '日',
            'category': 'Western',
            'thumb': 'https://ehgt.org/t.webp',
            'uploader': 'u',
            'posted': '1700000000',
            'filecount': '5',
            'rating': '3.2',
            'tags': ['language:english'],
          },
        ],
      });
      expect(galleries.single.gid, 1);
      expect(galleries.single.category, EhCategory.western);
      expect(galleries.single.pageCount, 5);
      expect(galleries.single.tags, ['language:english']);
    });
  });
}
