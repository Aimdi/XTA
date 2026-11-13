package com.aimdi.xtanative.networks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoAddressesTest {
    /** PEPE's mainnet contract, in its EIP-55 checksum casing. */
    private val pepe = "0x6982508145454Ce325dDbE47a25d4ec3d2311933"
    private val pepeLower = "0x6982508145454ce325ddbe47a25d4ec3d2311933"

    /** BONK's Solana mint. */
    private val bonk = "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263"
    private val evm = "0x23a2b8f3c1d4e5f60718293a4b5c6d7e8f90fe51"
    private val mint = "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm"

    @Test
    fun anEvmAddressIsRecognisedWhateverItsChecksumCasing() {
        for (raw in listOf(pepe, pepeLower, pepe.uppercase().replaceFirst("0X", "0x"))) {
            assertEquals(CryptoAddressInput(CryptoAddressKind.EVM, pepeLower), CryptoAddresses.detect(raw), raw)
        }
    }

    @Test
    fun aSolanaMintIsRecognisedAndKeepsItsCase() {
        assertEquals(CryptoAddressInput(CryptoAddressKind.SOLANA, bonk), CryptoAddresses.detect("  $bonk "))
    }

    @Test
    fun aCashtagPrefixAndExplorerLinksAreLookedThrough() {
        assertEquals(pepeLower, CryptoAddresses.detect("$$pepe")?.address)
        assertEquals(pepeLower, CryptoAddresses.detect("https://etherscan.io/token/$pepe")?.address)
        assertEquals(bonk, CryptoAddresses.detect("https://dexscreener.com/solana/$bonk")?.address)
        assertEquals(bonk, CryptoAddresses.detect("https://solscan.io/token/$bonk?cluster=mainnet#holders")?.address)
        assertEquals(bonk, CryptoAddresses.detect("solana:$bonk")?.address, "a payment link names its mint")
    }

    @Test
    fun thingsThatOnlyLookCloseAreNotAddresses() {
        for (raw in listOf(
            "",
            "PEPE",
            "\$AAPL",
            "0x6982508145454ce325ddbe47a25d4ec3d231193", // 39 hex digits
            "0x6982508145454ce325ddbe47a25d4ec3d23119333", // 41 hex digits
            "0x6982508145454ce325ddbe47a25d4ec3d231193g", // not hex
            "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB26O", // O is not base58
            "bitcoincashbitcoincashbitcoincashx", // letters only: a name, not a mint
            "Dez", // far too short
        )) {
            assertNull(CryptoAddresses.detect(raw), raw)
        }
    }

    @Test
    fun rowsShowTheNetworkAndAShortenedAddress() {
        assertEquals("0x6982…1933", CryptoAddresses.short(pepeLower))
        assertEquals("DezX…B263", CryptoAddresses.short(bonk))
        assertEquals("short", CryptoAddresses.short("short"))
        assertEquals("BNB Chain", CryptoAddresses.chainName("bsc"))
        assertEquals("newchain", CryptoAddresses.chainName("newchain"))
    }

    @Test
    fun addressesCompareCaseInsensitivelyOnlyOnEvm() {
        assertTrue(CryptoAddresses.same(pepe, pepeLower))
        assertFalse(CryptoAddresses.same(bonk, bonk.lowercase()))
    }

    @Test
    fun aContractAddressInAPostIsCutOutOfItsText() {
        assertEquals(
            listOf(
                AddressPiece.Text("CA: "),
                AddressPiece.Address(evm),
                AddressPiece.Text(", and on Solana "),
                AddressPiece.Address(mint),
                AddressPiece.Text("."),
            ),
            CryptoAddresses.pieces("CA: $evm, and on Solana $mint."),
        )
    }

    @Test
    fun aLongWordWithoutADigitAndAnAddressInsideAHashtagStayWords() {
        val word = "So Supercalifragilisticexpialidociousness"
        assertEquals(listOf(AddressPiece.Text(word)), CryptoAddresses.pieces(word))
        assertEquals(listOf(AddressPiece.Text("#$evm and @$mint")), CryptoAddresses.pieces("#$evm and @$mint"))
        assertEquals(listOf(AddressPiece.Text("x$evm")), CryptoAddresses.pieces("x$evm"), "part of a longer word")
    }

    @Test
    fun aPostShowsAnAddressCutInTheMiddleAndAWatchedTokensWithItsNetwork() {
        assertEquals("0x23a2…⁠fe51", CryptoAddresses.postLabel(evm), "unbroken by the cut")
        assertEquals("EKpQ…⁠zcjm", CryptoAddresses.postLabel(mint))
        val watched = mapOf(evm to "Base")
        val shown = evm.uppercase().replaceFirst("0X", "0x")
        assertEquals("Base", CryptoAddresses.networkIn(shown, watched), "any casing")
        assertEquals("Base\u00a0·\u00a00x23A2…\u2060FE51", CryptoAddresses.postLabel(shown, CryptoAddresses.networkIn(shown, watched)), "one unit: no break around the dot")
        assertNull(CryptoAddresses.networkIn(mint, watched), "not watched: no network")
        assertNull(CryptoAddresses.networkIn(mint.lowercase(), mapOf(mint to "Solana")), "a mint's case is its own")
    }
}
