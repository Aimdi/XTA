package com.aimdi.xtanative.networks.deepmarks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BIP-340's own test vectors (deepmarks/bip340_vectors.csv, from the bitcoin/bips
 * repository at the commit XTA's bip340 package tests against).
 */
class SchnorrTest {
    private class Vector(val index: String, val secretKey: String, val publicKey: String, val aux: String, val message: String, val signature: String, val valid: Boolean, val comment: String)

    private val vectors = javaClass.getResource("/deepmarks/bip340_vectors.csv")!!.readText().lines().drop(1).filter { it.isNotBlank() }.map { line ->
        val cells = line.split(',', limit = 8).map { it.trim().lowercase() }
        Vector(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5], cells[6] == "true", cells[7])
    }

    @Test
    fun theVectorsWithASecretKeyAreSignedExactly() {
        val signing = vectors.filter { it.secretKey.isNotEmpty() }
        assertEquals(4, signing.size)
        for (vector in signing) {
            assertEquals(vector.publicKey, hex(Schnorr.publicKey(unhex(vector.secretKey))), "vector ${vector.index}")
            assertEquals(vector.signature, hex(Schnorr.sign(unhex(vector.secretKey), unhex(vector.message), unhex(vector.aux))), "vector ${vector.index}")
        }
    }

    @Test
    fun everyVectorIsAcceptedOrRefusedAsBip340Says() {
        assertEquals(15, vectors.size)
        for (vector in vectors) {
            assertEquals(vector.valid, Schnorr.verify(unhex(vector.publicKey), unhex(vector.message), unhex(vector.signature)), "vector ${vector.index}: ${vector.comment}")
        }
    }

    @Test
    fun aSignatureIsBoundToItsMessageAndKey() {
        val secret = unhex("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef")
        val message = ByteArray(32) { it.toByte() }
        val signature = Schnorr.sign(secret, message, ByteArray(32))
        assertTrue(Schnorr.verify(Schnorr.publicKey(secret), message, signature))
        assertFalse(Schnorr.verify(Schnorr.publicKey(secret), message.copyOf().also { it[0] = 1 }, signature))
        assertFalse(Schnorr.verify(Schnorr.publicKey(unhex("0".repeat(63) + "3")), message, signature))
    }
}
