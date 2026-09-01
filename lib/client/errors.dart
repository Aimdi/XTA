/// Failures the X client surfaces to the UI.
///
/// Each has a dedicated widget in `ui/errors.dart`; the ones mixing in
/// [SyntheticException] are shapes X returned that the app does not understand,
/// which are worth reporting rather than swallowing.
library;

import 'package:xta/catcher/exceptions.dart';

class UnknownProfileResultType with SyntheticException implements Exception {
  final String type;
  final String message;
  final String uri;

  UnknownProfileResultType(this.type, this.message, this.uri);

  @override
  String toString() {
    return 'Unknown profile result type: {type: $type, message: $message, uri: $uri}';
  }
}

class UnknownProfileUnavailableReason with SyntheticException implements Exception {
  final String reason;
  final String uri;

  UnknownProfileUnavailableReason(this.reason, this.uri);

  @override
  String toString() {
    return 'Unknown profile unavailable reason: {reason: $reason, uri: $uri}';
  }
}

class TwitterError {
  final String uri;
  final int code;
  final String message;

  TwitterError({required this.uri, required this.code, required this.message});

  @override
  String toString() {
    return 'TwitterError{code: $code, message: $message, url: $uri}';
  }
}

class SearchHasNoTimelineException {
  final String? query;

  SearchHasNoTimelineException(this.query);

  @override
  String toString() {
    return 'The search has no timeline {query: $query}';
  }
}

class UnknownTimelineItemType with SyntheticException implements Exception {
  final String type;
  final String entryId;

  UnknownTimelineItemType(this.type, this.entryId);

  @override
  String toString() {
    return 'Unknown timeline item type: {type: $type, entryId: $entryId}';
  }
}
