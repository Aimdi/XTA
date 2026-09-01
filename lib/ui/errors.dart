import 'dart:async';
import 'dart:io';

import 'package:async_button_builder/async_button_builder.dart';
import 'package:dynamic_color/dynamic_color.dart';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/catcher/exceptions.dart';

import 'package:xta/client/client.dart';
import 'package:xta/client/login_webview.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';

/// Snackbar for work already under way, with a small spinner in place of an
/// icon so a slow download does not look like a frozen one.
///
/// It stays put until the caller replaces it: the default few seconds would
/// leave a long download with nothing on screen saying it was still going.
///
/// The spinner takes no colour, so it picks up the accent from whichever theme
/// the snackbar is shown in.
SnackBar workingSnackBar(String message) => SnackBar(
      duration: const Duration(minutes: 2),
      content: Row(
        children: [
          const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
          const SizedBox(width: 12),
          Flexible(child: Text(message, style: const TextStyle(height: 1.5))),
        ],
      ),
    );

void showWorkingSnackBar(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..clearSnackBars()
    ..showSnackBar(workingSnackBar(message));
}

void showSnackBar(BuildContext context, {required String icon, required String message, bool clearBefore = true}) {
  if (clearBefore) {
    ScaffoldMessenger.of(context).clearSnackBars();
  }

  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Flexible(child: Text(message, style: const TextStyle(height: 1.5))),
          Text(icon),
        ],
      ),
    ),
  );
}

abstract class FritterErrorWidget extends StatelessWidget {
  const FritterErrorWidget({super.key});
}

/// The shape every full-screen error here has: centred when it fits, scrolling
/// when it does not.
///
/// These screens are a centred `Column` of an emoji, a heading, the details and
/// some buttons. At twice the text size that column is taller than the phone,
/// and a `Column` given less room than it needs does not clip politely — it
/// runs its last children off the bottom edge. The last child is the retry
/// button, so every error became a dead end for exactly the readers who had
/// turned the text up.
class ErrorLayout extends StatelessWidget {
  final List<Widget> children;

  const ErrorLayout({super.key, required this.children});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        child: ConstrainedBox(
          // Fill the viewport so the column still centres in it when it fits;
          // past that the column is taller and the scroll view takes over.
          constraints: BoxConstraints(minHeight: constraints.hasBoundedHeight ? constraints.maxHeight : 0),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(mainAxisAlignment: MainAxisAlignment.center, children: children),
          ),
        ),
      ),
    );
  }
}

class UnknownTwitterErrorCode with SyntheticException implements Exception {
  final int code;
  final String message;
  final String uri;

  UnknownTwitterErrorCode(this.code, this.message, this.uri);

  @override
  String toString() {
    return 'Unknown Twitter error code: {code: $code, message: $message, uri: $uri}';
  }
}

EmojiErrorWidget createEmojiError(TwitterError error) {
  String emoji;
  String message;

  switch (error.code) {
    case 22:
      emoji = '🔒';
      message = L10n.current.private_profile;
      break;
    case 34:
      emoji = '🤔';
      message = L10n.current.page_not_found;
      break;
    case 50:
      emoji = '🕵️';
      message = L10n.current.user_not_found;
      break;
    case 63:
      emoji = '👮';
      message = L10n.current.account_suspended;
      break;
    case 200:
      emoji = '⛔';
      message = L10n.current.forbidden;
      break;
    case 239:
      emoji = '💩';
      message = L10n.current.bad_guest_token;
      break;
    default:
      emoji = '💥';
      message = L10n.current.catastrophic_failure;
      break;
  }

  return EmojiErrorWidget(emoji: emoji, message: message, errorMessage: error.message);
}

class EmojiErrorWidget extends FritterErrorWidget {
  final String emoji;
  final String message;
  final String errorMessage;
  final Function? onRetry;
  final String? retryText;
  final bool showBackButton;

  const EmojiErrorWidget({
    super.key,
    required this.emoji,
    required this.message,
    required this.errorMessage,
    this.onRetry,
    this.retryText,
    this.showBackButton = true,
  });

