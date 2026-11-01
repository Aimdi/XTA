import 'dart:convert';

/// A Threads post as its public pages embed it. [tag] is the topic it is filed under.
Map<String, Object?> threadsPostJson(String id, {String user = 'zuck', String? text, String? tag}) => {
  'pk': id,
  'code': 'C$id',
  'caption': {'text': text ?? 'post $id'},
  'user': {'username': user},
  if (tag != null)
    'text_post_app_info': {
      'tag_header': {'display_name': tag},
    },
};

/// A Threads public page (a post, a profile, a search) carrying [chains] of posts in `thread_items`, as its
/// `data-sjs` scripts do.
String threadsPage(List<List<Map<String, Object?>>> chains) {
  final blob = jsonEncode({
    'edges': [
      for (final chain in chains)
        {
          'node': {
            'thread_items': [
              for (final post in chain) {'post': post},
            ],
          },
        },
    ],
  });
  return '<html><script data-sjs>$blob</script></html>';
}
