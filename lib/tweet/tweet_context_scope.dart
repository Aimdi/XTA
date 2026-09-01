import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/tweet/_video.dart';

/// Provides the [TweetContextState] and [VideoContextState] that tweet tiles
/// read from context. Any subtree rendering [TweetConversation]/[TweetTile] must
/// sit under this scope, otherwise those reads throw ProviderNotFoundException.
class TweetContextScope extends StatelessWidget {
  final Widget child;

  const TweetContextScope({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    var prefs = PrefService.of(context, listen: false);
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<TweetContextState>(
            create: (_) => TweetContextState.fromPrefs(prefs)),
        ChangeNotifierProvider<VideoContextState>(
            create: (_) => VideoContextState(prefs.get(optionMediaDefaultMute))),
      ],
      child: child,
    );
  }
}
