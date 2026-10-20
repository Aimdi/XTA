import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

/// Reports its child's height after every layout, without a rebuild.
class HeightProbe extends SingleChildRenderObjectWidget {
  final ValueSetter<double> onHeight;

  const HeightProbe({super.key, required this.onHeight, super.child});

  @override
  RenderObject createRenderObject(BuildContext context) => RenderHeightProbe(onHeight);

  @override
  void updateRenderObject(BuildContext context, RenderHeightProbe renderObject) => renderObject.onHeight = onHeight;
}

class RenderHeightProbe extends RenderProxyBox {
  ValueSetter<double> onHeight;

  RenderHeightProbe(this.onHeight);

  @override
  void performLayout() {
    super.performLayout();
    onHeight(size.height);
  }
}
