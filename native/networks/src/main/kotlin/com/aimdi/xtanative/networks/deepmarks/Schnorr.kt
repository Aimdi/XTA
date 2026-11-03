package com.aimdi.xtanative.networks.deepmarks

import java.math.BigInteger
import java.security.MessageDigest

/**
 * BIP-340 Schnorr signatures over secp256k1, the way Nostr signs events: what
 * XTA gets from the bip340 package, in BigInteger arithmetic. Like that
 * package it is not constant-time; it signs the reader's own bookmarks on
 * their own device. Keys, messages and signatures are raw bytes.
 */
internal object Schnorr {
    private val P = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    private val N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val G = Point(
        BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
        BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16),
    )
    private val THREE = BigInteger.valueOf(3)
    private val FOUR = BigInteger.valueOf(4)
    private val SEVEN = BigInteger.valueOf(7)
    private val EIGHT = BigInteger.valueOf(8)

    /** A point on the curve; null stands for the point at infinity. */
    private data class Point(val x: BigInteger, val y: BigInteger) {
        val hasEvenY: Boolean get() = !y.testBit(0)
    }

    /** A point as (x·z², y·z³, z), so that adding and doubling need no inverse until the end; null is infinity. */
    private class Jacobian(val x: BigInteger, val y: BigInteger, val z: BigInteger)

    /** Whether [secretKey] is a secret key at all: 32 bytes, from 1 to n − 1. */
    fun isSecretKey(secretKey: ByteArray): Boolean =
        secretKey.size == 32 && BigInteger(1, secretKey).let { it.signum() > 0 && it < N }

    /** The x-only public key of [secretKey]. */
    fun publicKey(secretKey: ByteArray): ByteArray {
        require(isSecretKey(secretKey)) { "Not a secp256k1 secret key" }
        return bytes(times(G, BigInteger(1, secretKey))!!.x)
    }

    /** BIP-340's default signing of [message] with [secretKey], its nonce hedged by 32 bytes of [auxRandom]. */
    fun sign(secretKey: ByteArray, message: ByteArray, auxRandom: ByteArray): ByteArray {
        require(isSecretKey(secretKey)) { "Not a secp256k1 secret key" }
        require(auxRandom.size == 32) { "BIP-340 takes 32 bytes of auxiliary randomness" }
        val d0 = BigInteger(1, secretKey)
        val p = times(G, d0)!!
        val d = if (p.hasEvenY) d0 else N - d0
        val t = xor(bytes(d), taggedHash("BIP0340/aux", auxRandom))
        val k0 = BigInteger(1, taggedHash("BIP0340/nonce", t + bytes(p.x) + message)).mod(N)
        check(k0.signum() != 0) { "BIP-340 nonce is zero" }
        val r = times(G, k0)!!
        val k = if (r.hasEvenY) k0 else N - k0
        val e = challenge(bytes(r.x), bytes(p.x), message)
        return bytes(r.x) + bytes((k + e * d).mod(N))
    }

    /** Whether [signature] is [publicKey]'s BIP-340 signature of [message]. */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val p = liftX(BigInteger(1, publicKey)) ?: return false
        val rx = signature.copyOfRange(0, 32)
        val r = BigInteger(1, rx)
        val s = BigInteger(1, signature.copyOfRange(32, 64))
        if (r >= P || s >= N) return false
        val e = challenge(rx, publicKey, message)
        val point = plus(times(G, s), times(p, (N - e).mod(N))) ?: return false
        return point.hasEvenY && point.x == r
    }

    private fun challenge(rx: ByteArray, px: ByteArray, message: ByteArray) =
        BigInteger(1, taggedHash("BIP0340/challenge", rx + px + message)).mod(N)

    /** The point with x coordinate [x] and an even y, when there is one. */
    private fun liftX(x: BigInteger): Point? {
        if (x >= P) return null
        val c = (x.modPow(THREE, P) + SEVEN).mod(P)
        val y = c.modPow((P + BigInteger.ONE).shiftRight(2), P)
        if (y.modPow(BigInteger.TWO, P) != c) return null
        return Point(x, if (y.testBit(0)) P - y else y)
    }

    /** [point] (null: infinity) plus [other]. */
    private fun plus(point: Point?, other: Point?): Point? = affine(plus(point?.let(::jacobian), other?.let(::jacobian)))

    /** [k] times [point], doubling and adding from the top bit down. */
    private fun times(point: Point, k: BigInteger): Point? {
        val addend = jacobian(point)
        return affine((k.bitLength() - 1 downTo 0).fold(null as Jacobian?) { sum, bit -> plus(twice(sum), addend.takeIf { k.testBit(bit) }) })
    }

    private fun jacobian(point: Point) = Jacobian(point.x, point.y, BigInteger.ONE)

    private fun affine(point: Jacobian?): Point? = point?.run {
        val inverse = z.modInverse(P)
        val inverseSquared = inverse * inverse % P
        Point(x * inverseSquared % P, y * inverseSquared % P * inverse % P)
    }

    private fun twice(point: Jacobian?): Jacobian? {
        if (point == null || point.y.signum() == 0) return null
        val ySquared = point.y * point.y % P
        val s = FOUR * point.x % P * ySquared % P
        val m = THREE * point.x % P * point.x % P
        val x = (m * m - BigInteger.TWO * s).mod(P)
        return Jacobian(x, (m * (s - x) - EIGHT * ySquared % P * ySquared).mod(P), BigInteger.TWO * point.y % P * point.z % P)
    }

    private fun plus(a: Jacobian?, b: Jacobian?): Jacobian? {
        if (a == null) return b
        if (b == null) return a
        val aZSquared = a.z * a.z % P
        val bZSquared = b.z * b.z % P
        val u1 = a.x * bZSquared % P
        val u2 = b.x * aZSquared % P
        val s1 = a.y * bZSquared % P * b.z % P
        val s2 = b.y * aZSquared % P * a.z % P
        if (u1 == u2) return if (s1 == s2) twice(a) else null
        val h = (u2 - u1).mod(P)
        val r = (s2 - s1).mod(P)
        val hSquared = h * h % P
        val hCubed = hSquared * h % P
        val u1HSquared = u1 * hSquared % P
        val x = (r * r - hCubed - BigInteger.TWO * u1HSquared).mod(P)
        return Jacobian(x, (r * (u1HSquared - x) - s1 * hCubed).mod(P), h * a.z % P * b.z % P)
    }

    private fun taggedHash(tag: String, data: ByteArray): ByteArray {
        val tagHash = sha256(tag.toByteArray(Charsets.UTF_8))
        return sha256(tagHash + tagHash + data)
    }

    private fun xor(a: ByteArray, b: ByteArray) = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }

    /** [x] as 32 big-endian bytes. */
    private fun bytes(x: BigInteger): ByteArray = x.toByteArray().takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }
}

/** The SHA-256 of [data]. */
internal fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
