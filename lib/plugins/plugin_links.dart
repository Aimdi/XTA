import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_story_screen.dart';
import 'package:xta/plugins/hackernews/hn_user_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_screen.dart';
import 'package:xta/plugins/instagram/instagram_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/plugins/plugin_url.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/reddit/reddit_thread_screen.dart';
import 'package:xta/plugins/substack/substack_links.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_reader_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/threads/threads_thread_screen.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_player_screen.dart';
import 'package:xta/plugins/tiktok/tiktok_profile_screen.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/status.dart';
import 'package:xta/utils/urls.dart';

/// Opens [url] in a plugin screen when one can read it, otherwise the browser.
Future<void> openLink(BuildContext context, String url) async {
  if (await openWithPlugins(context, url) || !context.mounted) {
    return;
  }
  if (await _openX(context, url) || !context.mounted) {
    return;
  }
  await openUri(context, url);
}

const _xHosts = {
  'x.com',
  'www.x.com',
  'twitter.com',
  'www.twitter.com',
  'mobile.twitter.com',
};

/// An x.com / twitter.com status or profile, opened on the native screens
/// rather than handed to the browser.
Future<bool> _openX(BuildContext context, String url) async {
  final uri = Uri.tryParse(url);
  if (uri == null || !_xHosts.contains(uri.host.toLowerCase())) {
    return false;
  }
  final parsed = await parseUri(uri);
  if (!context.mounted) {
    return true;
  }
  switch (parsed) {
    case PostUriInfo(screenName: final screenName, id: final id):
      await Navigator.pushNamed(
        context,
        routeStatus,
        arguments: StatusScreenArguments(id: id, username: screenName),
      );
      return true;
    case ProfileUriInfo(
      screenName: final screenName,
      profileTabIndex: final tab,
    ):
      await Navigator.pushNamed(
        context,
        routeProfile,
        arguments: ProfileScreenArguments.fromScreenName(screenName, tab),
      );
      return true;
    default:
      return false;
  }
}

/// Opens [url] inside XTA when an enabled plugin can read it, returning true
/// when it handled the link. Callers fall back to the browser on false.
Future<bool> openWithPlugins(BuildContext context, String url) async {
  if (await _openSubstack(context, url)) {
    return true;
  }
  if (!context.mounted) {
    return false;
  }
  return _openParsed(context, url);
}

Future<bool> _openParsed(BuildContext context, String url) async {
  final prefs = _prefsOf(context);
  if (prefs == null) {
    return false;
  }
  final link = parsePluginLink(url, mastodonHosts: _mastodonHosts(prefs));
  if (link == null || !_enabledFor(prefs, link)) {
    return false;
  }
  return _pushLink(context, link);
}

Future<bool> _pushLink(BuildContext context, PluginLink link) {
  return switch (link) {
    BlueskyProfileLink() => _push(
      context,
      BlueskyProfileScreen(actor: link.actor),
    ),
    BlueskyPostLink() => _push(
      context,
      BlueskyThreadScreen(post: _blueskyStub(link)),
    ),
    ThreadsProfileLink() => _push(
      context,
      ThreadsProfileScreen(username: link.handle),
    ),
    ThreadsPostLink() => _push(
      context,
      ThreadsThreadScreen(post: _threadsStub(link)),
    ),
    InstagramProfileLink() => _push(
      context,
      InstagramProfileScreen(handle: link.handle),
    ),
    TikTokProfileLink() => _push(
      context,
      TikTokProfileScreen(handle: link.handle),
    ),
    TikTokVideoLink() => _openTikTokVideo(context, link),
    RedditSubredditLink() => _push(
      context,
      RedditListingScreen.subreddit(link.name),
    ),
    RedditUserLink() => _push(context, RedditListingScreen.user(link.name)),
    RedditThreadLink() => _push(
      context,
      RedditThreadScreen(post: _redditStub(link)),
    ),
    MastodonProfileLink() => _push(
      context,
      MastodonProfileScreen(acct: link.acct),
    ),
    MastodonStatusLink() => _push(
      context,
      MastodonThreadScreen(post: _mastodonStub(link)),
    ),
    PixivWebLink() => _openPixiv(context, link.ref),
    HnStoryLink() => _push(
      context,
      HnStoryScreen(
        story: HnStory(id: link.id, title: ''),
      ),
    ),
    HnUserLink() => _push(context, HnUserScreen(userId: link.id)),
  };
}

