import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// Compatibility wrapper — the Pixez-style UI uses [PixivIllustTile] in grids.
class PixivIllustCard extends StatelessWidget {
  final PixivIllust illust;

  const PixivIllustCard({super.key, required this.illust});

  @override
  Widget build(BuildContext context) => PixivIllustTile(illust: illust);
}
