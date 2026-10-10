import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';

/// The box of the widget at [context] in screen coordinates, which a share
/// sheet on a tablet or a Mac points from; null before it is laid out.
Rect? pixivShareOrigin(BuildContext context) {
  final box = context.findRenderObject();
  return box is RenderBox && box.attached && box.hasSize ? box.localToGlobal(Offset.zero) & box.size : null;
}

/// Shares a Pixiv address, the sheet pointing at [origin].
Future<void> sharePixivLink(String url, {Rect? origin}) =>
    SharePlus.instance.share(ShareParams(text: url, sharePositionOrigin: origin));

/// An icon button sharing [url], its sheet anchored to the button.
class PixivShareLinkButton extends StatelessWidget {
  final String url;

  /// Says what is shared; "Share link" when absent.
  final String? tooltip;

  const PixivShareLinkButton({super.key, required this.url, this.tooltip});

  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: tooltip ?? L10n.of(context).share_link,
    icon: const Icon(Icons.share_outlined),
    onPressed: () => sharePixivLink(url, origin: pixivShareOrigin(context)),
  );
}
