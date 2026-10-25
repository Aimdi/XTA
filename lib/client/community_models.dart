/// X communities as their GraphQL responses describe them: a community's
/// header, and the pages of its members and of community search.
library;

import 'package:xta/client/tweet_models.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/json.dart';

class Community {
  final String id;
  final String name;
  final String? description;
  final int memberCount;
  final String? bannerUrl;
  final String? topic;
  final DateTime? createdAt;
  final List<String> rules;

  /// The few member faces X shows beside the member count.
  final List<String> memberAvatarUrls;

  const Community({
    required this.id,
    required this.name,
    this.description,
    this.memberCount = 0,
    this.bannerUrl,
    this.topic,
    this.createdAt,
    this.rules = const [],
    this.memberAvatarUrls = const [],
  });

  /// A `Community` result, wherever X nests one; null for anything else.
  static Community? fromResult(Json result) {
    final id = result['rest_id'].string ?? result['id_str'].string;
    if (result['__typename'].string != 'Community' || id == null) return null;
    final createdAt = result['created_at'].integer;
    return Community(
      id: id,
      name: result['name'].string ?? '',
      description: result['description'].string,
      memberCount: result['member_count'].integer ?? 0,
      bannerUrl:
          result['custom_banner_media']['media_info']['original_img_url'].string ??
          result['default_banner_media']['media_info']['original_img_url'].string,
      topic: result['primary_community_topic']['topic_name'].string,
      createdAt: createdAt == null ? null : DateTime.fromMillisecondsSinceEpoch(createdAt),
      rules: [for (final rule in result['rules'].list) ?rule['name'].string],
      memberAvatarUrls: [
        for (final member in result['members_facepile_results'].list) ?member['result']['avatar']['image_url'].string,
      ],
    );
  }

  /// A `CommunityQuery` body; null when X sent no community.
  static Community? fromQuery(Json body) => fromResult(body['data']['communityResults']['result']);
}

typedef CommunitySearchPage = ({List<Community> communities, String? cursor});

/// One page of `CommunitiesSearchQuery`.
CommunitySearchPage parseCommunitySearch(Json body) {
  final slice = body['data']['communities_search_slice'];
  return (
    communities: [for (final item in slice['items_results'].list) ?Community.fromResult(item['result'])],
    cursor: slice['slice_info']['next_cursor'].string,
  );
}

/// One page of `membersSliceTimeline_Query`. A member without a handle could
/// not be opened, so it is left out.
Follows parseCommunityMembers(Json body) {
  final slice = body['data']['communityResults']['result']['members_slice'];
  return Follows(
    cursorBottom: slice['slice_info']['next_cursor'].string,
    cursorTop: null,
    users: [
      for (final item in slice['items_results'].list)
        if (item['result'].raw case final Map<String, dynamic> user
            when Json(user)['rest_id'].exists && Json(user)['core']['screen_name'].exists)
          UserWithExtra.fromNonLegacyJson(user),
    ],
  );
}

/// The timeline under a `CommunityTweetsTimeline` body, in the shape
/// [TimelineParser] reads; null when X sent none.
Map<String, dynamic>? communityTimelineOf(Json body) {
  final timeline = body['data']['communityResults']['result']['ranked_community_timeline'].raw;
  return timeline is Map<String, dynamic> && Json(timeline)['timeline']['instructions'].raw is List ? timeline : null;
}
