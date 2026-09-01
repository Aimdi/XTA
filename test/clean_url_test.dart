import 'package:flutter_test/flutter_test.dart';
import 'package:xta/utils/urls.dart';

void main() {
  test('cleanUrl strips tracking parameters', () {
    expect(cleanUrl('https://x.com/a/status/1?s=20&t=xyz'), 'https://x.com/a/status/1');
    expect(cleanUrl('https://example.com/p?utm_source=tw&utm_medium=social&id=7'), 'https://example.com/p?id=7');
    expect(cleanUrl('https://example.com/p?s=size-m'), 'https://example.com/p?s=size-m');
    expect(cleanUrl('https://example.com/plain'), 'https://example.com/plain');
    expect(cleanUrl('https://x.com/search?q=hello&s=1'), 'https://x.com/search?q=hello');
    expect(cleanUrl('https://shop.com/x?fbclid=abc#frag'), 'https://shop.com/x#frag');
    expect(cleanUrl('not a url at all'), 'not a url at all');
  });

  test('cleanUrl strips the rest of the common click-ids too', () {
    expect(
      cleanUrl('https://shop.com/x?msclkid=1&gbraid=2&keep=yes'),
      'https://shop.com/x?keep=yes',
    );
    expect(
      cleanUrl('https://shop.com/x?ttclid=tik&igsh=inst&ocid=ms'),
      'https://shop.com/x',
    );
    expect(
      cleanUrl('https://news.example/a?mtm_campaign=x&pk_source=y&hsa_cam=1&id=9'),
      'https://news.example/a?id=9',
    );
    expect(
      cleanUrl('https://shop.com/x?_hsenc=a&li_fat_id=b&srsltid=c'),
      'https://shop.com/x',
    );
  });
}
