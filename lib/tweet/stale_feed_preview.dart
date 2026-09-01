import 'dart:async' show TimeoutException;
import 'dart:io' show SocketException;

import 'package:http/http.dart' show ClientException;
import 'package:xta/catcher/exceptions.dart';

/// Why a feed's first page failed, in the same terms `ui/errors.dart` already
/// uses. A reader looking at cached posts still has to be told which of these
/// happened: a rate limit passes on its own, a broken account does not.
enum StaleFeedReason {
  // Nothing reached X at all.
  offline,
  timedOut,
  // Transient and per endpoint: waiting, or another account, fixes it.
  rateLimited,
  // The accounts themselves, or the endpoint, are the problem.
  noWorkingAccount,
  noAccount,
  endpointRefused,
  unknown,
}

/// Classifies the error a first page failed with.
StaleFeedReason staleFeedReasonOf(Object? error) {
  if (error is SocketException || error is ClientException) {
    return StaleFeedReason.offline;
  }
  if (error is TimeoutException) {
    return StaleFeedReason.timedOut;
  }
  if (error is RateLimitedException) {
    return StaleFeedReason.rateLimited;
  }
  if (error is NoWorkingAccountException) {
    return StaleFeedReason.noWorkingAccount;
  }
  if (error is NoAccountAvailableException) {
    return StaleFeedReason.noAccount;
  }
  if (error is EndpointRefusedException) {
    return StaleFeedReason.endpointRefused;
  }
  return StaleFeedReason.unknown;
}

/// Whether a failed feed should show its cached posts instead of an error page.
///
/// Only the *first* page qualifies: once a page has loaded (`items` is not
/// null) the reader already has fresh posts on screen and the failure belongs
/// at the bottom of the list, where the next-page error indicator puts it.
bool shouldShowStalePreview({
  required Object? error,
  // Null until a page has loaded, which is what makes this the *first* page.
  required List<Object?>? items,
  required List<Object?>? preview,
}) {
  return error != null && items == null && preview != null && preview.isNotEmpty;
}

/// Whether two instants fall on the same calendar day, which decides whether
/// the cache's age is worth stating as a time of day or as a full date.
bool sameCalendarDay(DateTime a, DateTime b) => a.year == b.year && a.month == b.month && a.day == b.day;
