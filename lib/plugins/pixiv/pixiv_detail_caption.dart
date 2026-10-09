import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// The creator's caption under a work, as plain text.
class PixivDetailCaption extends StatelessWidget {
  final PixivIllust illust;

  const PixivDetailCaption({super.key, required this.illust});

  @override
  Widget build(BuildContext context) =>
      Text(illust.caption, style: Theme.of(context).textTheme.bodyMedium!.copyWith(height: 1.35));
}
