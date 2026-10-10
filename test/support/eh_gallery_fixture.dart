import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';

const ehFixtureGallery = EhGallery(gid: 9, token: 'tok', title: 'Sommer Book', titleJpn: '夏の本');

const ehFixturePerSheet = 4;

String _tile(int sheet, int page, int slot, {required bool thumbs}) {
  final style = thumbs
      ? 'width:200px;height:283px;background:transparent url(https://ehgt.org/sheet$sheet.webp) -${slot * 200}px 0 no-repeat'
      : 'width:200px;height:283px';
  return '<a href="https://e-hentai.org/s/tk$page/9-$page"><div title="Page $page" style="$style"></div></a>';
}

String _comment(int index, {required bool uploader}) =>
    '<div class="c1"><div class="c2"><div class="c3">Posted on 1$index August 2026, 16:44 by: &nbsp; '
    '<a href="#">${uploader ? 'Alice' : 'Reader $index'}</a></div>'
    '${uploader ? '<div class="c4 nosel">Uploader Comment</div>' : '<div class="c5 nosel">Score <span id="comment_score_$index">+$index</span></div>'}'
    '</div><div class="c6" id="comment_$index">Comment number $index</div></div>';

/// One sheet (`?p=[sheet]`) of a gallery page as the site serves it.
String ehGalleryHtml({int sheet = 0, int sheetCount = 3, int comments = 4, bool thumbs = false}) {
  final first = sheet * ehFixturePerSheet + 1;
  final tiles = [for (var slot = 0; slot < ehFixturePerSheet; slot++) _tile(sheet, first + slot, slot, thumbs: thumbs)];
  final links = [
    for (var p = 1; p < sheetCount; p++) '<td><a href="https://e-hentai.org/g/9/tok/?p=$p">${p + 1}</a></td>',
  ];
  return '''
<h1 id="gn">Sommer Book</h1><h1 id="gj">夏の本</h1>
<div id="gdc"><div class="cs ct2">Doujinshi</div></div>
<div id="gdn"><a href="#">Alice</a></div>
<div id="gdd"><table>
<tr><td class="gdt1">Posted:</td><td class="gdt2">2024-08-11 21:36</td></tr>
<tr><td class="gdt1">Language:</td><td class="gdt2">English &nbsp;<span class="halp" title="translated">TR</span></td></tr>
<tr><td class="gdt1">File Size:</td><td class="gdt2">96.57 MiB</td></tr>
<tr><td class="gdt1">Length:</td><td class="gdt2">24 pages</td></tr>
<tr><td class="gdt1">Favorited:</td><td class="gdt2" id="favcount">1234 times</td></tr>
</table></div>
<td id="rating_label">Average: 4.62</td><span id="rating_count">321</span>
<div id="taglist"><table>
<tr><td class="tc">language:</td><td>
<div id="td_language:english" class="gt"><a id="ta_language:english" href="#">english</a></div></td></tr>
<tr><td class="tc">parody:</td><td>
<div id="td_parody:original" class="gt"><a id="ta_parody:original" href="#">original</a></div></td></tr>
<tr><td class="tc">female:</td><td>
<div id="td_female:big_breasts" class="gt"><a id="ta_female:big_breasts" href="#">big breasts</a></div>
<div id="td_female:twin_tails" class="gtl"><a id="ta_female:twin_tails" href="#">twin tails</a></div></td></tr>
</table></div>
<table class="ptt"><tr><td class="ptds"><a href="#">${sheet + 1}</a></td>${links.join()}</tr></table>
<div id="gdt">${tiles.join()}</div>
${[for (var i = 0; i < comments; i++) _comment(i, uploader: i == 0)].join('\n')}
''';
}

/// Serves gallery sheets, failing the first [failures] requests with a 500.
class EhFixtureServer {
  final int sheetCount;
  final int comments;
  final bool thumbs;
  var failures = 0;

  /// While set, responses wait for it.
  Completer<void>? gate;
  final requests = <Uri>[];

  EhFixtureServer({this.sheetCount = 3, this.comments = 4, this.thumbs = false});

  List<int> get sheetsRequested => [
    for (final uri in requests)
      if (uri.path.startsWith('/g/')) int.tryParse(uri.queryParameters['p'] ?? '') ?? 0,
  ];

  Future<http.Response> handle(http.Request request) async {
    requests.add(request.url);
    await gate?.future;
    if (failures > 0) {
      failures--;
      return http.Response('busy', 500);
    }
    if (!request.url.path.startsWith('/g/')) return http.Response('<table></table>', 200);
    final sheet = int.tryParse(request.url.queryParameters['p'] ?? '') ?? 0;
    final html = ehGalleryHtml(sheet: sheet, sheetCount: sheetCount, comments: comments, thumbs: thumbs);
    return http.Response.bytes(utf8.encode(html), 200, headers: {'content-type': 'text/html; charset=utf-8'});
  }

  EhClient client({Map<String, Object> prefs = const {}}) => EhClient(
    PrefServiceCache(cache: {optionPluginEhCookies: '', optionPluginEhUseExhentai: false, ...prefs}),
    httpClient: MockClient(handle),
  );
}
