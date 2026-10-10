/// Which posts a shared timeline takes from its plugin members.
///
/// A group's "include replies" and "include reposts" settings reached X alone:
/// its search query carried `-filter:replies` / `-filter:retweets`, while every
/// other network was asked for its members' posts whole and mixed them in as
/// they came — so a group with replies off still filled with Bluesky replies.
library;

/// A group's reply and repost settings, as a plugin source is handed them.
typedef FeedPostKinds = ({bool replies, bool reposts});

/// Everything: what a feed with no such settings takes.
const FeedPostKinds allFeedPostKinds = (replies: true, reposts: true);

/// Whether a post belongs in a feed taking [kinds].
///
/// A repost follows the repost setting whatever it shares, as Bluesky's own
/// `posts_no_replies` filter keeps reposts of replies. Every other reply counts,
/// a continuation of the author's own thread included: the X side drops any
/// post that answers another (`inReplyToStatusIdStr`), its own threads too.
bool feedTakesPost(FeedPostKinds kinds, {required bool isReply, required bool isRepost}) =>
    isRepost ? kinds.reposts : kinds.replies || !isReply;

/// The name [source]'s posts are cached under for a feed taking [kinds]: a page
/// with replies or reposts hidden is a different page. Unchanged for a feed
/// taking everything, and for replies alone hidden, so existing caches still read.
String pluginFeedCacheSource(String source, FeedPostKinds kinds) =>
    [source, if (!kinds.replies) 'no-replies', if (!kinds.reposts) 'no-reposts'].join(':');
