import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/threads/threads_models.dart';

/// What a reader can see of each source's post, as the shared filters match it: words, author, and links.
String _joined(Iterable<String?> parts) => parts.whereType<String>().where((part) => part.isNotEmpty).join('\n');

String mastodonFilterText(MastodonPost post) => _joined([
  post.authorName,
  post.acct,
  post.spoilerText,
  post.text,
  post.quote?.text,
  post.linkCard?.title,
  post.linkCard?.url,
]);

String blueskyFilterText(BlueskyPost post) => _joined([
  post.authorName,
  post.handle,
  post.text,
  if (post.quotedPost case final quote?) blueskyFilterText(quote),
  post.linkCard?.title,
  post.linkCard?.url,
]);

String threadsFilterText(ThreadsPost post) =>
    _joined([post.authorName, post.handle, post.text, post.linkCard?.title, post.linkCard?.url]);

String redditFilterText(RedditPost post) =>
    _joined([post.title, post.selfText, post.author, post.subreddit, post.flair, post.domain, post.url]);

String rssFilterText(RssItem item) =>
    _joined([item.title, item.excerpt, item.author, item.feedTitle, item.link, ...item.categories]);

String substackFilterText(SubstackPost post) =>
    _joined([post.title, post.subtitle, post.description, post.authorName, post.publicationName, post.canonicalUrl]);

String substackNoteFilterText(SubstackNote note) => _joined([note.body, note.authorName, note.authorHandle, note.url]);

String hnFilterText(HnStory story) => _joined([story.title, story.text, story.author, story.url]);

String pixivFilterText(PixivIllust illust) => _joined([
  illust.title,
  illust.caption,
  illust.userName,
  illust.userAccount,
  for (final tag in illust.tags) ...[tag.name, tag.translatedName],
]);

String booruFilterText(BooruPost post) => _joined([...post.tags, post.source, post.host]);

String ehFilterText(EhGallery gallery) =>
    _joined([gallery.title, gallery.titleJpn, gallery.uploader, gallery.category?.label, ...gallery.tags]);
