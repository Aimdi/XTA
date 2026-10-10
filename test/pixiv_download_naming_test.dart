import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_download_naming.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

import 'support/pixiv_reader_harness.dart';

PixivIllust _work({String title = 'Sommerfest', String userName = 'Mika', bool r18 = false}) => PixivIllust(
  id: 120,
  title: title,
  caption: '',
  type: 'illust',
  thumbnailUrl: 'https://i.pximg.net/c/540x540_70/120_p0.jpg',
  originalUrls: const ['https://i.pximg.net/img-original/img/2026/07/01/120_p0.png'],
  pageUrls: const ['https://i.pximg.net/c/600x1200_90/120_p0.jpg'],
  pageCount: 1,
  userId: 42,
  userName: userName,
  userAccount: 'mika',
  isR18: r18,
);

void main() {
  group('pixivFileName', () {
    test('fills every placeholder and keeps the extension', () {
      expect(pixivFileName(pixivFileNameTemplateDefault, _work(), 3, extension: '.png'), '120_p3.png');
      expect(
        pixivFileName('{user_name} ({user_id}) - {title} - {illust_id}_{part}', _work(), 0, extension: '.jpg'),
        'Mika (42) - Sommerfest - 120_0.jpg',
      );
    });

    test('replaces characters no file system accepts, from the template and the work alike', () {
      final work = _work(title: 'a/b\\c:d*e?f"g<h>i|j', userName: 'x\ty');
      expect(
        pixivFileName('{title}_{user_name}_p{part}', work, 1, extension: '.png'),
        'a_b_c_d_e_f_g_h_i_j_x_y_p1.png',
      );
      expect(pixivFileName('a:b_{part}', work, 0, extension: '.gif'), 'a_b_0.gif');
    });

    test('a title that reads like a placeholder stays as written', () {
      expect(pixivFileName('{title}_p{part}', _work(title: '{user_id}'), 0, extension: '.png'), '{user_id}_p0.png');
    });

    test('unknown placeholders are kept, outer dots and spaces trimmed, long names cut', () {
      expect(pixivFileName('{nope}_{part}', _work(), 0, extension: '.png'), '{nope}_0.png');
      expect(pixivFileName(' ..{title}.. ', _work(), 0, extension: '.png'), 'Sommerfest.png');
      final long = pixivFileName('{title}', _work(title: 'x' * 400), 0, extension: '.png');
      expect(long, '${'x' * 180}.png');
    });

    test('a name that ends up empty falls back to the work and page', () {
      expect(pixivFileName('{title}', _work(title: '...'), 2, extension: '.jpg'), '120_p2.jpg');
    });
  });

  test('the extension comes from the original file, else .jpg', () {
    expect(pixivExtensionOf('https://i.pximg.net/img-original/120_p0.PNG'), '.png');
    expect(pixivExtensionOf('https://i.pximg.net/img-original/120_p0.jpg?x=1'), '.jpg');
    expect(pixivExtensionOf('https://i.pximg.net/img-original/120_p0'), '.jpg');
    expect(pixivExtensionOf('not a url %%'), '.jpg');
  });

  test('{part} is required', () {
    expect(pixivTemplateHasPart('{illust_id}_p{part}'), isTrue);
    expect(pixivTemplateHasPart('{illust_id}'), isFalse);
  });

  group('PixivSaveNaming.of', () {
    PrefServiceCache prefs(Map<String, Object> values) => PrefServiceCache(cache: values);

    test('reads the template and both folder settings', () {
      final naming = PixivSaveNaming.of(
        prefs({
          optionPluginPixivFileNameTemplate: '{user_name}_{part}',
          optionPluginPixivFolderPerArtist: true,
          optionPluginPixivFolderR18: true,
        }),
      );
      expect(naming.pageName(_work(), 0), 'Mika_0.png');
      expect(naming.workName(_work(), '.gif'), 'Mika_0.gif');
      expect(naming.subfolder(_work()), 'Mika_42');
      expect(naming.subfolder(_work(r18: true)), 'R-18/Mika_42');
    });

    test('a stored template without {part} is not used', () {
      final naming = PixivSaveNaming.of(prefs({optionPluginPixivFileNameTemplate: '{title}'}));
      expect(naming.template, pixivFileNameTemplateDefault);
      expect(PixivSaveNaming.of(prefs({})).template, pixivFileNameTemplateDefault);
    });

    test('R-18 works alone go to R-18, and an artist name cannot leave its folder', () {
      final r18Only = PixivSaveNaming.of(prefs({optionPluginPixivFolderR18: true}));
      expect(r18Only.subfolder(_work()), isNull);
      expect(r18Only.subfolder(_work(r18: true)), 'R-18');
      final perArtist = PixivSaveNaming.of(prefs({optionPluginPixivFolderPerArtist: true}));
      expect(perArtist.subfolder(_work(userName: '../evil/..')), '.._evil_.._42');
    });

    test('nothing set means no subfolder', () {
      expect(const PixivSaveNaming().subfolder(_work(r18: true)), isNull);
    });
  });

  test('batch requests carry the template name and the subfolder', () {
    final naming = PixivSaveNaming.of(
      PrefServiceCache(
        cache: {optionPluginPixivFileNameTemplate: '{title}_p{part}', optionPluginPixivFolderPerArtist: true},
      ),
    );
    final requests = pixivPageRequests(pixivWork(pages: 3), 'content://tree/x', naming: naming, pages: [2, 0]);
    expect(requests.map((request) => request.fileName), ['Sommerfest_p2.png', 'Sommerfest_p0.png']);
    expect(requests.map((request) => request.subfolder).toSet(), {'Mika_42'});
    expect(requests.first.uri.path, endsWith('120_p2.png'));
  });
}
