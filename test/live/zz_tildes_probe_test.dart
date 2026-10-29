import 'package:flutter_test/flutter_test.dart';
import 'package:html/parser.dart' as html;
import 'package:http/http.dart' as http;

// Temporary probe: Tildes' pagination and vote markup.
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);
  test('probe tildes', () async {
    final response = await http.get(Uri.parse('https://tildes.net/?order=activity'), headers: {'User-Agent': 'XTA web sources'});
    final document = html.parse(response.body);
    // ignore: avoid_print
    print('PROBE status ${response.statusCode} length ${response.body.length}');
    for (final link in document.querySelectorAll('a')) {
      final href = link.attributes['href'] ?? '';
      if (href.contains('after=') || href.contains('before=') || link.text.toLowerCase().contains('next')) {
        // ignore: avoid_print
        print('PROBE link ${link.outerHtml.replaceAll(RegExp(r'\s+'), ' ')} parent ${link.parent?.localName}#${link.parent?.id}.${link.parent?.className}');
      }
    }
    final topic = document.querySelector('article.topic');
    // ignore: avoid_print
    print('PROBE topic ${topic?.outerHtml.replaceAll(RegExp(r'\s+'), ' ')}');
  }, skip: enabled ? false : 'live');
}
