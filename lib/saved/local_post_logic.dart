/// Pure helpers for reader-authored local notes.
library;

const int localPostMaxLength = 20000;

/// Empty after trim is rejected. Over-long bodies are clipped, not thrown.
String? normalizeLocalPostBody(String body) {
  final trimmed = body.trim();
  if (trimmed.isEmpty) {
    return null;
  }
  if (trimmed.length <= localPostMaxLength) {
    return trimmed;
  }
  return trimmed.substring(0, localPostMaxLength);
}

bool localPostMatchesQuery(String body, String query) {
  if (query.isEmpty) {
    return true;
  }
  return body.toLowerCase().contains(query);
}