  @override
  Widget build(BuildContext context) {
    var onRetry = this.onRetry;

    return ErrorLayout(
      children: [
        Container(
          margin: const EdgeInsets.only(bottom: 16),
          child: Text(emoji, style: const TextStyle(fontSize: 36)),
        ),
        Text(message, textAlign: TextAlign.center, style: const TextStyle(fontSize: 18)),
        Container(
          margin: const EdgeInsets.only(top: 12),
          child: Text(
            errorMessage,
            textAlign: TextAlign.center,
            style: TextStyle(color: Theme.of(context).hintColor),
          ),
        ),
        Container(
          margin: const EdgeInsets.only(top: 12),
          // Wraps rather than a Row: two buttons whose labels have been
          // translated and then scaled up do not fit side by side on a phone,
          // and a Row's answer to that is to run off the edge.
          child: Wrap(
            alignment: WrapAlignment.center,
            spacing: 16,
            runSpacing: 12,
            children: [
              if (showBackButton)
                ElevatedButton(
                  child: Text(L10n.of(context).back),
                  onPressed: () {
                    // Check if we can actually pop the last route, as we might have opened here directly from another app
                    if (Navigator.canPop(context)) {
                      Navigator.pop(context);
                      return;
                    }

                    // If we're running on Android, close the app gracefully. Otherwise, return to the home screen
                    if (Platform.isAndroid) {
                      SystemNavigator.pop();
                    } else {
                      Navigator.pushReplacementNamed(context, routeHome);
                    }
                  },
                ),
              if (onRetry != null)
                AsyncButtonBuilder(
                  showError: false,
                  showSuccess: false,
                  builder: (context, child, callback, buttonState) {
                    return ElevatedButton(onPressed: callback, child: child);
                  },
                  child: Text(retryText ?? L10n.current.retry),
                  onPressed: () => onRetry(),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Shared layout for actionable error screens: emoji, title, details and a row
/// of action buttons.
class ActionableErrorWidget extends FritterErrorWidget {
  final String emoji;
  final String title;
  final String details;
  final List<Widget> actions;

  const ActionableErrorWidget({
    super.key,
    required this.emoji,
    required this.title,
    required this.details,
    required this.actions,
  });

  @override
  Widget build(BuildContext context) {
    return ErrorLayout(
      children: [
        Container(
          margin: const EdgeInsets.only(bottom: 16),
          child: Text(emoji, style: const TextStyle(fontSize: 36)),
        ),
        Text(title, textAlign: TextAlign.center, style: const TextStyle(fontSize: 18)),
        Container(
          margin: const EdgeInsets.only(top: 12),
          child: Text(
            details,
            textAlign: TextAlign.center,
            style: TextStyle(color: Theme.of(context).hintColor),
          ),
        ),
        Container(
          margin: const EdgeInsets.only(top: 12),
          child: Wrap(alignment: WrapAlignment.center, spacing: 12, runSpacing: 12, children: actions),
        ),
      ],
    );
  }
}

/// Button that opens the X login flow to add another account.
Widget addAccountButton(BuildContext context) => ElevatedButton.icon(
  icon: const Icon(Icons.person_add),
  label: Text(L10n.of(context).add_account),
  onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const TwitterLoginWebview())),
);

class NoAccountErrorWidget extends FritterErrorWidget {
  final Function? onRetry;

  const NoAccountErrorWidget({super.key, this.onRetry});

  @override
  Widget build(BuildContext context) {
    return ActionableErrorWidget(
      emoji: '🔑',
      title: L10n.of(context).no_account_available_title,
      details: L10n.of(context).no_account_available_message,
      actions: [
        addAccountButton(context),
        if (onRetry != null) TextButton(child: Text(L10n.of(context).retry), onPressed: () => onRetry!()),
      ],
    );
  }
}

class RateLimitErrorWidget extends FritterErrorWidget {
  final Function? onRetry;

  const RateLimitErrorWidget({super.key, this.onRetry});

  @override
  Widget build(BuildContext context) {
    return ActionableErrorWidget(
      emoji: '⏳',
      title: L10n.of(context).rate_limited_title,
      details: L10n.of(context).rate_limited_message,
      actions: [
        addAccountButton(context),
        if (onRetry != null) TextButton(child: Text(L10n.of(context).retry), onPressed: () => onRetry!()),
      ],
    );
  }
}

class NoWorkingAccountErrorWidget extends FritterErrorWidget {
  final Function? onRetry;

  const NoWorkingAccountErrorWidget({super.key, this.onRetry});

  @override
  Widget build(BuildContext context) {
    return ActionableErrorWidget(
      emoji: '🤷',
      title: L10n.of(context).no_working_account_title,
      details: L10n.of(context).no_working_account_message,
      actions: [
        addAccountButton(context),
        if (onRetry != null) TextButton(child: Text(L10n.of(context).retry), onPressed: () => onRetry!()),
      ],
    );
  }
}

/// X refused the endpoint for every account. Deliberately does *not* offer to
/// add an account: the whole point of this error is that the accounts are not
/// the problem, and inviting the reader to add one sends them off to fix
/// something that is not broken.
class EndpointRefusedErrorWidget extends FritterErrorWidget {
  final Function? onRetry;

  const EndpointRefusedErrorWidget({super.key, this.onRetry});

  @override
  Widget build(BuildContext context) {
    return ActionableErrorWidget(
      emoji: '🚧',
      title: L10n.of(context).endpoint_refused_title,
      details: L10n.of(context).endpoint_refused_message,
      actions: [if (onRetry != null) TextButton(child: Text(L10n.of(context).retry), onPressed: () => onRetry!())],
    );
  }
}

class InlineErrorWidget extends FritterErrorWidget {
  final Object? error;

  const InlineErrorWidget({super.key, required this.error});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Container(
            margin: const EdgeInsets.only(right: 8),
            child: Icon(Icons.error_outline, color: Colors.red.harmonizeWith(Theme.of(context).colorScheme.primary)),
          ),
          Text(
            '$error',
            textAlign: TextAlign.center,
            style: TextStyle(color: Theme.of(context).hintColor),
          ),
        ],
      ),
    );
  }
}

class AlertErrorWidget extends FritterErrorWidget {
  final Object? error;
  final StackTrace? stackTrace;
  final String prefix;

  const AlertErrorWidget({super.key, required this.error, required this.stackTrace, required this.prefix});

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      content: FullPageErrorWidget(error: error, prefix: prefix, stackTrace: stackTrace),
    );
  }
}

