import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// Asks [question] with Cancel beside [action]; true only once [action] is chosen.
Future<bool> confirmPixivAction(BuildContext context, String question, String action) async {
  final answer = await showDialog<bool>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(question),
      actions: [
        TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: Text(L10n.of(dialogContext).cancel)),
        FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: Text(action)),
      ],
    ),
  );
  return answer == true;
}
