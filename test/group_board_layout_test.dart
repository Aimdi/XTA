import 'package:flutter_test/flutter_test.dart';
import 'package:xta/subscriptions/group_board_layout.dart';

void main() {
  group('groupBoardColumns', () {
    test('a phone keeps the reader\'s two or three columns', () {
      expect(groupBoardColumns(chosen: 2, width: 390), 2);
      expect(groupBoardColumns(chosen: 3, width: 390), 3);
    });

    test('a value outside two or three falls back into range', () {
      expect(groupBoardColumns(chosen: 1, width: 390), 2);
      expect(groupBoardColumns(chosen: 7, width: 390), 3);
    });

    test('a wide window adds columns so no tile passes the maximum width', () {
      for (final width in [800.0, 1280.0, 1868.0, 2536.0]) {
        final columns = groupBoardColumns(chosen: 2, width: width);
        expect(width / columns, lessThanOrEqualTo(kGroupTileMaxWidth), reason: 'width $width');
      }
      expect(groupBoardColumns(chosen: 2, width: 1868), 6);
    });
  });

  group('groupTileExtent', () {
    test('a phone keeps the aspect ratio', () {
      expect(groupTileExtent(chosen: 2, columns: 2, textScale: 1), isNull);
      expect(groupTileExtent(chosen: 3, columns: 3, textScale: 1), isNull);
    });

    test('a widened board gets content-height tiles that grow with large text', () {
      final normal = groupTileExtent(chosen: 2, columns: 6, textScale: 1)!;
      expect(normal, kGroupTileWideHeight);
      expect(groupTileExtent(chosen: 2, columns: 6, textScale: 2)!, greaterThan(normal));
    });
  });

  test('two columns are landscape tiles, more are square', () {
    expect(groupTileBaseRatio(2), greaterThan(1));
    expect(groupTileBaseRatio(3), 1);
    expect(groupTileBaseRatio(6), 1);
  });
}