class ScaffoldErrorWidget extends FritterErrorWidget {
  final Object? error;
  final StackTrace? stackTrace;
  final String prefix;
  final Function? onRetry;
  final String? retryText;

  const ScaffoldErrorWidget({
    super.key,
    required this.error,
    required this.stackTrace,
    required this.prefix,
    this.onRetry,
    this.retryText,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(),
      body: FullPageErrorWidget(
        error: error,
        prefix: prefix,
        stackTrace: stackTrace,
        onRetry: onRetry,
        retryText: retryText,
      ),
    );
  }
}

class FullPageErrorWidget extends FritterErrorWidget {
  final Object? error;
  final StackTrace? stackTrace;
  final String prefix;
  final Function? onRetry;
  final String? retryText;

  const FullPageErrorWidget({
    super.key,
    required this.error,
    required this.stackTrace,
    required this.prefix,
    this.onRetry,
    this.retryText,
  });

  @override
  Widget build(BuildContext context) {
    var onRetry = this.onRetry;

    var error = this.error;
    if (error is SocketException) {
      return EmojiErrorWidget(
        emoji: '🔌',
        message: L10n.of(context).could_not_contact_twitter,
        errorMessage: L10n.of(context).please_check_your_internet_connection_error_message(error.message),
        onRetry: onRetry,
      );
    }

    if (error is NoAccountAvailableException) {
      return NoAccountErrorWidget(onRetry: onRetry);
    }

    if (error is RateLimitedException) {
      return RateLimitErrorWidget(onRetry: onRetry);
    }

    if (error is NoWorkingAccountException) {
      return NoWorkingAccountErrorWidget(onRetry: onRetry);
    }

    if (error is EndpointRefusedException) {
      return EndpointRefusedErrorWidget(onRetry: onRetry);
    }

    if (error is TwitterError) {
      return createEmojiError(error);
    }

    if (error is TimeoutException) {
      return EmojiErrorWidget(
        emoji: '⏱️',
        message: L10n.of(context).timed_out,
        errorMessage: L10n.of(context).this_took_too_long_to_load_please_check_your_network_connection,
        onRetry: onRetry,
      );
    }

    // The branch every plugin exception lands in: their errors are plain
    // `implements Exception`, so none match the special cases above. It used to
    // print the stack trace unconditionally — and every plugin passes null, so
    // under the message sat the literal word "null" — inside a column capped at
    // 500px, which clipped the retry button away exactly when the details were
    // long or the text was large. ErrorLayout was written for that second
    // problem and this branch never got it.
    return ErrorLayout(
      children: [
        Container(
          margin: const EdgeInsets.only(bottom: 16),
          child: Icon(
            Icons.error_outline,
            color: Colors.red.harmonizeWith(Theme.of(context).colorScheme.primary),
            size: 36,
          ),
        ),
        Text(
          L10n.of(context).oops_something_went_wrong,
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 18),
        ),
        Container(
          margin: const EdgeInsets.only(top: 12),
          child: Text(prefix, textAlign: TextAlign.center, style: TextStyle(color: Theme.of(context).hintColor)),
        ),
        Container(
          alignment: Alignment.center,
          margin: const EdgeInsets.only(top: 12),
          child: Text('$error', textAlign: TextAlign.left, style: TextStyle(color: Theme.of(context).hintColor)),
        ),
        if (stackTrace != null)
          Container(
            alignment: Alignment.center,
            margin: const EdgeInsets.only(top: 12),
            child: Text(
              '$stackTrace',
              textAlign: TextAlign.left,
              style: TextStyle(color: Theme.of(context).hintColor),
            ),
          ),
        if (onRetry != null)
          Container(
            margin: const EdgeInsets.only(top: 12),
            child: ElevatedButton(child: Text(retryText ?? L10n.current.retry), onPressed: () => onRetry()),
          ),
      ],
    );
  }
}
