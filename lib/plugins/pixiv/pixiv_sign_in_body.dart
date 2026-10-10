import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// What every section but More shows before a Pixiv account is connected.
class PixivSignInBody extends StatelessWidget {
  final bool signingIn;
  final VoidCallback onSignIn;

  const PixivSignInBody({super.key, required this.signingIn, required this.onSignIn});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(l10n.plugin_pixiv_not_configured, textAlign: TextAlign.center),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: signingIn ? null : onSignIn,
              child: signingIn
                  ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : Text(l10n.plugin_pixiv_sign_in),
            ),
          ],
        ),
      ),
    );
  }
}
