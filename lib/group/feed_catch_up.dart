import 'package:xta/constants.dart';

/// Catch-up mode is per feed, so its preference key carries the group id.
String feedCatchUpModeKey(String groupId) => '$optionFeedCatchUpModePrefix$groupId';

/// Whether a chunk's gap-fill still has ground to cover: every post on the page
/// just fetched is newer than the newest stored one, and there is a cursor to
/// follow down towards them.
///
