import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';

/// The reader's own follows, public or private, where Home's people icon leads.
class PixivFollowingScreen extends StatelessWidget {
  const PixivFollowingScreen({super.key});

  @override
  Widget build(BuildContext context) => const PixivUserListScreen(kind: PixivUserListKind.following);
}
