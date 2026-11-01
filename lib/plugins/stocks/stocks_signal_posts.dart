import 'package:xta/client/client.dart';
import 'package:xta/tweet/cashtag_quotes.dart';

/// Where trading-signal groups invite readers to.
const Set<String> _telegramHosts = {'t.me', 'telegram.me', 'telegram.dog'};

bool _isTelegram(String? url) {
  final host = Uri.tryParse(url ?? '')?.host.toLowerCase().replaceFirst(RegExp(r'^www\.'), '');
  return host != null && _telegramHosts.contains(host);
}

/// The shape of a trading-signal group's post: an invite to a Telegram
/// channel and three or more cashtags. Such posts lead a cashtag feed by
/// volume, not by what they say about any one of them.
bool isSignalGroupPost(TweetWithCard tweet) {
  final urls = tweet.noteEntities?.urls ?? tweet.entities?.urls ?? const [];
  return urls.any((url) => _isTelegram(url.expandedUrl)) && tweetCashtags(tweet).length >= 3;
}

/// [chains] with the signal-group posts moved to the end, each group in its
/// order. Nothing is left out.
List<TweetChain> signalGroupPostsLast(List<TweetChain> chains) {
  bool signal(TweetChain chain) => chain.tweets.any(isSignalGroupPost);
  return [...chains.where((chain) => !signal(chain)), ...chains.where(signal)];
}
