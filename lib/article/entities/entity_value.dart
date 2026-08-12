import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:xta/tweet/_media.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/ui/capped_network_image.dart';

part 'markdown_entity.dart';
part 'image_entity.dart';
part 'video_entity.dart';
part 'link_entity.dart';
part 'divider_entity.dart';

sealed class EntityValue {
  const EntityValue();

  Widget toWidget(BuildContext context);
}
