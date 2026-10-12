/// Requests in flight per account and endpoint, so a burst of parallel
/// requests spreads over the accounts instead of piling onto the one the quota
/// alone would pick. In memory only: it describes this moment, not history.
class AccountLoad {
  static final Map<String, int> _inFlight = {};

  static String _key(String accountId, String endpoint) => '$accountId\n$endpoint';

  static int of(String accountId, String endpoint) => _inFlight[_key(accountId, endpoint)] ?? 0;

  static void begin(String accountId, String endpoint) =>
      _inFlight.update(_key(accountId, endpoint), (n) => n + 1, ifAbsent: () => 1);

  static void end(String accountId, String endpoint) {
    final key = _key(accountId, endpoint);
    final left = (_inFlight[key] ?? 1) - 1;
    left > 0 ? _inFlight[key] = left : _inFlight.remove(key);
  }
}
