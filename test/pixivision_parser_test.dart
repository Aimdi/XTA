import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixivision_parser.dart';

String _fixture(String name) => File('test/fixtures/Pixivision/$name').readAsStringSync();

Map<String, Object?> _fields(PixivisionWork work) => {
  'artwork': work.artworkId,
  'user': work.userId,
  'title': work.title,
  'name': work.userName,
  'image': work.imageUrl,
  'avatar': work.avatarUrl,
};

void main() {
  group('the English layout', () {
    final article = parsePixivisionArticle(_fixture('article_en.html'));

    test('reads the title, the description as the intro and the cover', () {
      expect(article.title, 'Cats in Autumn');
      expect(article.intro, 'Autumn is here, and the cats know it.\n\nEnjoy these cosy pieces!');
      expect(article.coverUrl, contains('1000_p0_master1200.jpg'));
    });

    test('lists each featured work once, with its artist, and nothing outside the article', () {
      expect(article.works.map(_fields), [
        {
          'artwork': 1001,
          'user': 501,
          'title': 'Maple Cat',
          'name': 'Mika',
          'image': 'https://i.pximg.net/c/768x1200_80/img-master/img/2024/09/01/00/00/00/1001_p0_master1200.jpg',
          'avatar': 'https://i.pximg.net/user-profile/img/2020/01/01/00/00/00/501_170.jpg',
        },
        {
          'artwork': 1002,
          'user': 502,
          'title': 'Sleepy Leaves',
          'name': 'Sora',
          'image': 'https://i.pximg.net/c/768x1200_80/img-master/img/2024/09/02/00/00/00/1002_p0_master1200.jpg',
          'avatar': 'https://i.pximg.net/user-profile/img/2021/01/01/00/00/00/502_170.jpg',
        },
      ]);
    });
  });

  group('the Chinese layout', () {
    final article = parsePixivisionArticle(_fixture('article_zh.html'));

    test('takes the opening paragraphs as the intro when there is no description', () {
      expect(article.title, '秋天的猫咪');
      expect(article.intro, '秋天来了。\n\n一起欣赏猫咪插画吧！');
    });

    test('reads lazy images, tracking queries and the older member links', () {
      expect(article.works.map(_fields), [
        {
          'artwork': 2001,
          'user': 601,
          'title': '枫叶猫',
          'name': '小雪',
          'image': 'https://i.pximg.net/c/768x1200_80/img-master/img/2024/09/03/00/00/00/2001_p0_master1200.jpg',
          'avatar': 'https://i.pximg.net/user-profile/img/2022/01/01/00/00/00/601_170.jpg',
        },
        {
          'artwork': 2002,
          'user': 602,
          'title': '落叶午睡',
          'name': '阿空',
          'image': 'https://i.pximg.net/c/768x1200_80/img-master/img/2024/09/04/00/00/00/2002_p0_master1200.jpg',
          'avatar': 'https://i.pximg.net/user-profile/img/2022/01/02/00/00/00/602_170.jpg',
        },
      ]);
    });
  });

  group('reshaped pages', () {
    test('a page with no article reads as empty rather than throwing', () {
      final article = parsePixivisionArticle('<html><body><p>Maintenance</p></body></html>');
      expect(article.works, isEmpty);
      expect(article.isEmpty, isFalse, reason: 'the stray paragraph is all the page says');
      expect(parsePixivisionArticle('').isEmpty, isTrue);
    });

    test('a work without an artist link is left out instead of borrowing a neighbour', () {
      final article = parsePixivisionArticle('''
        <article><div class="am__body">
          <div><a href="https://www.pixiv.net/artworks/1"><img src="https://i.pximg.net/1.jpg"></a></div>
          <div>
            <a href="https://www.pixiv.net/artworks/2">Two</a>
            <a href="https://www.pixiv.net/users/20">Artist</a>
          </div>
        </div></article>''');
      expect(article.works.map((work) => (work.artworkId, work.userId, work.userName)), [(2, 20, 'Artist')]);
    });

    test('a block missing its title and pictures still yields the ids', () {
      final article = parsePixivisionArticle('''
        <article><div class="am__body"><div>
          <a href="/en/artworks/7"></a><a href="/en/users/70"></a>
        </div></div></article>''');
      final work = article.works.single;
      expect((work.artworkId, work.userId, work.title, work.userName), (7, 70, '', ''));
      expect(work.imageUrl, isNull);
      expect(work.avatarUrl, isNull);
    });
  });

  test('the page language follows the XTA locale', () {
    final mapped = {
      for (final locale in ['ja', 'ko', 'zh', 'zh_Hans', 'zh_Hant', 'zh-TW', 'en', 'de', 'pt_BR'])
        locale: pixivisionLanguage(locale),
    };
    expect(mapped, {
      'ja': 'ja',
      'ko': 'ko',
      'zh': 'zh',
      'zh_Hans': 'zh',
      'zh_Hant': 'zh-tw',
      'zh-TW': 'zh-tw',
      'en': 'en',
      'de': 'en',
      'pt_BR': 'en',
    });
    expect(pixivisionArticleUrl(42, 'zh-tw'), 'https://www.pixivision.net/zh-tw/a/42');
  });
}
