import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';

void main() {
  group('PixivMuteState', () {
    test('filters muted authors, tags and illust ids', () {
      const state = PixivMuteState(
        authorIds: {10},
        tags: {'cat'},
        illustIds: {3},
      );

      final visible = state.filter([
        _illust(id: 1, userId: 10),
        _illust(id: 2, tags: const [PixivTag(name: 'Cat')]),
        _illust(id: 3),
        _illust(id: 4, tags: const [PixivTag(name: 'dog')]),
      ]);

      expect(visible.map((illust) => illust.id), [4]);
    });
  });
}

PixivIllust _illust({
  required int id,
  int userId = 1,
  List<PixivTag> tags = const [],
}) {
  return PixivIllust(
    id: id,
    title: '',
    caption: '',
    type: 'illust',
    thumbnailUrl: 'https://i.pximg.net/$id.jpg',
    pageCount: 1,
    userId: userId,
    userName: '',
    userAccount: '',
    tags: tags,
  );
}
