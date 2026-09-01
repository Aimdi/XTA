/// Runs [mapper] over [items] with at most [concurrency] in-flight at once.
///
/// Order of results matches [items]. Used by the group feed so a large
/// subscription set (dozens of search chunks) cannot open every request at
/// once and trip X into a cascade of 404s / rate limits.
Future<List<T>> mapWithConcurrency<E, T>(Iterable<E> items, int concurrency, Future<T> Function(E item) mapper) async {
  final list = items.toList(growable: false);
  if (list.isEmpty) {
    return const [];
  }

  final limit = concurrency < 1 ? 1 : concurrency;
  final results = List<T?>.filled(list.length, null);
  var next = 0;

  Future<void> worker() async {
    while (true) {
      final index = next;
      next++;
      if (index >= list.length) {
        return;
      }
      results[index] = await mapper(list[index]);
    }
  }

  final workerCount = limit < list.length ? limit : list.length;
  await Future.wait(List.generate(workerCount, (_) => worker()));
  return results.cast<T>();
}
