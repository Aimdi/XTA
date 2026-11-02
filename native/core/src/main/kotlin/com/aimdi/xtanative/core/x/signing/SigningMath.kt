package com.aimdi.xtanative.core.x.signing

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// The arithmetic of XTA's signer (lib/client/x_client_transaction_id), kept to
// the same roundings: X checks the bytes, so "close" is wrong.

/** JavaScript's Math.round for the non-negative numbers the signer passes. */
internal fun jsRound(value: Double): Long {
    val floor = floor(value)
    return if (value - floor >= 0.5) ceil(value).toLong() else floor.toLong()
}

/** Dart's round(): half away from zero. */
internal fun dartRound(value: Double): Long =
    if (value >= 0) floor(value + 0.5).toLong() else -floor(-value + 0.5).toLong()

/** Two decimals, as Dart's `(value * 100).round() / 100.0`. */
internal fun roundTo2(value: Double): Double = dartRound(value * 100) / 100.0

/** -1.0 for an odd index, 0.0 for an even one. */
internal fun isOdd(index: Int): Double = if (index % 2 != 0) -1.0 else 0.0

/** A non-negative number in hexadecimal with its fraction: 2.75 → "2.C". */
internal fun floatToHex(x: Double): String {
    val result = ArrayList<String>()
    val fraction = x - x.toLong()
    var quotient = x.toLong()
    var current = x
    while (quotient > 0) {
        quotient = (current / 16).toLong()
        val remainder = (current - quotient * 16).toInt()
        result.add(0, hexDigit(remainder))
        current = quotient.toDouble()
    }
    if (fraction == 0.0) return result.joinToString("")
    result.add(".")
    var frac = fraction
    while (frac > 0) {
        frac *= 16
        val integer = frac.toInt()
        frac -= integer
        result.add(hexDigit(integer))
    }
    return result.joinToString("")
}

private fun hexDigit(value: Int): String = if (value > 9) (value + 55).toChar().toString() else value.toString()

internal fun interpolate(from: DoubleArray, to: DoubleArray, f: Double): DoubleArray {
    require(from.size == to.size) { "Mismatched interpolation arguments" }
    return DoubleArray(from.size) { i -> from[i] * (1 - f) + to[i] * f }
}

internal fun rotationMatrix(degrees: Double): DoubleArray {
    val radians = degrees * PI / 180
    return doubleArrayOf(cos(radians), -sin(radians), sin(radians), cos(radians))
}

/** The animation's easing curve, solved for time by bisection. */
internal class Cubic(private val curves: DoubleArray) {
    fun valueAt(time: Double): Double {
        if (time <= 0.0) {
            var startGradient = 0.0
            if (curves[0] > 0.0) {
                startGradient = curves[1] / curves[0]
            } else if (curves[1] == 0.0 && curves[2] > 0.0) {
                startGradient = curves[3] / curves[2]
            }
            return startGradient * time
        }
        if (time >= 1.0) {
            var endGradient = 0.0
            if (curves[2] < 1.0) {
                endGradient = (curves[3] - 1.0) / (curves[2] - 1.0)
            } else if (curves[2] == 1.0 && curves[0] < 1.0) {
                endGradient = (curves[1] - 1.0) / (curves[0] - 1.0)
            }
            return 1.0 + endGradient * (time - 1.0)
        }
        var start = 0.0
        var end = 1.0
        var mid = 0.0
        while (start < end) {
            mid = (start + end) / 2
            val xEstimate = calculate(curves[0], curves[2], mid)
            if (kotlin.math.abs(time - xEstimate) < 0.00001) {
                return calculate(curves[1], curves[3], mid)
            }
            if (xEstimate < time) start = mid else end = mid
        }
        return calculate(curves[1], curves[3], mid)
    }

    private fun calculate(a: Double, b: Double, m: Double): Double =
        3.0 * a * (1 - m) * (1 - m) * m + 3.0 * b * (1 - m) * m * m + m * m * m
}
