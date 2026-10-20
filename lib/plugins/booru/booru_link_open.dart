import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_links.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_screen.dart';

/// Opens the post [ref] names in the Booru viewer. False leaves the link to
/// the browser: the post did not load, the site has no such post, or it is
/// over the rating cap.
Future<bool> openBooruPostRef(BuildContext context, BooruPostRef ref) async {
  final BooruPost? post;
  try {
    post = await context.read<BooruClient>().postAt(ref);
  } catch (_) {
    return false;
  }
  if (!context.mounted) return true;
  if (post case final found?) {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => BooruPostScreen(post: found)));
    return true;
  }
  return false;
}
