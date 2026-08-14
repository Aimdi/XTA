import 'package:flutter_test/flutter_test.dart';
import 'package:xta/subscriptions/import_handles.dart';

void main() {
  test('reads @handles, bare names and profile links', () {
    expect(
      parseImportHandles(
        '@nasa\nSpaceX https://x.com/elonmusk twitter.com/jack',
      ),
      ['nasa', 'SpaceX', 'elonmusk', 'jack'],
    );
  });

  test('dedupes by lowercase and skips junk', () {
    expect(parseImportHandles('@NASA nasa !!! too-long-handle-name'), ['NASA']);
    expect(parseImportHandles(''), isEmpty);
    expect(parseImportHandles('https://example.com/nasa'), isEmpty);
  });

  test('accepts a trailing slash on a profile url', () {
    expect(importHandleFromToken('https://x.com/i/status/1'), isNull);
    expect(importHandleFromToken('https://x.com/grok/'), 'grok');
  });
}
