import 'package:flutter_test/flutter_test.dart';
import 'package:xta/utils/urls.dart';

void main() {
  test('extractListId accepts list URLs and bare ids', () {
    expect(extractListId('https://x.com/i/lists/1234567890123456789'), '1234567890123456789');
    expect(extractListId('https://twitter.com/i/lists/42/'), '42');
    expect(extractListId('  987654321  '), '987654321');
    expect(extractListId('https://x.com/SomeUser/lists'), isNull);
    expect(extractListId('https://x.com/i/lists/not-a-number'), isNull);
    expect(extractListId('hello'), isNull);
  });

  test('parseUri routes list links, profiles and posts correctly', () async {
    expect(await parseUri(Uri.parse('https://x.com/i/lists/123')), isA<ListUriInfo>());
    expect(await parseUri(Uri.parse('https://x.com/DogsTrust')), isA<ProfileUriInfo>());
    expect(await parseUri(Uri.parse('https://x.com/DogsTrust/lists')), isA<ProfileUriInfo>());
    expect(await parseUri(Uri.parse('https://x.com/DogsTrust/status/1')), isA<PostUriInfo>());
    // /i/… reserved paths must never parse as a profile named "i".
    expect(await parseUri(Uri.parse('https://x.com/i/topics/tweet/9')), isA<PostUriInfo>());
    expect(await parseUri(Uri.parse('https://x.com/i/flow/login')), isA<UnknownResult>());
  });

  test('parseUri routes broadcasts and Spaces instead of failing open', () async {
    final broadcast = await parseUri(Uri.parse('https://x.com/i/broadcasts/1abc'));
    expect(broadcast, isA<LiveUriInfo>());
    expect((broadcast as LiveUriInfo).isSpace, isFalse);
    expect(broadcast.url, 'https://x.com/i/broadcasts/1abc');

    final space = await parseUri(Uri.parse('https://x.com/i/spaces/1room'));
    expect(space, isA<LiveUriInfo>());
    expect((space as LiveUriInfo).isSpace, isTrue);
    expect(space.url, 'https://x.com/i/spaces/1room');

    final singular = await parseUri(Uri.parse('https://twitter.com/i/space/1solo'));
    expect(singular, isA<LiveUriInfo>());
    expect((singular as LiveUriInfo).url, 'https://x.com/i/spaces/1solo');

    final singularBroadcast = await parseUri(Uri.parse('https://x.com/i/broadcast/1solo'));
    expect(singularBroadcast, isA<LiveUriInfo>());
    expect((singularBroadcast as LiveUriInfo).url, 'https://x.com/i/broadcasts/1solo');

    final mobileSpace = await parseUri(Uri.parse('https://mobile.x.com/i/spaces/1room'));
    expect(mobileSpace, isA<LiveUriInfo>());
    expect((mobileSpace as LiveUriInfo).isSpace, isTrue);

    expect(await parseUri(Uri.parse('https://x.com/i/broadcasts/1YqJvq…')), isA<UnknownResult>());
    expect(await parseUri(Uri.parse('https://x.com/i/spaces/1Owx...')), isA<UnknownResult>());

    final pscp = await parseUri(Uri.parse('https://pscp.tv/w/1vod'));
    expect(pscp, isA<LiveUriInfo>());
    expect((pscp as LiveUriInfo).url, 'https://x.com/i/broadcasts/1vod');
  });

  test('isLiveWatchUrl covers broadcasts, Spaces, and Periscope', () {
    expect(isLiveWatchUrl('https://x.com/i/broadcasts/1abc'), isTrue);
    expect(isLiveWatchUrl('https://x.com/i/broadcast/1solo'), isTrue);
    expect(isLiveWatchUrl('https://x.com/i/spaces/1room'), isTrue);
    expect(isLiveWatchUrl('https://www.x.com/i/space/1room'), isTrue);
    expect(isLiveWatchUrl('x.com/i/spaces/1disp'), isTrue);
    expect(isLiveWatchUrl('https://pscp.tv/w/1abc'), isTrue);
    expect(isLiveWatchUrl('https://x.com/i/broadcasts/1YqJvq…'), isFalse);
    expect(isLiveWatchUrl('https://x.com/someone/status/1'), isFalse);
    expect(isLiveWatchUrl('https://x.com/i/lists/1'), isFalse);
    expect(isLiveWatchUrl(null), isFalse);
  });
}
