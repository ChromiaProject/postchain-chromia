package net.postchain.mc.cli.config

import net.postchain.client.config.PostchainClientConfig
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey
import org.bitcoinj.crypto.MnemonicCode
import java.awt.Desktop
import java.io.File
import java.util.*

const val configFileName = ".pmc/config"

object PmcConfigProvider {

    fun read(create: Boolean = false, promtp: Boolean = false): PostchainClientConfig {

        when {
            envConfigurationFile().exists() -> return  PostchainClientConfig.fromProperties(envConfigurationFile().absolutePath)
            localConfigurationFile().exists() -> return PostchainClientConfig.fromProperties(configFileName)
            globalConfigurationFile().exists() -> return PostchainClientConfig.fromProperties(globalConfigurationFile().absolutePath)
        }

        if (create && shouldCreate(promtp)) {
            return createConfigFile(localConfigurationFile(), null)
        }
        throw IllegalArgumentException("Configuration file for pmc must be found")
    }

    private fun shouldCreate(promtp: Boolean): Boolean {
        if (!promtp) return true
        val read = Scanner(System.`in`)
        println("No configuration file found, would you like to create one? (Y/n)")
        return read.nextBoolean()
    }

    fun createConfigFile(file: File, blockchainRid: BlockchainRid?): PostchainClientConfig {
        with(file) {
            parentFile.mkdirs()

            val cs = Secp256K1CryptoSystem()
            val privKey = cs.getRandomBytes(32)
            val mnemonicInstance = MnemonicCode.INSTANCE
            val mnemonic = mnemonicInstance.toMnemonic(privKey).joinToString(" ")
            val pubKey = secp256k1_derivePubKey(privKey)
            val properties = Properties()
            properties["privkey"] = privKey.toHex()
            properties["pubkey"] = pubKey.toHex()
            properties["api.url"] = ""
            properties["brid"] = blockchainRid?.toHex() ?: ""

            outputStream().use {
                properties.store(it, "Keypair generated using secp256k1 for postchain management console")
                it.flush()
            }

            Desktop.getDesktop().edit(this)
            println("Configuration file created in $absolutePath")
            println("Mnemonic: $mnemonic")
            return PostchainClientConfig.fromProperties(absolutePath)
        }
    }

    fun globalConfigurationFile() = File("${System.getProperty("user.home")}/$configFileName")
    fun localConfigurationFile() = File(configFileName)
    fun envConfigurationFile() = File(System.getenv("POSTCHAIN_CLIENT_CONFIG"))
}
