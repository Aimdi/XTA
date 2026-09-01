import 'package:flutter/material.dart';
// intl exports a TextDirection of its own, which has no `ltr` and silently
// shadows the one TextPainter wants.
import 'package:intl/intl.dart' hide TextDirection;
import 'package:xta/tweet/ticker/ticker_quote.dart';

/// Room reserved for the price labels down the right edge.
const double kTickerAxisWidth = 54;

/// Room reserved for the date labels along the bottom.
const double kTickerAxisHeight = 22;

/// How many price gridlines the chart draws, the last one included.
const int kTickerGridLines = 4;

/// The price line for a symbol, with axes and a scrubber.
///
/// Drawn rather than embedded: no third-party page, no scripts, nothing that
/// could carry a tracker in. A price chart with no numbers on it says only
/// "up" or "down", so both axes are labelled, and dragging across it reports
/// the point under the finger back to [onScrub] — which is how the header can
/// show that day's price instead of only the latest.
class TickerChart extends StatefulWidget {
  final TickerQuote quote;
  final double height;

  /// Called with the point being touched, and null when the finger lifts.
  final ValueChanged<TickerPoint?>? onScrub;

  const TickerChart({
    super.key,
    required this.quote,
    this.height = 200,
    this.onScrub,
  });

  @override
  State<TickerChart> createState() => _TickerChartState();
}

class _TickerChartState extends State<TickerChart> {
  int? _active;

  void _updateFrom(double dx, double width) {
    final points = widget.quote.points;
    final plotWidth = width - kTickerAxisWidth;
    if (points.length < 2 || plotWidth <= 0) {
      return;
    }

    final ratio = (dx / plotWidth).clamp(0.0, 1.0);
    final index = (ratio * (points.length - 1)).round();
    if (index != _active) {
      setState(() => _active = index);
      widget.onScrub?.call(points[index]);
    }
  }

  void _end() {
    if (_active != null) {
      setState(() => _active = null);
      widget.onScrub?.call(null);
    }
  }

