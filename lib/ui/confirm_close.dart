import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';

/// System Back on the root screen: leave at once, or ask first when the
/// reader kept the confirmation on. Home and the intro share this.
Future<void> confirmCloseApp(BuildContext context) async {
  final prefs = PrefService.of(context, listen: false);
  if (prefs.get<bool>(optionConfirmClose) == false) {
    SystemNavigator.pop();
    return;
  }

  final confirmed = await showDialog<bool>(
    context: context,
    builder: (c) => AlertDialog(
      title: Text(L10n.current.are_you_sure),
      content: Text(L10n.current.confirm_close_fritter),
      actions: [
        TextButton(child: Text(L10n.current.no), onPressed: () => Navigator.pop(c, false)),
        TextButton(child: Text(L10n.current.yes), onPressed: () => Navigator.pop(c, true)),
      ],
    ),
  );

  if (confirmed == true && context.mounted) {
    SystemNavigator.pop();
  }
}
