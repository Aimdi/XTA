/// Waits on real asynchronous work (I/O, a request a store does not await)
/// until [done], then returns; after [timeout] it gives up and leaves the
/// expectation that follows to fail. A fixed sleep instead fails whenever the
/// machine is busier than the one the test was written on.
Future<void> waitUntil(bool Function() done, {Duration timeout = const Duration(seconds: 5)}) async {
  const step = Duration(milliseconds: 5);
  for (var waited = Duration.zero; waited < timeout && !done(); waited += step) {
    await Future<void>.delayed(step);
  }
}
