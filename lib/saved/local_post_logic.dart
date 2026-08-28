/// Pure helpers for reader-authored local notes.
library;

import 'dart:convert';

import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';

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

bool localPostHasContent(String body, List<LocalPostMedia> media) {
  return normalizeLocalPostBody(body) != null || media.isNotEmpty;
}

bool localPostMatchesQuery(String body, String query) {
  if (query.isEmpty) {
    return true;
  }
  return body.toLowerCase().contains(query);
}

bool localPostRecordMatches(LocalPost post, String query) {
  if (localPostMatchesQuery(post.body, query)) {
    return true;
  }
  for (final item in post.media) {
    if (item.name.toLowerCase().contains(query)) {
      return true;
    }
  }
  return false;
}

String inferLocalPostMime(String name, String? mime) {
  if (mime != null && mime.isNotEmpty && mime != 'application/octet-stream') {
    return mime;
  }
  final dot = name.lastIndexOf('.');
  final ext = dot < 0 ? '' : name.substring(dot + 1).toLowerCase();
  return switch (ext) {
    'jpg' || 'jpeg' => 'image/jpeg',
    'png' => 'image/png',
    'gif' => 'image/gif',
    'webp' => 'image/webp',
    'heic' || 'heif' => 'image/heic',
    'mp4' => 'video/mp4',
    'mov' => 'video/quicktime',
    'webm' => 'video/webm',
    'mkv' => 'video/x-matroska',
    _ => mime == null || mime.isEmpty ? 'application/octet-stream' : mime,
  };
}

String? encodeQuotedTweet(TweetWithCard tweet) {
  try {
    return jsonEncode(tweet.toJson());
  } catch (_) {
    return null;
  }
}

TweetWithCard? parseQuotedTweet(String? json) {
  if (json == null || json.isEmpty) {
    return null;
  }
  try {
    final decoded = jsonDecode(json);
    if (decoded is! Map) {
      return null;
    }
    return TweetWithCard.fromJson(Map<String, dynamic>.from(decoded));
  } catch (_) {
    return null;
  }
}
