import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_models.dart';

/// What one viewer has already asked a host about its posts: a post's tag
/// kinds and its related posts.
///
/// The viewer's pages are not kept alive, so swiping back to a post used to
/// ask for its tags and related posts again. Answers are held for as long as
/// the viewer is open, the last [capacity] of them; a failed one is asked
/// again next time.
class BooruPostLookups {
  final int capacity;

  BooruPostLookups({this.capacity = 40});

  final _answers = <String, Future<Object?>>{};

  /// The answer for [key], asked with [ask] only the first time.
  Future<T> remember<T>(String key, Future<T> Function() ask) {
    final known = _answers.remove(key);
    if (known != null) {
      _answers[key] = known;
      return known as Future<T>;
    }
    final answer = ask();
    _answers[key] = answer;
    answer.then<void>(
      (_) {},
      onError: (Object _) {
        if (identical(_answers[key], answer)) _answers.remove(key);
      },
    );
    while (_answers.length > capacity) {
      _answers.remove(_answers.keys.first);
    }
    return answer;
  }
}

/// [post]'s tag kinds and counts, asked once per open viewer when
/// [lookups] is given.
Future<Map<String, BooruTagInfo>> booruPostTagInfo(BooruClient client, BooruPostLookups? lookups, BooruPost post) {
  Future<Map<String, BooruTagInfo>> ask() => client.tagInfo(post);
  return lookups?.remember('tags:${post.key}', ask) ?? ask();
}
