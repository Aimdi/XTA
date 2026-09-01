import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/threads/threads_discovery.dart';
import 'package:xta/plugins/threads/threads_models.dart';

ThreadsPost _post({required String handle, String? name, String? repostedBy}) {
  return ThreadsPost(
    id: 'https://www.threads.com/@$handle/post/1',
    handle: handle,
    authorName: name ?? handle,
    text: 'hi',
    repostedByHandle: repostedBy,
  );
}

void main() {
  test('reposted original authors come first and skip follows', () {
    final people = peopleToFollowFromThreads(
      posts: [
        _post(handle: 'alice'),
        _post(handle: 'bob', name: 'Bob', repostedBy: 'alice'),
        _post(handle: 'cara', repostedBy: 'alice'),
        _post(handle: 'alice'),
      ],
      alreadyFollows: (handle) => handle == 'alice',
    );

    expect(people.map((e) => e.handle), ['bob', 'cara']);
    expect(people.first.fromRepost, isTrue);
    expect(people.first.name, 'Bob');
  });

  test('caps the strip and ignores empty handles', () {
    final people = peopleToFollowFromThreads(
      posts: [
        _post(handle: ''),
        for (var i = 0; i < 20; i++) _post(handle: 'user$i'),
      ],
      alreadyFollows: (_) => false,
      cap: 3,
    );
    expect(people, hasLength(3));
    expect(people.map((e) => e.handle), ['user0', 'user1', 'user2']);
  });

  test('starter handles are the public ones a guest can try', () {
    expect(kThreadsStarterHandles, ['zuck', 'mosseri', 'meta']);
  });
}
