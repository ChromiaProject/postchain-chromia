package net.postchain.mc.cli.account

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.Command
import net.postchain.mc.cli.base.Ok
import org.bitcoinj.crypto.MnemonicCode
import java.io.FileOutputStream
import java.util.*

@Parameters(commandDescription = "Generates public/private key pair")
class CommandKeygen: Command {


    @Parameter(
            names = ["-m", "--mnemonic"],
            description = "Mnemonic word list, words separated by space, e.g: \"lift employ roast rotate liar holiday sun fever output magnet...\"",
            required = false)
    private var wordList = ""

    @Parameter(
            names = ["-s", "--save"],
            description = "Output the new key pair into specific file",
            required = false)
    private var file = ""

    override fun key(): String = "keygen"

    override fun execute(): CliResult {
        return try {
            Ok(keygen())
        } catch (e: Exception) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

    /**
     * Cryptographic key generator. Will generate a pair of public and private keys and print to stdout.
     */
    private fun keygen(): String {
        var privKey: ByteArray
        var mnemonic: String
        val mnemonicInstance = MnemonicCode.INSTANCE
        if (wordList.isEmpty()) {
            val cs = Secp256K1CryptoSystem()
            privKey = cs.getRandomBytes(32)
            mnemonic = mnemonicInstance.toMnemonic(privKey).joinToString(" ")
        } else {
            val words = wordList.split(" ")
            mnemonicInstance.check(words)
            mnemonic = wordList
            privKey = mnemonicInstance.toEntropy(words)
        }

        val pubKey = secp256k1_derivePubKey(privKey)

        if (file.isNotEmpty()) {
            val properties = Properties()
            properties["privkey"] = privKey.toHex()
            properties["pubkey"] = pubKey.toHex()

            var fileOutputStream = FileOutputStream(file)
            properties.store(fileOutputStream, "save new key pair to file")
            fileOutputStream.flush()
            fileOutputStream.close()
        }

        return """
            |privkey:   ${privKey.toHex()}
            |pubkey:    ${pubKey.toHex()}
            |mnemonic:  $mnemonic 
        """.trimMargin()
    }
}
