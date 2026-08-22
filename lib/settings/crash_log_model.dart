import 'package:flutter_triple/flutter_triple.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:xta/utils/crash_log.dart';
import 'package:xta/utils/crash_log_entry.dart';

/// What the Crash log screen shows: the recorded entries, newest first, plus
/// the header the copied text needs to be worth reading.
class CrashLogView {
  final List<CrashLogEntry> entries;
  final String header;

  const CrashLogView({required this.entries, required this.header});

  static const empty = CrashLogView(entries: [], header: '');

  String toPlainText() =>
      formatCrashLog(entries.reversed.toList(), header: header);
}

class CrashLogModel extends Store<CrashLogView> {
  final CrashLog log;

  CrashLogModel(this.log) : super(CrashLogView.empty);

  Future<void> load() => execute(() async {
    await log.flush();
    return CrashLogView(
      entries: log.entries.reversed.toList(),
      header: await _header(),
    );
  });

  Future<void> addTestEntry() async {
    await log.recordTestEntry();
    await load();
  }

  Future<void> clear() async {
    await log.clear();
    await load();
  }

  /// Deliberately not localised, like the entries themselves: it is pasted into
  /// a bug report read by whoever maintains the fork.
  Future<String> _header() async {
    final info = await PackageInfo.fromPlatform();
    return 'XTA crash log · ${info.version}+${info.buildNumber} · '
        '${DateTime.now().toIso8601String()}';
  }
}
