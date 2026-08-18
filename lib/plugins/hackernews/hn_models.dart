enum HnFeed { top, newest, best, ask, show, jobs }

/// One HN story, ask, show, or job.
class HnStory {
  final int id;
  final String title;
  final String? url;
  final String? text;
  final String? author;
  final int score;
  final int commentCount;
  final DateTime? createdAt;
  final String type;

  const HnStory({
    required this.id,
    required this.title,
    this.url,
    this.text,
    this.author,
    this.score = 0,
    this.commentCount = 0,
    this.createdAt,
    this.type = 'story',
  });

  String get hnUrl => 'https://news.ycombinator.com/item?id=$id';

  String? get host {
    final parsed = Uri.tryParse(url ?? '');
    if (parsed == null || parsed.host.isEmpty) {
      return null;
    }
    final host = parsed.host.toLowerCase();
    return host.startsWith('www.') ? host.substring(4) : host;
  }

  Map<String, Object?> toJson() => {
    'id': id,
    'title': title,
    'url': url,
    'text': text,
    'author': author,
    'score': score,
    'commentCount': commentCount,
    'createdAt': createdAt?.toUtc().toIso8601String(),
    'type': type,
  };

  factory HnStory.fromJson(Map<String, Object?> json) {
    final created = json['createdAt'] as String?;
    return HnStory(
      id: json['id'] as int? ?? 0,
      title: json['title'] as String? ?? '',
      url: json['url'] as String?,
      text: json['text'] as String?,
      author: json['author'] as String?,
      score: json['score'] as int? ?? 0,
      commentCount: json['commentCount'] as int? ?? 0,
      createdAt: created == null ? null : DateTime.tryParse(created),
      type: json['type'] as String? ?? 'story',
    );
  }
}

class HnComment {
  final int id;
  final String? author;
  final String? text;
  final DateTime? createdAt;
  final List<HnComment> children;
  final bool deleted;

  const HnComment({
    required this.id,
    this.author,
    this.text,
    this.createdAt,
    this.children = const [],
    this.deleted = false,
  });
}

class HnUser {
  final String id;
  final int karma;
  final String? about;
  final DateTime? createdAt;
  final int submittedCount;

  const HnUser({
    required this.id,
    this.karma = 0,
    this.about,
    this.createdAt,
    this.submittedCount = 0,
  });
}

class HnStoryPage {
  final List<HnStory> stories;
  final int page;
  final bool hasMore;

  const HnStoryPage({
    required this.stories,
    required this.page,
    required this.hasMore,
  });
}

class HnException implements Exception {
  final String message;

  const HnException(this.message);

  @override
  String toString() => 'HnException: $message';
}