Future<bool> _push(BuildContext context, Widget screen) async {
  await Navigator.push(context, MaterialPageRoute(builder: (_) => screen));
  return true;
}

Future<bool> _openPixiv(BuildContext context, PixivLinkRef ref) async {
  if (ref case PixivUserLinkRef(:final id)) {
    return _push(context, PixivUserScreen(userId: id));
  }
  try {
    final client = context.read<PixivClient>();
    final illust = await client.illustDetail(ref.id);
    if (!context.mounted) {
      return true;
    }
    return _push(context, PixivIllustScreen(illust: illust));
  } catch (_) {
    return false;
  }
}

Future<bool> _openTikTokVideo(
  BuildContext context,
  TikTokVideoLink link,
) async {
  try {
    final client = context.read<TikTokClient>();
    final post = await client.video(link.id, handle: link.handle);
    if (!context.mounted) {
      return true;
    }
    return _push(context, TikTokPlayerScreen(post: post));
  } catch (_) {
    return false;
  }
}

Future<bool> _openSubstack(BuildContext context, String url) async {
  final link = substackLinkFor(context, url);
  if (link == null) {
    return false;
  }

  final known = _knownPublications(context);
  final match = known
      .where((p) => Uri.tryParse(p.baseUrl)?.host == link.publicationBase.host)
      .firstOrNull;

  await Navigator.push(
    context,
    MaterialPageRoute(
      builder: (_) => SubstackReaderScreen(
        post: substackPostStub(link, publicationName: match?.name),
      ),
    ),
  );
  return true;
}

/// The Substack post [url] points at, or null when the plugin is off or the
/// link is not a readable post.
SubstackPostLink? substackLinkFor(BuildContext context, String url) {
  final prefs = _prefsOf(context);
  if (prefs == null || prefs.get(optionPluginSubstackEnabled) != true) {
    return null;
  }

  return parseSubstackPostLink(
    url,
    knownBaseUrls: _knownPublications(context).map((e) => e.baseUrl),
  );
}

List<SubstackPublication> _knownPublications(BuildContext context) {
  try {
    return context.read<SubstackPublicationsStore>().state;
  } catch (_) {
    return const [];
  }
}

BasePrefService? _prefsOf(BuildContext context) {
  try {
    return PrefService.of(context, listen: false);
  } catch (_) {
    return null;
  }
}

Set<String> _mastodonHosts(BasePrefService prefs) => mastodonLinkHosts([
  ...mastodonConfiguredInstances(prefs),
  ...kMastodonDefaultInstances,
]);

bool _enabledFor(BasePrefService prefs, PluginLink link) {
  final key = switch (link) {
    BlueskyProfileLink() || BlueskyPostLink() => optionPluginBlueskyEnabled,
    ThreadsProfileLink() || ThreadsPostLink() => optionPluginThreadsEnabled,
    InstagramProfileLink() => optionPluginInstagramEnabled,
    TikTokProfileLink() || TikTokVideoLink() => optionPluginTiktokEnabled,
    RedditSubredditLink() ||
    RedditUserLink() ||
    RedditThreadLink() => optionPluginRedditEnabled,
    MastodonProfileLink() ||
    MastodonStatusLink() => optionPluginMastodonEnabled,
    PixivWebLink() => optionPluginPixivEnabled,
    HnStoryLink() || HnUserLink() => optionPluginHnEnabled,
  };
  return prefs.get(key) == true;
}

BlueskyPost _blueskyStub(BlueskyPostLink link) {
  final did = link.actor.startsWith('did:') ? link.actor : '';
  return BlueskyPost(
    uri: link.atUri,
    cid: '',
    handle: did.isEmpty ? link.actor : '',
    did: did,
    authorName: link.actor,
    text: '',
    url: 'https://bsky.app/profile/${link.actor}/post/${link.rkey}',
  );
}

ThreadsPost _threadsStub(ThreadsPostLink link) => ThreadsPost(
  id: link.url,
  handle: link.handle,
  authorName: link.handle,
  text: '',
  url: link.url,
);

RedditPost _redditStub(RedditThreadLink link) => RedditPost(
  id: link.id,
  title: link.title ?? link.id,
  subreddit: link.subreddit,
  permalink: link.permalink,
);

MastodonPost _mastodonStub(MastodonStatusLink link) => MastodonPost(
  id: link.statusId,
  acct: link.acct,
  authorName: link.acct.split('@').first,
  text: '',
  url: link.url,
);
