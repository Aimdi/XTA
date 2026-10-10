import 'package:flutter/material.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_previews.dart';
import 'package:xta/tweet/tweet_skeleton.dart';

const _ehSkeletonChipWidths = [72.0, 96.0, 64.0, 110.0, 84.0, 58.0, 90.0];

/// The gallery page below its header while the detail loads: bones where the
/// facts, the Read button, the tags and the first previews will be.
class EhGallerySkeleton extends StatefulWidget {
  final int previewCount;

  const EhGallerySkeleton({super.key, this.previewCount = 8});

  @override
  State<EhGallerySkeleton> createState() => _EhGallerySkeletonState();
}

class _EhGallerySkeletonState extends State<EhGallerySkeleton> with SingleTickerProviderStateMixin {
  late final AnimationController _pulse = skeletonPulseController(this);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    applySkeletonPulse(context, _pulse);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ExcludeSemantics(
      child: RepaintBoundary(
        child: AnimatedBuilder(
          animation: _pulse,
          builder: (context, _) => _bones(context, skeletonBoneColor(context, _pulse.value)),
        ),
      ),
    );
  }

  Widget _bones(BuildContext context, Color color) {
    return Padding(
      key: const ValueKey('eh-gallery-skeleton'),
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SkeletonBone(width: 240, height: 14, color: color),
          const SizedBox(height: 16),
          SkeletonBone(width: double.infinity, height: 48, radius: 24, color: color),
          const SizedBox(height: 24),
          Wrap(
            spacing: 6,
            runSpacing: 10,
            children: [
              for (final width in _ehSkeletonChipWidths)
                SkeletonBone(width: width, height: 30, radius: 10, color: color),
            ],
          ),
          const SizedBox(height: 24),
          _previewBones(color),
        ],
      ),
    );
  }

  Widget _previewBones(Color color) {
    return LayoutBuilder(
      builder: (context, constraints) => GridView.builder(
        shrinkWrap: true,
        primary: false,
        physics: const NeverScrollableScrollPhysics(),
        padding: EdgeInsets.zero,
        gridDelegate: ehPreviewGridDelegate(constraints.maxWidth),
        itemCount: widget.previewCount,
        itemBuilder: (context, index) =>
            SkeletonBone(width: double.infinity, height: double.infinity, radius: 6, color: color),
      ),
    );
  }
}
