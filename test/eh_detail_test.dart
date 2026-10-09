import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_parse.dart';
import 'package:xta/plugins/ehviewer/eh_tags.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';

const _detail = '''
<h1 id="gn">Sommer Book</h1><h1 id="gj">夏の本</h1>
<div id="gdd"><table>
<tr><td class="gdt1">Posted:</td><td class="gdt2">2024-08-11 21:36</td></tr>
<tr><td class="gdt1">Language:</td><td class="gdt2">English &nbsp;<span class="halp" title="This gallery has been translated from the original language text.">TR</span></td></tr>
<tr><td class="gdt1">File Size:</td><td class="gdt2">96.57 MiB</td></tr>
<tr><td class="gdt1">Length:</td><td class="gdt2">24 pages</td></tr>
<tr><td class="gdt1">Favorited:</td><td class="gdt2" id="favcount">1234 times</td></tr>
</table></div>
<td id="rating_label">Average: 4.62</td><span id="rating_count">321</span>
<div id="taglist"><table>
<tr><td class="tc">language:</td><td>
<div id="td_language:english" class="gt" style="opacity:1.0"><a id="ta_language:english" href="https://e-hentai.org/tag/language:english">english</a></div>
<div id="td_language:translated" class="gt"><a id="ta_language:translated" href="#">translated</a></div></td></tr>
<tr><td class="tc">female:</td><td>
<div id="td_female:big_breasts" class="gt"><a id="ta_female:big_breasts" href="#">big breasts</a></div>
<div id="td_female:twin_tails" class="gtl"><a id="ta_female:twin_tails" href="#">twin tails</a></div></td></tr>
<tr><td class="tc">parody:</td><td>
<div class="gtw" id="td_parody:original"><a id="ta_parody:original" href="#">original</a></div></td></tr>
</table></div>
''';

void main() {
  group('gallery detail', () {
    final detail = parseEhGalleryDetail(_detail, gid: 1, token: 'abc')!;

    test('tags keep their spaces and their namespaces', () {
      expect(detail.tags, [
        'language:english',
        'language:translated',
        'female:big breasts',
        'female:twin tails',
        'parody:original',
      ]);
    });

    test('dashed tags are weak', () {
      expect(detail.weakTags, {'female:twin tails', 'parody:original'});
    });

    test('facts from the detail table', () {
      expect(detail.language, 'English');
      expect(detail.translated, isTrue);
      expect(detail.fileSizeBytes, (96.57 * 1024 * 1024).round());
      expect(detail.favoritedCount, 1234);
      expect(detail.ratingCount, 321);
      expect(detail.rating, 4.62);
      expect(detail.pageCount, 24);
    });

    test('a bare tag cell falls back to its id', () {
      expect(parseEhDetailTags('<div id="td_artist:big_bob"></div>').single.raw, 'artist:big bob');
    });
  });

  group('helpers', () {
    test('sizes and favorite counts read as the site writes them', () {
      expect(ehParseFileSize('512 KiB'), 512 * 1024);
      expect(ehParseFileSize('1.5 GiB'), (1.5 * 1024 * 1024 * 1024).round());
      expect(ehParseFileSize('?'), isNull);
      expect(ehParseFavorited('Never'), 0);
      expect(ehParseFavorited('Once'), 1);
      expect(ehParseFavorited('87 times'), 87);
    });

    test('a tag searches for exactly itself', () {
      expect(EhTag.parse('female:big breasts').query, r'female:"big breasts$"');
      expect(EhTag.parse('lolicon').query, r'"lolicon$"');
      expect(EhTag.parse('weird:thing').namespace, isNull);
      expect(EhTag.parse('weird:thing').raw, 'weird:thing');
    });

    test('groups follow the site order with namespaceless tags last', () {
      final groups = ehTagGroups(['female:a', 'artist:b', 'x', 'language:c']);
      expect(groups.map((g) => g.namespace), [EhNamespace.language, EhNamespace.artist, EhNamespace.female, null]);
      expect(ehTagKind(EhNamespace.parody), PluginTagKind.copyright);
      expect(ehTagKind(EhNamespace.group), PluginTagKind.artist);
      expect(ehTagKind(null), isNull);
    });
  });
}
