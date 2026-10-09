import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/login_webview.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/intro/intro_illustrations.dart';
import 'package:xta/intro/intro_page_frame.dart';
import 'package:xta/intro/intro_store.dart';
import 'package:xta/settings/_data.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Add an X account: pushes the login webview, then re-reads the accounts
/// table, since the webview pops without a result. A backup import that
/// succeeds finishes the intro, as a returning reader has already chosen.
class IntroAccountPage extends StatelessWidget {
  final int page;
  final IntroAccountsStore accounts;
  final VoidCallback onNext;
  final VoidCallback onSkip;

  const IntroAccountPage({
    super.key,
    required this.page,
    required this.accounts,
    required this.onNext,
    required this.onSkip,
  });

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<IntroAccountsStore, List<Account>>(
      store: accounts,
      onState: _build,
      onError: (context, _) => _build(context, const []),
    );
  }

  Widget _build(BuildContext context, List<Account> signedIn) {
    final l10n = L10n.of(context);
    return IntroPageFrame(
      page: page,
      title: l10n.intro_account_title,
      body: l10n.intro_account_body,
      footnote: signedIn.isEmpty ? l10n.intro_account_caption : null,
      onSkip: onSkip,
      card: _card(context, signedIn),
      // With accounts the chips sit under the drawing, so the card grows to
      // fit them instead of squeezing the phone.
      cardFitsContent: signedIn.isNotEmpty,
      footer: signedIn.isEmpty ? _signInFooter(context) : IntroButtonRow.next(l10n, onNext),
    );
  }

  Widget _card(BuildContext context, List<Account> signedIn) {
    if (signedIn.isEmpty) return const IntroFlowIllustration();
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SizedBox(
          height: kIntroCardMinHeight - 2 * kIntroCardPadding,
          child: IntroFlowIllustration(accounts: signedIn),
        ),
        const SizedBox(height: kTweetSpace3),
        Wrap(
          alignment: WrapAlignment.center,
          spacing: kTweetSpace2,
          runSpacing: kTweetSpace2,
          children: [for (final account in signedIn) IntroAccountChip(account: account)],
        ),
      ],
    );
  }

  Widget _signInFooter(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        IntroButtonRow(
          primary: IntroAction(l10n.add_account, () => _addAccount(context), key: const ValueKey('intro-add-account')),
          secondary: IntroAction(l10n.intro_not_now, onNext, key: const ValueKey('intro-not-now')),
        ),
        const SizedBox(height: kTweetSpace2),
        TextButton(
          key: const ValueKey('intro-import-backup'),
          onPressed: () => _importBackup(context),
          style: TextButton.styleFrom(minimumSize: const Size(0, kTweetTouchTarget)),
          child: Text(l10n.import_backup),
        ),
      ],
    );
  }

  Future<void> _addAccount(BuildContext context) async {
    await Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const TwitterLoginWebview()));
    await accounts.refresh();
  }

  Future<void> _importBackup(BuildContext context) async {
    final intro = context.read<IntroStore>();
    final imported = await importBackup(context);
    if (imported) {
      await intro.markSeen();
    }
  }
}

/// "Signed in as @name", with the handle's initial on the accent.
class IntroAccountChip extends StatelessWidget {
  final Account account;

  const IntroAccountChip({super.key, required this.account});

  @override
  Widget build(BuildContext context) {
    final tokens = introTokens(context);
    final surface = xLookFloatingSurface(tokens);
    return Container(
      padding: const EdgeInsetsDirectional.fromSTEB(kTweetSpace1, kTweetSpace1, kTweetSpace3, kTweetSpace1),
      decoration: BoxDecoration(
        color: surface,
        borderRadius: BorderRadius.circular(kTweetSpace4),
        border: Border.all(color: introHairline(tokens, surface), width: kTweetDividerThickness),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IntroAccountAvatar(account: account, size: 24),
          const SizedBox(width: kTweetSpace2),
          Flexible(
            child: Text(
              L10n.of(context).intro_signed_in_as(account.screenName ?? ''),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall!.copyWith(color: tokens.onBackground),
            ),
          ),
        ],
      ),
    );
  }
}