  /// Dates for the bottom axis, formatted where the locale is known rather than
  /// inside the painter.
  List<String> _dateLabels() {
    final points = widget.quote.points;
    if (points.length < 2) {
      return const [];
    }

    // A month of daily closes needs a day and a month; a day of minute bars
    // needs the time. The span decides, not the range that was asked for.
    final span = points.last.at.difference(points.first.at);
    final format = span.inDays >= 200
        ? DateFormat.yMMM()
        : span.inDays >= 2
        ? DateFormat.Md()
        : DateFormat.Hm();

    return [
      format.format(points.first.at),
      format.format(points[points.length ~/ 2].at),
      format.format(points.last.at),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final up = widget.quote.isUp ?? true;
    final colour = up ? const Color(0xFF00BA7C) : const Color(0xFFF4212E);

    return SizedBox(
      height: widget.height,
      width: double.infinity,
      child: LayoutBuilder(
        builder: (context, constraints) => GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTapDown: (d) =>
              _updateFrom(d.localPosition.dx, constraints.maxWidth),
          onTapUp: (_) => _end(),
          onTapCancel: _end,
          onHorizontalDragStart: (d) =>
              _updateFrom(d.localPosition.dx, constraints.maxWidth),
          onHorizontalDragUpdate: (d) =>
              _updateFrom(d.localPosition.dx, constraints.maxWidth),
          onHorizontalDragEnd: (_) => _end(),
          onHorizontalDragCancel: _end,
          child: RepaintBoundary(
            child: CustomPaint(
              painter: _TickerChartPainter(
                closes: widget.quote.points
                    .map((p) => p.close)
                    .toList(growable: false),
                baseline: widget.quote.previousClose,
                line: colour,
                grid: theme.colorScheme.outlineVariant,
                label: theme.colorScheme.onSurfaceVariant,
                crosshair: theme.colorScheme.onSurface,
                surface: theme.colorScheme.surface,
                dateLabels: _dateLabels(),
                active: _active,
                textScaler: MediaQuery.textScalerOf(context),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _TickerChartPainter extends CustomPainter {
  final List<double> closes;
  final double? baseline;
  final Color line;
  final Color grid;
  final Color label;
  final Color crosshair;
  final Color surface;
  final List<String> dateLabels;
  final int? active;
  final TextScaler textScaler;

  _TickerChartPainter({
    required this.closes,
    required this.baseline,
    required this.line,
    required this.grid,
    required this.label,
    required this.crosshair,
    required this.surface,
    required this.dateLabels,
    required this.active,
    required this.textScaler,
  });

  /// Prices are formatted here rather than passed in: how many of them there
  /// are depends on the height, which only the painter knows.
  static final _price = NumberFormat.decimalPatternDigits(decimalDigits: 2);

  @override
  void paint(Canvas canvas, Size size) {
    final plot = Size(
      size.width - kTickerAxisWidth,
      size.height - kTickerAxisHeight,
    );
    if (closes.length < 2 || plot.width <= 0 || plot.height <= 0) {
      return;
    }

    var low = closes.reduce((a, b) => a < b ? a : b);
    var high = closes.reduce((a, b) => a > b ? a : b);
    // The previous close belongs inside the range, or the reference line it
    // draws would sit off the top or bottom of the chart.
    final base = baseline;
    if (base != null) {
      low = low < base ? low : base;
      high = high > base ? high : base;
    }

    final span = high - low;
    double yFor(double value) => span == 0
        ? plot.height / 2
        : plot.height - ((value - low) / span) * plot.height;
    double xFor(int i) => plot.width * (i / (closes.length - 1));

    _paintGrid(canvas, plot, low: low, high: high, yFor: yFor);
    _paintDates(canvas, plot, size);

    final path = Path()..moveTo(xFor(0), yFor(closes.first));
    for (var i = 1; i < closes.length; i++) {
      path.lineTo(xFor(i), yFor(closes[i]));
    }

    if (base != null) {
      _paintBaseline(canvas, plot, yFor(base));
    }

    canvas.drawPath(
      Path.from(path)
        ..lineTo(plot.width, plot.height)
        ..lineTo(0, plot.height)
        ..close(),
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [line.withValues(alpha: 0.22), line.withValues(alpha: 0.0)],
        ).createShader(Offset.zero & plot),
    );

    canvas.drawPath(
      path,
      Paint()
        ..color = line
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..strokeJoin = StrokeJoin.round
        ..strokeCap = StrokeCap.round,
    );

    final index = active;
    if (index != null && index >= 0 && index < closes.length) {
      _paintCrosshair(canvas, plot, x: xFor(index), y: yFor(closes[index]));
    }
  }

  /// Price gridlines, labelled down the right so the numbers never sit over the
  /// line itself.
  void _paintGrid(
    Canvas canvas,
    Size plot, {
    required double low,
    required double high,
    required double Function(double) yFor,
  }) {
    final paint = Paint()
      ..color = grid.withValues(alpha: 0.4)
      ..strokeWidth = 0.5;

    for (var i = 0; i <= kTickerGridLines; i++) {
      final value = low + (high - low) * (i / kTickerGridLines);
      final y = yFor(value);
      canvas.drawLine(Offset(0, y), Offset(plot.width, y), paint);
      _text(
        canvas,
        _price.format(value),
        Offset(plot.width + 6, y - 6),
        maxWidth: kTickerAxisWidth - 8,
      );
    }
  }

  void _paintDates(Canvas canvas, Size plot, Size size) {
    if (dateLabels.length != 3) {
      return;
    }

    final y = plot.height + 4;
    _text(canvas, dateLabels[0], Offset(0, y), maxWidth: plot.width / 3);
    _text(
      canvas,
      dateLabels[1],
      Offset(plot.width / 2 - 24, y),
      maxWidth: plot.width / 3,
    );
    _text(
      canvas,
      dateLabels[2],
      Offset(plot.width - 52, y),
      maxWidth: plot.width / 3,
    );
  }

  /// The previous close, dashed so it never reads as part of the price line.
  void _paintBaseline(Canvas canvas, Size plot, double y) {
    final paint = Paint()
      ..color = grid
      ..strokeWidth = 1;

    for (var x = 0.0; x < plot.width; x += 8) {
      canvas.drawLine(
        Offset(x, y),
        Offset((x + 4).clamp(0.0, plot.width), y),
        paint,
      );
    }
  }

  void _paintCrosshair(
    Canvas canvas,
    Size plot, {
    required double x,
    required double y,
  }) {
    canvas.drawLine(
      Offset(x, 0),
      Offset(x, plot.height),
      Paint()
        ..color = crosshair.withValues(alpha: 0.45)
        ..strokeWidth = 1,
    );

    // A ring rather than a dot: the line runs through it, so the point stays
    // readable against its own colour.
    canvas.drawCircle(Offset(x, y), 5, Paint()..color = surface);
    canvas.drawCircle(
      Offset(x, y),
      5,
      Paint()
        ..color = line
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.5,
    );
  }

  void _text(
    Canvas canvas,
    String value,
    Offset at, {
    required double maxWidth,
  }) {
    final painter = TextPainter(
      text: TextSpan(
        text: value,
        style: TextStyle(color: label, fontSize: 10),
      ),
      textDirection: TextDirection.ltr,
      textScaler: textScaler,
      maxLines: 1,
      ellipsis: '…',
    )..layout(maxWidth: maxWidth);

    painter.paint(canvas, at);
  }

  @override
  bool shouldRepaint(covariant _TickerChartPainter old) =>
      old.closes != closes ||
      old.baseline != baseline ||
      old.line != line ||
      old.active != active ||
      old.dateLabels != dateLabels;
}
