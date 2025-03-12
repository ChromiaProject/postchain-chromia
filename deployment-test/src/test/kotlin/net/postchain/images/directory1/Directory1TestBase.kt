package net.postchain.images.directory1

import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance

// Directory1 test base with common constants, cleanup and with per class lifecycle to avoid the static usage of ManagedModeBase
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class Directory1TestBase : ManagedModeBase() {

    companion object {

        val ecAdminKeyPair = KeyPair.of(
                "02552192E2FA6F1C1229EB74FBDC9F27EEB87641BA11B29F9094D4F729C081AFA3",
                "E9CF8BC054D6F853FA9457D95EDBCA76EF52CEAD2913674031513FB015F5B5C0")
        val provider1KeyPair = KeyPair.of(
                "03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05",
                "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114")
        val provider2KeyPair = KeyPair.of(
                "03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95",
                "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5")
        val provider3KeyPair = KeyPair.of(
                "03D01591E5466B07AC1D1F77BEBE2164AB0BA31366FBF005907F28FD144D64B871",
                "AD329F5C4E4DDF226D1A4948D7A2CCB34E76F64D4972B934FDBBDBEF4CA7B905")
        val provider4KeyPair = KeyPair.of(
                "02B6F2967CF9AFC4D289EF475A2C2DDEC9EAB79AC60C1C99683E3134074619E635",
                "2C3ED78A578575FD9E67996164A6B281C8AEE29D9AAEE9900088749E33C99150")

        val alicePubkey = "038f888dec563b5bc253e87abc90afd26c3287021d10236ea19d248043dc39e0b8".hexStringToByteArray()
        val alicePrivkey = "71b5b7f8de0661af934a5e4612f3d0ba183e639bdf4e7452fb6457ed3cfbc825".hexStringToByteArray()
        val aliceKeyPair = KeyPair(alicePubkey, alicePrivkey)
        val bobPubkey = "02E0A8A3C79C9F18B7CEAD2493435AC926B4A527EF670B873F5F1410084EFF9C80".hexStringToByteArray()
        val bobPrivkey = "B31AB878C62B0E940B345C659A456D3573CF25960823C34C7BEEB5D1F813BEFD".hexStringToByteArray()
        val bobKeyPair = KeyPair(bobPubkey, bobPrivkey)

        const val EC_CHAIN_NAME = "economy_chain"
        const val TC_CHAIN_NAME = "token_chain"
        const val EVM_TX_SUBMITTER_CHAIN = "evm_transaction_submitter_chain"

        val hashCalculator = GtvMerkleHashCalculatorV2(Secp256K1CryptoSystem())
    }

    @AfterAll
    open fun tearDown() {
        super.breakdown()
    }
}
