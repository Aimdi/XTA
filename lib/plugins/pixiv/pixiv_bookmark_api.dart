import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/utils/json.dart';

/// The tag Pixiv files a bookmark under when it carries none of the reader's tags.
const pixivUnclassifiedTag = '未分類';

/// One of the reader's bookmark tags and how many works carry it.
typedef PixivBookmarkTag = ({String name, int count});

/// A tag as the bookmark editor lists it: checked when the bookmark carries it.
typedef PixivBookmarkTagChoice = ({String name, bool checked});

/// A work's bookmark as `/v2/illust/bookmark/detail` describes it. Its tags are
/// the work's own plus any the reader added, each marked when it is filed.
class PixivBookmarkDetail {
  final bool isBookmarked;

  /// `public` or `private`.
  final String restrict;
  final List<PixivBookmarkTagChoice> tags;

  const PixivBookmarkDetail({required this.isBookmarked, required this.restrict, this.tags = const []});

  factory PixivBookmarkDetail.fromJson(Object? json) {
    final detail = Json(json)['bookmark_detail'];
    return PixivBookmarkDetail(
      isBookmarked: detail['is_bookmarked'].boolean == true,
      restrict: detail['restrict'].string == 'private' ? 'private' : 'public',
      tags: [
        for (final tag in detail['tags'].list)
          if ((tag['name'].string ?? '').trim() case final name when name.isNotEmpty)
            (name: name, checked: tag['is_registered'].boolean == true),
      ],
    );
  }
}

/// One page of `/v1/user/bookmark-tags/illust`.
PixivPage<PixivBookmarkTag> parsePixivBookmarkTags(Object? json) {
  final root = Json(json);
  return PixivPage([
    for (final tag in root['bookmark_tags'].list)
      if ((tag['name'].string ?? '').trim() case final name when name.isNotEmpty)
        (name: name, count: tag['count'].integer ?? 0),
  ], nextUrl: root['next_url'].string);
}

/// Up to [limit] of [tags] whose name contains [query], ignoring case: the
/// suggestions under a tag field.
List<PixivBookmarkTag> pixivBookmarkTagMatches(Iterable<PixivBookmarkTag> tags, String query, {int limit = 8}) {
  final needle = query.trim().toLowerCase();
  if (needle.isEmpty) return const [];
  return tags.where((tag) => tag.name.toLowerCase().contains(needle)).take(limit).toList();
}

/// Tag names from what the reader typed. Pixiv tags hold no spaces, and its
/// form splits on them, so `blue sky` is the two tags it would become anyway.
List<String> pixivTagsFromInput(String input) => input.split(RegExp(r'\s+')).where((tag) => tag.isNotEmpty).toList();

/// The bookmark tags of one work and the reader's tag lists, and the only
/// place a bookmark is written to Pixiv.
class PixivBookmarkApi {
  final PixivClient client;

  const PixivBookmarkApi(this.client);

  /// A test's fake when one is provided, else the app's client.
  static PixivBookmarkApi of(BuildContext context) =>
      context.read<PixivBookmarkApi?>() ?? PixivBookmarkApi(context.read<PixivClient>());

  Future<PixivBookmarkDetail> detail(int illustId) async => PixivBookmarkDetail.fromJson(
    await client.getJson('/v2/illust/bookmark/detail', query: {'illust_id': '$illustId'}),
  );

  /// Adds [illustId], or re-files a bookmark it already has. Pixiv's form
  /// takes the whole tag list space-joined in a single `tags[]` field.
  Future<void> add(int illustId, {required String restrict, List<String> tags = const []}) async {
    final joined = {for (final tag in tags) ...pixivTagsFromInput(tag)}.join(' ');
    await client.postForm('/v2/illust/bookmark/add', {
      'illust_id': '$illustId',
      'restrict': restrict,
      if (joined.isNotEmpty) 'tags[]': joined,
    });
  }

  Future<void> delete(int illustId) async {
    await client.postForm('/v1/illust/bookmark/delete', {'illust_id': '$illustId'});
  }

  /// One page of the reader's [restrict] bookmark tags with their counts.
  Future<PixivPage<PixivBookmarkTag>> tags({required String restrict, String? nextUrl}) async {
    if (nextUrl != null) return parsePixivBookmarkTags(await client.getNextJson(nextUrl));
    final userId = await client.ensureUserId();
    final json = await client.getJson(
      '/v1/user/bookmark-tags/illust',
      query: {'user_id': '$userId', 'restrict': restrict},
    );
    return parsePixivBookmarkTags(json);
  }
}
