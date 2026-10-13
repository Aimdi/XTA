import 'package:xta/plugins/substack/substack_transport.dart';

/// Virtual time for the Substack transport: a sleep moves the clock at once
/// and is recorded, so a test can check every wait without waiting.
class FakeSubstackClock extends SubstackClock {
  DateTime _now;
  final sleeps = <Duration>[];

  FakeSubstackClock([DateTime? start]) : _now = start ?? DateTime.utc(2026, 10, 13, 12);

  @override
  DateTime now() => _now;

  @override
  Future<void> sleep(Duration duration) async {
    sleeps.add(duration);
    _now = _now.add(duration);
  }

  void advance(Duration duration) => _now = _now.add(duration);
}
