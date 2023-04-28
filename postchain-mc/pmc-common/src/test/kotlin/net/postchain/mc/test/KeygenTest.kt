package net.postchain.mc.test

import assertk.assertions.isEqualTo
import net.postchain.common.PropertiesFileLoader
import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.mc.cli.keys.CommandKeygen
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.MnemonicException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.absolutePathString
import kotlin.test.assertEquals

class KeygenTest {

    // mostly test external lib
    @Test
    fun testMnemonic() {
        val cs = Secp256K1CryptoSystem()
        val privKey = cs.generatePrivKey().data

        val wordList = MnemonicCode.INSTANCE.toMnemonic(privKey)

        val reverse = MnemonicCode.INSTANCE.toEntropy(wordList)

        assertEquals(privKey.toHex(), reverse.toHex())
    }
    
    @Test
    fun keygen() {
        val file = kotlin.io.path.createTempFile()
        CommandKeygen().parse(arrayOf(
                "-m", "picnic shove leader great protect table leg witness walk night cable caution about produce engage armor first burden olive violin cube gentle bulk train",
                "-s", file.absolutePathString()))

        val keys = PropertiesFileLoader.load(file.absolutePathString())
        assertk.assert(keys.getString("pubkey")).isEqualTo("030C9C4203B80509B353F85792FB9F664918F6D2136D8FCE55BE1A985B89E058D3")
        assertk.assert(keys.getString("privkey")).isEqualTo("A438E1FA331ACBB9DFD7E5F692B07F9250075752905F5763D268FA2356C24787")

        val exception = assertThrows<MnemonicException.MnemonicLengthException> {
            CommandKeygen().parse(arrayOf("-m", "invalid mnemonic"))
        }
        assertk.assert(exception.message).isEqualTo("Word list size must be multiple of three words.")
    }

    @Test
    fun `keygen with node option should save with prefix`() {
        val file = kotlin.io.path.createTempFile()
        CommandKeygen().parse(arrayOf(
                "-m", "picnic shove leader great protect table leg witness walk night cable caution about produce engage armor first burden olive violin cube gentle bulk train",
                "-s", file.absolutePathString(),
                "-n"))

        val keys = PropertiesFileLoader.load(file.absolutePathString())
        assertk.assert(keys.getString("messaging.pubkey")).isEqualTo("030C9C4203B80509B353F85792FB9F664918F6D2136D8FCE55BE1A985B89E058D3")
        assertk.assert(keys.getString("messaging.privkey")).isEqualTo("A438E1FA331ACBB9DFD7E5F692B07F9250075752905F5763D268FA2356C24787")
    }
}