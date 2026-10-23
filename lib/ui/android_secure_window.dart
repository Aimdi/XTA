import 'package:flutter/widgets.dart';
import 'package:secure_content/secure_content.dart';
import 'package:xta/utils/desktop.dart';

/// Android's FLAG_SECURE around the app.
///
/// A desktop window cannot refuse a screenshot, and [SecureWidget] calls the
/// Android channel when it is disposed even while off, which throws on the
/// desktop. There the app is built without it.
class AndroidSecureWindow extends StatelessWidget {
  final bool isSecure;
  final WidgetBuilder builder;

  const AndroidSecureWindow({super.key, required this.isSecure, required this.builder});

  @override
  Widget build(BuildContext context) =>
      isDesktop ? builder(context) : SecureWidget(isSecure: isSecure, builder: (context, _, _) => builder(context));
}
