import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/feed_cache.dart';

Map<String, Object?> row(Object? createdAt) => {'created_at': createdAt};

void main() {
  group('parseChunkTimestamp', () {
    test('reads SQLite CURRENT_TIMESTAMP as the UTC it is', () {
      final parsed = parseChunkTimestamp('2026-08-04 12:00:00');

      expect(parsed, isNotNull);
      // Read as local time this would be wrong by the device's offset, which is
      // exactly how a cache written a minute ago ends up looking hours old.
      expect(parsed!.toUtc(), DateTime.utc(2026, 8, 4, 12));
    });

    test('leaves an explicit zone alone', () {
      expect(parseChunkTimestamp('2026-08-04T12:00:00Z')!.toUtc(), DateTime.utc(2026, 8, 4, 12));
      expect(parseChunkTimestamp('2026-08-04 12:00:00Z')!.toUtc(), DateTime.utc(2026, 8, 4, 12));
    });

    test('returns null for anything it cannot read', () {
      expect(parseChunkTimestamp(null), isNull);
      expect(parseChunkTimestamp(''), isNull);
      expect(parseChunkTimestamp('   '), isNull);
      expect(parseChunkTimestamp('not a date'), isNull);
      expect(parseChunkTimestamp(1754308800), isNull);
    });
  });

  group('newestChunkTimestamp', () {
    test('picks the most recent row', () {
      final rows = [row('2026-08-04 09:00:00'), row('2026-08-04 14:05:00'), row('2026-08-03 23:00:00')];

      expect(newestChunkTimestamp(rows)!.toUtc(), DateTime.utc(2026, 8, 4, 14, 5));
    });

    test('ignores rows it cannot read', () {
      final rows = [row(null), row('2026-08-04 09:00:00')];

      expect(newestChunkTimestamp(rows)!.toUtc(), DateTime.utc(2026, 8, 4, 9));
    });

    test('is null when nothing is readable', () {
      expect(newestChunkTimestamp(const []), isNull);
      expect(newestChunkTimestamp([row('nope')]), isNull);
    });
  });
}
