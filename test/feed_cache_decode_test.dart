import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/feed_cache.dart';

void main() {
  test('async chunk decode matches the sync path', () async {
    final row = {
      'response': jsonEncode([
        {'id': 'c1', 'isPinned': false, 'tweets': <Map<String, dynamic>>[]},
        {'id': 'c2', 'isPinned': true, 'tweets': <Map<String, dynamic>>[]},
      ]),
    };

    final sync = chainsFromStoredChunks([row]);
    final decoded = await chainsFromStoredChunksAsync([row]);

    expect(decoded.map((c) => c.id), sync.map((c) => c.id));
    expect(decoded.map((c) => c.isPinned), sync.map((c) => c.isPinned));
  });

  test('an empty store is nothing to decode', () async {
    expect(await chainsFromStoredChunksAsync(const []), isEmpty);
  });

  test('a corrupt or null chunk is skipped instead of aborting', () async {
    final rows = [
      {'response': '{not-json'},
      {'response': null},
      {
        'response': jsonEncode([
          {'id': 'ok', 'isPinned': false, 'tweets': <Map<String, dynamic>>[]},
        ]),
      },
    ];

    final decoded = await chainsFromStoredChunksAsync(rows);
    expect(decoded.map((c) => c.id), ['ok']);
  });

  test('chunk encode matches the former UI-isolate jsonEncode', () async {
    final chains = [
      {'id': 'c1', 'isPinned': false, 'tweets': <Map<String, dynamic>>[]},
      {'id': 'c2', 'isPinned': true, 'tweets': <Map<String, dynamic>>[]},
    ];
    expect(await encodeChunkBlob(chains), jsonEncode(chains));
    expect(await encodeChunkBlob(const []), '[]');
  });
}
