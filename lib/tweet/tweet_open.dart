/// What a tap on a post, or on its author, is allowed to open.
///
/// Both used to assert their way through nullable fields — `tweet.idStr!`,
/// `tweet.user!.screenName!` — while the screens they open have always taken
/// an optional username, and the rest of the card treats those fields as the
/// optional things they are. A post whose author arrives without a handle
/// drew perfectly and then threw the instant it was tapped. Flutter catches an
/// exception thrown inside a gesture callback and carries on, so the tap did
/// nothing at all, and tapping again did nothing again — a post you could see
/// and could not open.
///
/// It shows up on a quoted post whose author X did not fill in, and on any post
/// while the author rows are hidden, since then nothing in the build touches
/// the handle and only the tap finds out.
///
/// Deciding it here rather than at the call site means the answer can be tested
/// without building a timeline.
library;

import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:xta/client/client.dart';

/// Where a tapped post leads: its id, and the author's handle when there is
/// one. Null when there is no post to open, which is the only case in which a
/// tap may rightly do nothing.
({String id, String? username})? openablePost(TweetWithCard tweet) {
  final id = tweet.idStr;
  if (id == null || id.isEmpty) {
    return null;
  }

  return (id: id, username: tweet.user?.screenName);
}

/// Where a tapped author leads, or null when the tap should be ignored.
///
/// [currentUsername] is the profile already on screen: tapping its own name
/// would push the same profile onto itself, so that tap is deliberately inert.
({String? id, String screenName})? openableProfile(User? user, {String? currentUsername}) {
  final screenName = user?.screenName;
  if (user == null || screenName == null || screenName.isEmpty) {
    return null;
  }
  if (currentUsername != null && screenName.endsWith(currentUsername)) {
    return null;
  }

  return (id: user.idStr, screenName: screenName);
}
