/// What a backup preview counts, and the order it lists them in.
///
/// Its own file so a plugin can name the category its rows belong to without
/// importing the backup document — which reads the plugin registry, and would
/// otherwise import it back.
library;

enum BackupCategory {
  settings,
  subscriptions,
  substack,
  subreddits,
  stocks,
  threads,
  bluesky,
  mastodon,
  booruTags,
  ehFavorites,
  ehHistory,
  tiktokSubscriptions,
  groups,
  groupMembers,
  savedPosts,
  folders,
  likedPosts,
  filters,
  readPositions,
  upvotes,
  threadsLikes,
  blueskyLikes,
  accounts,
  profileNotes,
  antennas,
}
